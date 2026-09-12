package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureCaveAnchorResolverTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void floorRequiresSolidBoundaryAndUpwardClearance() {
        IntPredicate carved = carvedAt(10, 11, 12, 13);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_FLOOR, 10, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_FLOOR, 11, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_FLOOR, 10, 5));
    }

    @Test
    public void ceilingRequiresSolidBoundaryAndDownwardClearance() {
        IntPredicate carved = carvedAt(20, 21, 22, 23);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CEILING, 23, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CEILING, 22, 4));
    }

    @Test
    public void centerRequiresCarvedSpaceOnBothSides() {
        IntPredicate carved = carvedAt(30, 31, 32, 33, 34);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 32, 5));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 31, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 33, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(32, 33, 34), IrisStructureAnchorMode.CAVE_CENTER, 32, 3));
    }

    @Test
    public void evenHeightCavernsHaveTwoValidCenterBlocks() {
        IntPredicate carved = carvedAt(30, 31, 32, 33);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 31, 4));
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 32, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, IrisStructureAnchorMode.CAVE_CENTER, 30, 4));
    }

    @Test
    public void anyRequiresClearanceAroundTheAlignedCenter() {
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(39, 40, 41), IrisStructureAnchorMode.CAVE_ANY, 40, 3));
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(48, 49, 50, 51), IrisStructureAnchorMode.CAVE_ANY, 49, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(60, 61, 62), IrisStructureAnchorMode.CAVE_ANY, 60, 3));
    }

    @Test
    public void deterministicColumnPlanDoesNotRepeatAttempts() {
        int[] first = StructureCaveAnchorResolver.candidateColumnIndices(new RNG(928374L), 64);
        int[] second = StructureCaveAnchorResolver.candidateColumnIndices(new RNG(928374L), 64);
        Set<Integer> unique = new HashSet<>();
        Arrays.stream(first).forEach(unique::add);

        assertArrayEquals(first, second);
        assertEquals(64, first.length);
        assertEquals(64, unique.size());
        assertTrue(unique.stream().allMatch(column -> column >= 0 && column < 256));
        assertEquals(64, StructureCaveAnchorResolver
                .candidateColumnIndices(new RNG(1L), Integer.MAX_VALUE).length);
    }

    @Test
    public void worldHeightIsConvertedToMantleRelativeHeight() {
        assertEquals(0, StructureCaveAnchorResolver.toMantleY(-64, -64));
        assertEquals(80, StructureCaveAnchorResolver.toMantleY(16, -64));
        assertEquals(383, StructureCaveAnchorResolver.toMantleY(319, -64));
    }

    @Test
    public void biomeAllowlistMatchingIsTrimmedCaseInsensitiveAndNamespaceAware() {
        assertTrue(StructureCaveAnchorResolver.matchesBiomeKey("crystal_caves", " Iris:CRYSTAL_CAVES "));
        assertTrue(StructureCaveAnchorResolver.matchesBiomeKey("iris:crystal_caves", "crystal_caves"));
        assertFalse(StructureCaveAnchorResolver.matchesBiomeKey("iris:crystal_caves", "other:crystal_caves"));
        assertFalse(StructureCaveAnchorResolver.matchesBiomeKey("crystal_caves", "  "));
    }

    @Test
    public void dryCaveUnderSubmergedSurfaceUsesAnchorCavernState() {
        Engine engine = engineWithFloorAnchor(new MatterCavern(true, "", (byte) 0), 0);

        StructureCaveAnchorResolver.Anchor anchor = StructureCaveAnchorResolver.resolve(
                engine, floorPlacement(false), 0, 0, new RNG(87234L));

        assertNotNull(anchor);
        assertEquals(-54, anchor.y());
        verify(engine, never()).getHeight(anyInt(), anyInt(), eq(true));
    }

    @Test
    public void wetCaveUnderDrySurfaceIsRejectedAtTheAnchor() {
        Engine engine = engineWithFloorAnchor(new MatterCavern(true, "", (byte) 1), 200);

        StructureCaveAnchorResolver.Anchor anchor = StructureCaveAnchorResolver.resolve(
                engine, floorPlacement(false), 0, 0, new RNG(87234L));

        assertNull(anchor);
        verify(engine, never()).getHeight(anyInt(), anyInt(), eq(true));
    }

    @Test
    public void dryPolicyHonorsExplicitLiquidsAndDefaultLavaHeight() {
        MatterCavern air = new MatterCavern(true, "", (byte) 0);
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern lava = new MatterCavern(true, "", (byte) 2);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, air, 8, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(false, air, 9, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, water, 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, lava, 20, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(false, forcedAir, 0, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(true, water, 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(
                true, new MatterCavern(false, "", (byte) 0), 20, 8));
    }

    @Test
    public void plannedHydrologyCannotBecomeStructureAnchors() {
        MatterCavern fluid = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(
                true, fluid, HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE), 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(
                true, fluid, HydrologyCaveCell.of(HydrologyCaveAction.FALLING_FLUID), 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(
                true, null, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD), 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(
                false, forcedAir, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), 0, 8));
    }

    private static Engine engineWithFloorAnchor(MatterCavern anchorCavern, int surfaceHeight) {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisDimension dimension = mock(IrisDimension.class);
        MatterCavern carvedAir = new MatterCavern(true, "", (byte) 0);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(surfaceHeight);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(127);
        when(dimension.getCaveLavaHeight()).thenReturn(8);
        when(engine.getMantle().getMantle().get(
                anyInt(), anyInt(), anyInt(), eq(MatterCavern.class)))
                .thenAnswer(invocation -> {
                    int mantleY = invocation.getArgument(1);
                    if (mantleY == 10) {
                        return anchorCavern;
                    }
                    return mantleY == 11 || mantleY == 12 ? carvedAir : null;
                });
        return engine;
    }

    private static IrisStructurePlacement floorPlacement(boolean underwater) {
        return new IrisStructurePlacement()
                .setAnchor(IrisStructureAnchorMode.CAVE_FLOOR)
                .setMinHeight(-54)
                .setMaxHeight(-54)
                .setCaveAnchorAttempts(1)
                .setCaveAnchorScanStep(1)
                .setCaveMinimumClearance(3)
                .setUnderwater(underwater);
    }

    private static IntPredicate carvedAt(Integer... values) {
        Set<Integer> carved = Set.of(values);
        return carved::contains;
    }
}
