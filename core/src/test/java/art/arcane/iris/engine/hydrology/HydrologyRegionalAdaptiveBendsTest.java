package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalAdaptiveBendsTest {
    @Test
    public void confinedValleysRetainSmallerBendsAndLegalEndpoints() {
        List<HydrologyPoint> guide = guide();
        HydrologyPlannerSettings settings = HydrologyRegionalMorphologyTest.settings(8192, 256);
        HydrologyTerrainSampler open = (x, z) -> HydrologyTerrainSample.openLand(76, 0D, "land");
        HydrologyTerrainSampler confined = (x, z) -> HydrologyTerrainSample.openLand(Math.abs(z - 768) < 16 ? 76 : 108, 0D, "land");
        HydrologyRegionalRoute.Refinement broad = refine(settings, open, guide);
        HydrologyRegionalRoute.Refinement tight = refine(settings, confined, guide);

        assertNull(broad.toString(), broad.rejection());
        assertNull(tight.toString(), tight.rejection());
        assertTrue(maximumDeviation(broad.points()) >= 32);
        assertTrue(maximumDeviation(tight.points()) < maximumDeviation(broad.points()) / 2D);
        assertTrue(maximumDeviation(tight.points()) > 0);
        assertEquals(guide.getFirst(), tight.points().getFirst());
        assertEquals(guide.getLast(), tight.points().getLast());
        assertEquals(tight, refine(settings, confined, guide));
        assertTrue(tight.points().stream().allMatch(point -> point.y() == 76));
    }

    @Test
    public void reducedFreedomBlendsTheFixedDetailFieldWithoutMovingEndpoints() throws Exception {
        HydrologyPlannerSettings settings = HydrologyRegionalMorphologyTest.settings(8192, 256);
        List<HydrologyPoint> guide = guide();
        HydrologyTerrainSampler open = (x, z) -> HydrologyTerrainSample.openLand(76, 0D, "land");
        HydrologyTerrainSampler narrow = (x, z) -> HydrologyTerrainSample.openLand(Math.abs(z - 768) < 12 ? 76 : 108, 0D, "land");
        List<HydrologyPoint> broad = interpolate(settings, open, guide);
        List<HydrologyPoint> tight = interpolate(settings, narrow, guide);

        assertTrue("broad=" + crossings(broad) + " tight=" + crossings(tight), crossings(tight) > crossings(broad));
        assertTrue(maximumDeviation(tight) < maximumDeviation(broad) / 2D);
        assertEquals(guide.getFirst(), tight.getFirst());
        assertEquals(guide.getLast(), tight.getLast());
        for (HydrologyPoint point : tight) {
            assertTrue(Math.abs(point.z() - 768) <= 256D * settings.geometry().meanders().maximumOffsetRatio());
        }
    }

    private static HydrologyRegionalRoute.Refinement refine(HydrologyPlannerSettings settings, HydrologyTerrainSampler terrain,
                                                            List<HydrologyPoint> guide) {
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(new HydrologyPlanner(1337L, settings, terrain));
        return route.select(guide, "default", false, terrain,
                candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();
    }

    @SuppressWarnings("unchecked")
    private static List<HydrologyPoint> interpolate(HydrologyPlannerSettings settings, HydrologyTerrainSampler terrain,
                                                      List<HydrologyPoint> guide) throws Exception {
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(new HydrologyPlanner(1337L, settings, terrain));
        Method method = HydrologyRegionalRoute.class.getDeclaredMethod("interpolate", List.class, double.class, HydrologyRegionalMorphology.class);
        method.setAccessible(true);
        return (List<HydrologyPoint>) method.invoke(route, guide, 1D, HydrologyRegionalMorphology.sample(guide, terrain, settings));
    }

    private static int maximumDeviation(List<HydrologyPoint> points) {
        int maximum = 0;
        for (HydrologyPoint point : points) {
            maximum = Math.max(maximum, Math.abs(point.z() - 768));
        }
        return maximum;
    }

    private static int crossings(List<HydrologyPoint> points) {
        int previous = 0;
        int crossings = 0;
        for (HydrologyPoint point : points) {
            if (point.x() < 512 || point.x() > 5632) {
                continue;
            }
            int side = point.z() > 769 ? 1 : point.z() < 767 ? -1 : 0;
            if (side != 0) {
                if (previous != 0 && side != previous) {
                    crossings++;
                }
                previous = side;
            }
        }
        return crossings;
    }

    private static List<HydrologyPoint> guide() {
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        for (int x = 0; x <= 6144; x += 256) {
            guide.add(new HydrologyPoint(x, 76, 768));
        }
        return guide;
    }
}
