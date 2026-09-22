package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class HydrologyRouteHeightProbeTest {
    @Test
    public void pitChecksRetainMissingAndIntegerDifferenceSemantics() {
        int threshold = HydrologyPlannerSettings.defaults().hydraulics().waterfallMinimumDrop();
        HydrologyTerrainSample blocked = spy(land(threshold));
        doReturn(false).when(blocked).transitAllowed();
        HydrologyTerrainSample[] sides = {null, HydrologyTerrainSample.ocean(threshold, "ocean"),
                land(threshold - 1), land(threshold), land(Integer.MIN_VALUE), land(Integer.MAX_VALUE), blocked};
        for (int center : new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (HydrologyTerrainSample before : sides) {
                for (HydrologyTerrainSample after : sides) {
                    Fixture fixture = fixture((x, z) -> x < 0 ? before : after);
                    boolean expected = before != null && !before.ocean() && after != null && !after.ocean()
                            && before.naturalHeight() - center >= threshold
                            && after.naturalHeight() - center >= threshold;
                    RouteCandidate candidate = new RouteCandidate(new HydrologyPoint(0, center, 0),
                            0D, 0D, 0D, 0D, 0D, new RouteDirection(1D, 0D), true);
                    assertEquals(expected, fixture.planner().routePaths.isTerrainPit(candidate));
                    assertEquals(2, fixture.scalarCalls().get());
                    assertEquals(0, fixture.basisCalls().get());
                }
            }
        }
    }

    @Test
    public void crevassesUseScalarsWhileTransitionsStillCheckInteriorPolicy() {
        int threshold = HydrologyPlannerSettings.defaults().hydraulics().waterfallMinimumDrop();
        HydrologyTerrainSample blocked = spy(land(80));
        doReturn(false).when(blocked).transitAllowed();
        HydrologyTerrainSample[] terrain = {null, HydrologyTerrainSample.ocean(80, "ocean"),
                land(80), land(80 - threshold), land(81 - threshold), land(Integer.MIN_VALUE),
                land(Integer.MAX_VALUE), blocked};
        HydrologyPoint start = new HydrologyPoint(0, 80, 0);
        HydrologyPoint end = new HydrologyPoint(2, 80, 0);
        for (HydrologyTerrainSample endpoint : terrain) {
            for (HydrologyTerrainSample middle : terrain) {
                boolean endpointsMissing = endpoint == null || endpoint.ocean();
                boolean middleMissing = middle == null || middle.ocean();
                boolean crevasse = endpointsMissing || middleMissing
                        || endpoint.naturalHeight() - middle.naturalHeight() >= threshold;
                Fixture fixture = fixture((x, z) -> x == 1 ? middle : endpoint);
                assertEquals(crevasse, fixture.planner().routePaths.crossesTerrainCrevasse(start, end));
                assertEquals(endpointsMissing ? 2 : 3, fixture.scalarCalls().get());
                assertEquals(0, fixture.basisCalls().get());

                fixture.scalarCalls().set(0);
                assertEquals(!crevasse && middle.transitAllowed(),
                        fixture.planner().routePaths.traversableTerrainTransition(start, end));
                assertEquals(2, fixture.scalarCalls().get());
                assertEquals(endpointsMissing ? 0 : 1, fixture.basisCalls().get());
            }
        }
        Fixture adjacent = fixture((x, z) -> null);
        assertFalse(adjacent.planner().routePaths.crossesTerrainCrevasse(start, new HydrologyPoint(1, 80, 0)));
        assertEquals(0, adjacent.scalarCalls().get());
    }

    private static HydrologyTerrainSample land(int height) {
        return HydrologyTerrainSample.openLand(height, 0D, "land");
    }

    private static Fixture fixture(HydrologyTerrainSampler terrain) {
        AtomicInteger basisCalls = new AtomicInteger();
        AtomicInteger scalarCalls = new AtomicInteger();
        HydrologyNaturalTerrainSampler natural = new HydrologyNaturalTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                throw new AssertionError("Height probes do not need a routing grid.");
            }

            @Override
            public NaturalClassification classifyNatural(int x, int z) {
                throw new AssertionError("Scalar probes already classify land.");
            }

            @Override
            public HydrologyTerrainSample sampleBasis(int x, int z) {
                basisCalls.incrementAndGet();
                return terrain.sample(x, z);
            }

            @Override
            public double sampleLandHeight(int x, int z) {
                scalarCalls.incrementAndGet();
                HydrologyTerrainSample sampled = terrain.sample(x, z);
                return sampled == null || sampled.ocean() ? Double.NaN : sampled.naturalHeight();
            }
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(91L, settings, terrain, natural,
                HydrologyGeometrySampler.deterministic(terrain), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, settings.seaLevel(), -4096, 4096));
        return new Fixture(planner, basisCalls, scalarCalls);
    }

    private record Fixture(HydrologyPlanner planner, AtomicInteger basisCalls, AtomicInteger scalarCalls) {
    }
}
