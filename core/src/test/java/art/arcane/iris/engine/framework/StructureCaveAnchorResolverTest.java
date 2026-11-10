package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.mantle.CarvedVoid;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StructureCaveAnchorResolverTest {
    private final AtomicInteger surfaceReads = new AtomicInteger();

    @Test
    public void floorRequiresSolidBoundaryAndUpwardClearance() {
        IntPredicate carved = carvedAt(10, 11, 12, 13);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_FLOOR, 10, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_FLOOR, 11, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_FLOOR, 10, 5));
    }

    @Test
    public void ceilingRequiresSolidBoundaryAndDownwardClearance() {
        IntPredicate carved = carvedAt(20, 21, 22, 23);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CEILING, 23, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CEILING, 22, 4));
    }

    @Test
    public void centerRequiresCarvedSpaceOnBothSides() {
        IntPredicate carved = carvedAt(30, 31, 32, 33, 34);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 32, 5));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 31, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 33, 3));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(32, 33, 34), StructureAnchorMode.CAVE_CENTER, 32, 3));
    }

    @Test
    public void evenHeightCavernsHaveTwoValidCenterBlocks() {
        IntPredicate carved = carvedAt(30, 31, 32, 33);

        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 31, 4));
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 32, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carved, StructureAnchorMode.CAVE_CENTER, 30, 4));
    }

    @Test
    public void anyRequiresClearanceAroundTheAlignedCenter() {
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(39, 40, 41), StructureAnchorMode.CAVE_ANY, 40, 3));
        assertTrue(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(48, 49, 50, 51), StructureAnchorMode.CAVE_ANY, 49, 4));
        assertFalse(StructureCaveAnchorResolver.matchesGeometry(
                carvedAt(60, 61, 62), StructureAnchorMode.CAVE_ANY, 60, 3));
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
        StructureTestWorld world = worldWithFloorAnchor(CarvedVoid.OPEN, 0);

        StructureCaveAnchorResolver.Anchor anchor = StructureCaveAnchorResolver.resolve(
                world, floorPlacement(false), 0, 0, new RNG(87234L));

        assertNotNull(anchor);
        assertEquals(-54, anchor.y());
        assertEquals(0, surfaceReads.get());
    }

    @Test
    public void wetCaveUnderDrySurfaceIsRejectedAtTheAnchor() {
        StructureTestWorld world = worldWithFloorAnchor(CarvedVoid.FLUID, 200);

        StructureCaveAnchorResolver.Anchor anchor = StructureCaveAnchorResolver.resolve(
                world, floorPlacement(false), 0, 0, new RNG(87234L));

        assertNull(anchor);
        assertEquals(0, surfaceReads.get());
    }

    @Test
    public void dryPolicyHonorsExplicitLiquidsAndDefaultLavaHeight() {
        CarvedVoid air = CarvedVoid.OPEN;
        CarvedVoid water = CarvedVoid.FLUID;
        CarvedVoid lava = CarvedVoid.LAVA;
        CarvedVoid forcedAir = CarvedVoid.AIR;

        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, air, 8, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(false, air, 9, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, water, 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(false, lava, 20, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(false, forcedAir, 0, 8));
        assertTrue(StructureCaveAnchorResolver.acceptsAnchorFluid(true, water, 20, 8));
        assertFalse(StructureCaveAnchorResolver.acceptsAnchorFluid(true, null, 20, 8));
    }

    private StructureTestWorld worldWithFloorAnchor(CarvedVoid anchorVoid, int surfaceHeight) {
        return new StructureTestWorld()
                .fluidHeight(127)
                .highest((x, z) -> {
                    surfaceReads.incrementAndGet();
                    return surfaceHeight;
                })
                .carved((x, mantleY, z) -> {
                    if (mantleY == 10) {
                        return Optional.of(anchorVoid);
                    }
                    return mantleY == 11 || mantleY == 12 ? Optional.of(CarvedVoid.OPEN) : Optional.empty();
                });
    }

    private static StructurePlacement floorPlacement(boolean underwater) {
        return placement("anchor", StructureAnchorMode.CAVE_FLOOR, "minHeight", -54, "maxHeight", -54, "caveAnchorAttempts", 1, "caveAnchorScanStep", 1, "caveMinimumClearance", 3, "underwater", underwater);
    }

    private static IntPredicate carvedAt(Integer... values) {
        Set<Integer> carved = Set.of(values);
        return carved::contains;
    }
}
