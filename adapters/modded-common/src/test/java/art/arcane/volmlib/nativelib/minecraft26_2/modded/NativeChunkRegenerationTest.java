package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeChunkRegenerationTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void replacesBlocksAndEntitiesBeforePolicyThenRebuildsBiomeAndLighting() throws Exception {
        ServerLevel level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        LevelChunk chunk = mock(LevelChunk.class);
        LevelChunkSection section = mock(LevelChunkSection.class);
        ThreadedLevelLightEngine lights = mock(ThreadedLevelLightEngine.class);
        when(level.getChunk(0, 0)).thenReturn(chunk);
        when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
        when(chunk.getSectionsCount()).thenReturn(1);
        when(chunk.getSection(0)).thenReturn(section);
        when(level.getChunkSource().getLightEngine()).thenReturn(lights);
        ChunkMap tracking = mock(ChunkMap.class);
        Field chunkMap = ServerChunkCache.class.getDeclaredField("chunkMap");
        chunkMap.setAccessible(true);
        chunkMap.set(level.getChunkSource(), tracking);
        when(tracking.getPlayers(any(), eq(false))).thenReturn(List.of());
        when(section.setBlockState(anyInt(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(Blocks.AIR.defaultBlockState());
        BlockPos oldTile = new BlockPos(3, 4, 5);
        when(chunk.getBlockEntities()).thenReturn(Map.of(oldTile, mock(BlockEntity.class)));
        Entity entity = mock(Entity.class);
        when(level.getEntities(isNull(Entity.class), any(), any())).thenReturn(List.of(entity));
        NativeBiomeResolver biomes = mock(NativeBiomeResolver.class);
        List<String> order = new ArrayList<>();
        doAnswer(invocation -> {
            order.add("biomes");
            return null;
        }).when(biomes).fill(chunk, level);
        doAnswer(invocation -> {
            order.add("unsaved");
            return null;
        }).when(chunk).markUnsaved();
        BlockState glowing = Blocks.GLOWSTONE.defaultBlockState();
        NativeChunkRegeneration.Replacement replacement = new NativeChunkRegeneration.Replacement(
                0, 0, 0, 16, (x, y, z) -> x == 1 && y == 2 && z == 3
                ? ModdedBlockState.of(glowing, null) : null, biomes);
        try (MockedStatic<Heightmap> ignored = mockStatic(Heightmap.class)) {
            new NativeChunkRegeneration(world).apply(replacement, () -> order.add("policy"));
        }
        assertEquals(List.of("policy", "biomes", "unsaved"), order);
        verify(entity).discard();
        verify(chunk).removeBlockEntity(oldTile);
        verify(section).acquire();
        verify(section).release();
        verify(section).setBlockState(1, 2, 3, glowing, false);
        verify(lights).checkBlock(new BlockPos(1, 2, 3));
    }
}
