package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeWorldMaintenanceTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void fluidExposureUsesSidesAndBottomWithoutTreatingAirAboveAsExposure() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.WATER.defaultBlockState());
        when(level.getBlockState(BlockPos.ZERO.above())).thenReturn(Blocks.AIR.defaultBlockState());
        assertFalse(NativeWorldMaintenance.exposedFluid(world, 0, 0, 0));
        when(level.getBlockState(BlockPos.ZERO.below())).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(NativeWorldMaintenance.exposedFluid(world, 0, 0, 0));
    }

    @Test
    public void emptySpaceBesideSolidBlocksDoesNotScheduleFluidPhysics() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());
        assertFalse(NativeWorldMaintenance.exposedFluid(world, 0, 0, 0));
    }
}
