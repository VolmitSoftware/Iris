package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.NavigableSet;
import java.util.TreeSet;

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
