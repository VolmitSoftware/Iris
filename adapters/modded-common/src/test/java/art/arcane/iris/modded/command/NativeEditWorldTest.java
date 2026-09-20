package art.arcane.iris.modded.command;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeEditWorldTest {
    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void repeatedWritesKeepTheOriginalUndoState() {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos point = new BlockPos(2, 64, 3);
        when(level.getBlockState(point)).thenReturn(Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        NativeEditWorld.EditSession session = new NativeEditWorld(new ModdedPlatformWorld(level))
                .editSession(new NativeEditWorld.EditOptions(1, 256, true));

        assertEquals(NativeEditWorld.WriteResult.BLOCK, session.set(2, 64, 3, ModdedBlockState.of(Blocks.DIRT.defaultBlockState(), null)));
        assertEquals(NativeEditWorld.WriteResult.AIR, session.set(2, 64, 3, ModdedBlockState.of(Blocks.AIR.defaultBlockState(), null)));
        assertEquals(1, session.restore(failure -> { throw new AssertionError(failure.error()); }));
        verify(level).setBlock(point, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Test
    public void protectedBedrockAndOutOfRangeWritesNeverEnterUndo() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenReturn(Blocks.BEDROCK.defaultBlockState());
        NativeEditWorld.EditSession session = new NativeEditWorld(new ModdedPlatformWorld(level))
                .editSession(new NativeEditWorld.EditOptions(1, 256, true));
        ModdedBlockState stone = ModdedBlockState.of(Blocks.STONE.defaultBlockState(), null);

        assertEquals(NativeEditWorld.WriteResult.SKIPPED, session.set(0, 0, 0, stone));
        assertEquals(NativeEditWorld.WriteResult.SKIPPED, session.set(0, 256, 0, stone));
        assertEquals(NativeEditWorld.WriteResult.SKIPPED, session.set(0, 64, 0, stone));
        assertTrue(session.empty());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    public void captureOmitsOnlyRegularAirAndPreservesCaveAir() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return pos.getX() == 0 ? Blocks.AIR.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState();
        });
        NativeEditWorld.CaptureTarget target = mock(NativeEditWorld.CaptureTarget.class);
        new NativeEditWorld(new ModdedPlatformWorld(level)).capture(
                new NativeEditWorld.Bounds(new NativeBlockPoint(0, 0, 0), new NativeBlockPoint(1, 0, 0)), target);

        verify(target).block(1, 0, 0, ModdedBlockState.of(Blocks.CAVE_AIR.defaultBlockState(), null));
        verify(target, never()).block(eq(0), anyInt(), anyInt(), any());
    }

    @Test
    public void reopenedDimensionDoesNotAcceptTheOldWorldIdentity() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel original = mock(ServerLevel.class);
        ServerLevel replacement = mock(ServerLevel.class);
        when(original.getServer()).thenReturn(server);
        when(original.dimension()).thenReturn(Level.OVERWORLD);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(original, replacement);
        NativeEditWorld world = new NativeEditWorld(new ModdedPlatformWorld(original));

        assertTrue(world.current());
        assertFalse(world.current());
        assertTrue(world.represents(new ModdedPlatformWorld(original)));
        assertFalse(world.represents(new ModdedPlatformWorld(replacement)));
    }
}
