package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
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
        NativeBlockState air = mock(NativeBlockState.class);
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
        NativeBlockState current = mock(NativeBlockState.class);
        NativeBlockState source = mock(NativeBlockState.class);
        NativeBlockState falling = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);
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
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState dry = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
        NativeBlockState lava = mock(NativeBlockState.class);
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
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
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
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState dry = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
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

    @Test
    public void sealGuardsPlaceHostRockWhereTheBlockCannotHoldWater() {
        NativeBlockState rock = new KeyedBlockState("minecraft:stone");
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        NativeBlockState grass = new KeyedBlockState("minecraft:short_grass");
        NativeBlockState sand = solid("minecraft:sand");
        NativeBlockState gravel = solid("minecraft:gravel");
        NativeBlockState granite = solid("minecraft:granite");
        NativeBlockState water = mock(NativeBlockState.class);
        when(water.isFluid()).thenReturn(true);
        when(water.key()).thenReturn("minecraft:water[level=0]");

        assertSame(rock, IrisCarveModifier.sealGuardState(air, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(null, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(grass, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(sand, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(gravel, rock));
        assertSame(granite, IrisCarveModifier.sealGuardState(granite, rock));
        assertSame(water, IrisCarveModifier.sealGuardState(water, rock));
    }

    @Test
    public void subterrainRoomsNeverTakeAnyInletCell() {
        SubterrainRoom room = new SubterrainRoom("room", "", IrisSubterrainFamily.CENOTE, 0, 0, 0, 0, 0, 0, -10, 10, 1D, 0, false, false,
                SubterrainCell.Kind.AIR);
        SubterrainCell open = new SubterrainCell(SubterrainCell.Kind.AIR, "", room);

        assertTrue(IrisCarveModifier.subterrainOwns(open, null));
        assertFalse(IrisCarveModifier.subterrainOwns(open, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR)));
        assertFalse(IrisCarveModifier.subterrainOwns(open, HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE)));
        assertFalse(IrisCarveModifier.subterrainOwns(open, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD)));
        assertFalse(IrisCarveModifier.subterrainOwns(SubterrainCell.OUTSIDE, null));
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.WATER, SubterrainCell.Kind.LAVA}) {
            SubterrainCell fluid = new SubterrainCell(kind, "", room);
            assertTrue(IrisCarveModifier.subterrainOwns(fluid, null));
            assertFalse("subterrain " + kind + " yields the inlet seal", IrisCarveModifier.subterrainOwns(fluid,
                    HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD)));
        }
    }

    private static NativeBlockState solid(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isSolid()).thenReturn(true);
        when(state.key()).thenReturn(key);
        return state;
    }
}
