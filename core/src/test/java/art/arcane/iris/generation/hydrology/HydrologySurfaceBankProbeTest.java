package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologySurfaceBankProbeTest {
    @Test
    public void raisingShoreWidthWidensTheBankProbeBandInsteadOfSlidingIt() {
        NavigableSet<Integer> narrow = probedBankOffsets(1.5D);
        NavigableSet<Integer> wide = probedBankOffsets(3D);
        int innerRing = innerRing();

        assertTrue("narrow band " + narrow, narrow.contains(innerRing));
        assertTrue("wide band " + wide, wide.contains(innerRing));
        assertTrue("band " + wide + " must still cover " + narrow, wide.containsAll(narrow));
        assertTrue("band " + wide + " must reach past " + narrow, wide.size() > narrow.size());
    }

    @Test
    public void theInnerProbeRingDoesNotMoveWithShoreWidth() {
        int innerRing = innerRing();
        for (double shoreWidth : new double[]{0D, 1.5D, 3D, 16D}) {
            NavigableSet<Integer> band = probedBankOffsets(shoreWidth);
            assertEquals("band " + band + " at shoreWidth " + shoreWidth,
                    innerRing, band.first().intValue());
        }
    }

    @Test
    public void bankHeightQueriesPreserveExactPenaltiesWithoutBankPolicySamples() {
        HydrologyTerrainSampler terrain = (x, z) -> x + z == 7 ? null
                : x < -3 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(80 + Math.floorMod(x * 3 + z * 7, 31), 0D, "land");
        AtomicInteger basisCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        HydrologyNaturalTerrainSampler natural = new HydrologyNaturalTerrainSampler() {
            @Override
            public HydrologyTerrainSample sampleBasis(int x, int z) {
                basisCalls.incrementAndGet();
                return terrain.sample(x, z);
            }

            @Override
            public double sampleLandHeight(int x, int z) {
                heightCalls.incrementAndGet();
                HydrologyTerrainSample sampled = terrain.sample(x, z);
                return sampled == null || sampled.ocean() ? Double.NaN : sampled.naturalHeight();
            }

            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                throw new AssertionError("Bank probes do not require a routing grid");
            }

            @Override
            public NaturalClassification classifyNatural(int x, int z) {
                throw new AssertionError("Bank probes only require land heights");
            }
        };
        for (double angle : new double[]{0D, 0.37D, 0.7853981633974483D}) {
            HydrologyPlannerSettings settings = settings(3D);
            HydrologyPlanner reference = new HydrologyPlanner(91L, settings, terrain);
            HydrologyPlanner optimized = new HydrologyPlanner(91L, settings, terrain, natural,
                    HydrologyGeometrySampler.deterministic(terrain), -4096,
                    footprint -> new HydrologyTerrainCaveVoxelView(terrain, settings.seaLevel(), -4096, 4096));
            HydrologyPoint point = new HydrologyPoint(0, 80, 0);
            RouteCandidate candidate = new RouteCandidate(point, 0D, 0D, 0D, 0D, 0D,
                    new RouteDirection(StrictMath.cos(angle), StrictMath.sin(angle)), true);
            basisCalls.set(0);
            heightCalls.set(0);
            double expected = reference.surfaceCourses.surfaceRouteCandidateBankPenalty(candidate);
            double actual = optimized.surfaceCourses.surfaceRouteCandidateBankPenalty(candidate);
            assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
            assertEquals(1, basisCalls.get());
            assertTrue(heightCalls.get() > 0);
            optimized.planningSamples.set(new HydrologyPlanner.PlanningSamples());
            try {
                assertEquals(expected, optimized.surfaceCourses.surfaceRouteCandidateBankPenalty(candidate), 0D);
                int firstPassHeightCalls = heightCalls.get();
                assertEquals(expected, optimized.surfaceCourses.surfaceRouteCandidateBankPenalty(candidate), 0D);
                assertEquals(firstPassHeightCalls, heightCalls.get());
            } finally {
                optimized.planningSamples.remove();
            }
        }
    }

    /** One block outside half the nominal maximum width - see HydrologySurfaceCoursePlanner. */
    private int innerRing() {
        return (int) StrictMath.ceil(
                HydrologyPlannerSettings.defaults().surface().maximumWidth() / 2D) + 1;
    }

    private NavigableSet<Integer> probedBankOffsets(double shoreWidth) {
        HydrologyPoint center = new HydrologyPoint(0, 84, 0);
        NavigableSet<Integer> probed = new TreeSet<>();
        HydrologyTerrainSampler sampler = (x, z) -> {
            if (x == center.x()) {
                probed.add(Math.abs(z - center.z()));
            }
            return HydrologyTerrainSample.openLand(84, 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(91L, settings(shoreWidth), sampler);
        planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());

        planner.surfaceCourses.surfaceRouteCandidateBankPenalty(new RouteCandidate(
                center, center.x(), center.z(), 0D, 0D, 0D, new RouteDirection(1D, 0D), true));

        probed.remove(0);
        return probed;
    }

    private HydrologyPlannerSettings settings(double shoreWidth) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Surface surface = base.surface();
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                new HydrologyPlannerSettings.Surface(
                        surface.enabled(),
                        surface.sources(),
                        surface.minimumWidth(),
                        surface.maximumWidth(),
                        surface.minimumDepth(),
                        surface.maximumDepth(),
                        surface.maximumIncision(),
                        shoreWidth,
                        surface.banks()),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                base.geometry(),
                base.deepFluids(),
                base.surfacePools(),
                base.widestShoreBiomeWidth(),
                base.seaCaves(),
                base.surfacePolicyBounds());
    }
}
