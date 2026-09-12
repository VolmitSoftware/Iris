package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisCarveModifierHydrologyTest {
    @Test
    public void explicitHydrologyCanMaterializeIntoAnUninitializedCell() {
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);

        assertFalse(IrisCarveModifier.shouldSkipEmptyCarve(null, true));
        assertFalse(IrisCarveModifier.shouldSkipEmptyCarve(air, true));
        assertTrue(IrisCarveModifier.shouldSkipEmptyCarve(null, false));
        assertTrue(IrisCarveModifier.shouldSkipEmptyCarve(air, false));
    }

    @Test
    public void overlayCompositionDoesNotMutateBaselineCavern() {
        MatterCavern baseline = new MatterCavern(true, "baseline", (byte) 2);

        assertSame(baseline, IrisCarveModifier.composeCavern(baseline, null));
        assertNull(IrisCarveModifier.composeCavern(
                baseline, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD)));

        MatterCavern wet = IrisCarveModifier.composeCavern(
                baseline, new HydrologyCaveCell(
                        HydrologyCaveAction.WET_SOURCE,
                        "deep_lava",
                        "iris:flooded"
                ));
        MatterCavern dry = IrisCarveModifier.composeCavern(
                baseline, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR));

        assertEquals(1, wet.getLiquid());
        assertEquals("iris:flooded", wet.getCustomBiome());
        assertEquals(3, dry.getLiquid());
        assertEquals(2, baseline.getLiquid());
        assertEquals("baseline", baseline.getCustomBiome());
    }

    @Test
    public void hydrologyActionsResolveDistinctFluidStates() {
        PlatformBlockState current = mock(PlatformBlockState.class);
        PlatformBlockState source = mock(PlatformBlockState.class);
        PlatformBlockState falling = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(source.key()).thenReturn("minecraft:water[level=0]");
        when(source.withProperty("level", "8")).thenReturn(falling);

        assertSame(source, IrisCarveModifier.resolveHydrologyState(
                HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE), current, source, air));
        assertSame(falling, IrisCarveModifier.resolveHydrologyState(
                HydrologyCaveCell.of(HydrologyCaveAction.FALLING_FLUID), current, source, air));
        assertSame(air, IrisCarveModifier.resolveHydrologyState(
                HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), current, source, air));
    }

    @Test
    public void waterloggingFollowsOnlyExplicitResultingWater() {
        PlatformBlockState waterlogged = mock(PlatformBlockState.class);
        PlatformBlockState dry = mock(PlatformBlockState.class);
        PlatformBlockState water = mock(PlatformBlockState.class);
        PlatformBlockState lava = mock(PlatformBlockState.class);
        when(waterlogged.key()).thenReturn("minecraft:seagrass[waterlogged=true]");
        when(waterlogged.withProperty("waterlogged", "false")).thenReturn(dry);
        when(water.isWater()).thenReturn(true);

        assertSame(waterlogged, IrisCarveModifier.normalizeWaterlogging(waterlogged, water));
        assertSame(dry, IrisCarveModifier.normalizeWaterlogging(waterlogged, null));
        assertSame(dry, IrisCarveModifier.normalizeWaterlogging(waterlogged, lava));
        assertSame(dry, IrisCarveModifier.resolveHydrologyState(
                HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD), waterlogged, water, null));
    }

    @Test
    public void noOverlayLeavesBaselineWaterloggingUntouched() {
        PlatformBlockState waterlogged = mock(PlatformBlockState.class);
        PlatformBlockState water = mock(PlatformBlockState.class);
        MatterCavern flooded = new MatterCavern(true, "", (byte) 1);

        assertSame(waterlogged, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                null,
                water
        ));
        verify(waterlogged, never()).withProperty("waterlogged", "false");
    }

    @Test
    public void explicitOverlayUsesComposedFluidIntentForWaterlogging() {
        PlatformBlockState waterlogged = mock(PlatformBlockState.class);
        PlatformBlockState dry = mock(PlatformBlockState.class);
        PlatformBlockState water = mock(PlatformBlockState.class);
        MatterCavern flooded = new MatterCavern(true, "", (byte) 1);
        when(waterlogged.key()).thenReturn("minecraft:seagrass[waterlogged=true]");
        when(waterlogged.withProperty("waterlogged", "false")).thenReturn(dry);
        when(water.isWater()).thenReturn(true);

        assertSame(waterlogged, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE),
                water
        ));
        assertSame(dry, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR),
                water
        ));
    }
}
