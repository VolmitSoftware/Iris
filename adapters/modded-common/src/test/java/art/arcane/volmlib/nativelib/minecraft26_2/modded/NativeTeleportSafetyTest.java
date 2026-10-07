package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeTeleportSafetyTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void rejectsTallAndHazardousSupportsAndAcceptsSolidFloor() {
        ServerPlayer player = player();
        ServerLevel level = level();
        for (BlockState support : new BlockState[]{Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.CACTUS.defaultBlockState()}) {
            setSupport(level, support);
            assertFalse(NativeWorldTeleport.isSafeStandingPosition(level, player, 8.5D, 4, 8.5D));
        }
        setSupport(level, Blocks.STONE.defaultBlockState());
        assertTrue(NativeWorldTeleport.isSafeStandingPosition(level, player, 8.5D, 4, 8.5D));
    }

    @Test
    public void fractionalPositionUsesWholePlayerBodyAndRequiresNeighborChunk() {
        ServerPlayer player = player();
        ServerLevel level = level();
        setSupport(level, Blocks.STONE.defaultBlockState());
        when(level.noCollision(any(), any(AABB.class))).thenAnswer(invocation -> {
            AABB body = invocation.getArgument(1);
            return body.maxX <= 16D;
        });
        assertTrue(NativeWorldTeleport.isSafeStandingPosition(level, player, 15.5D, 4, 8.5D));
        assertFalse(NativeWorldTeleport.isSafeStandingPosition(level, player, 15.9D, 4, 8.5D));
        when(level.noCollision(any(), any(AABB.class))).thenReturn(true);
        when(level.getChunkSource().hasChunk(1, 0)).thenReturn(false);
        assertFalse(NativeWorldTeleport.isSafeStandingPosition(level, player, 15.9D, 4, 8.5D));
        EntityDimensions dimensions = player.getDimensions(Pose.STANDING);
        assertEquals(0, NativeWorldTeleport.warmRadius(dimensions.makeBoundingBox(15.5D, 4D, 8.5D), new ChunkPos(0, 0)));
        assertEquals(1, NativeWorldTeleport.warmRadius(dimensions.makeBoundingBox(15.9D, 4D, 8.5D), new ChunkPos(0, 0)));
        assertEquals(1, NativeWorldTeleport.warmRadius(dimensions.makeBoundingBox(-16.1D, 4D, -8.5D), new ChunkPos(-2, -1)));
    }

    @Test
    public void scaledStandingBodyStaysInsideActualWorldHeight() {
        ServerLevel level = level();
        setSupport(level, Blocks.STONE.defaultBlockState());
        ServerPlayer player = player();
        when(player.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.scalable(0.6F, 13F));
        assertFalse(NativeWorldTeleport.isSafeStandingPosition(level, player, 8.5D, 4, 8.5D));
        when(player.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.scalable(0.6F, 12F));
        assertTrue(NativeWorldTeleport.isSafeStandingPosition(level, player, 8.5D, 4, 8.5D));
        assertFalse(NativeWorldTeleport.isSafeStandingPosition(level, player, 8.5D, 0, 8.5D));
    }

    @Test
    public void reconnectDuringChunkWarmupDoesNotTeleportReplacementSession() {
        MinecraftServer server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        when(server.isSameThread()).thenReturn(true);
        ServerLevel level = level();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse("iris:test"));
        when(level.getServer()).thenReturn(server);
        when(level.dimension()).thenReturn(key);
        when(server.getLevel(key)).thenReturn(level);
        ServerChunkCache chunks = level.getChunkSource();
        when(chunks.hasChunk(anyInt(), anyInt())).thenReturn(false);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        doReturn(loaded).when(chunks).addTicketAndLoadWithRadius(any(), any(), anyInt());
        ServerPlayer original = player();
        UUID id = UUID.randomUUID();
        when(original.getUUID()).thenReturn(id);
        when(server.getPlayerList().getPlayer(id)).thenReturn(original);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        CompletableFuture<Boolean> result = NativeWorldTeleport.teleport(NativeProtocolPlayer.fromHandle(original),
                new NativeWorldTeleport.Destination(NativeModdedServer.fromHandle(server), world, 8.5D, 4D, 8.5D, 0L));
        assertFalse(result.isDone());
        ServerPlayer replacement = player();
        when(server.getPlayerList().getPlayer(id)).thenReturn(replacement);
        loaded.complete(null);
        assertFalse(result.join());
        verify(chunks).removeTicketWithRadius(any(), any(), anyInt());
    }

    private ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.scalable(0.6F, 1.8F));
        return player;
    }

    private ServerLevel level() {
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(level.getMinY()).thenReturn(0);
        when(level.getMaxY()).thenReturn(16);
        when(chunks.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.noCollision(any(), any(AABB.class))).thenReturn(true);
        return level;
    }

    private void setSupport(ServerLevel level, BlockState support) {
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return position.getY() == 3 ? support : Blocks.AIR.defaultBlockState();
        });
    }
}
