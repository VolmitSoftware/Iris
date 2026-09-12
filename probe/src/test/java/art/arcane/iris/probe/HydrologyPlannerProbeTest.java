package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyTile;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HydrologyPlannerProbeTest {
    @Test
    public void coastalFixturePublishesAnAcceptedOceanMouth() {
        HydrologyTile tile = HydrologyPlannerProbe.surfaceMouthTile();
        assertTrue(tile.diagnosticCandidates().stream().limit(16).toList().toString(),
                tile.features().stream().anyMatch(feature -> feature.type() == HydrologyFeatureType.MOUTH));
    }

    @Test
    public void inlandFixturePublishesAContainedWaterfall() {
        HydrologyTile tile = HydrologyPlannerProbe.surfaceWaterfallTile();
        assertTrue(tile.diagnosticCandidates().stream().limit(16).toList().toString(),
                tile.features().stream().anyMatch(feature -> feature.type() == HydrologyFeatureType.WATERFALL));
        assertTrue(tile.courses().stream().anyMatch(course -> course.segments().stream().anyMatch(segment -> segment.fallingFluid()
                && segment.type() == HydrologyFeatureType.WATERFALL)
                && tile.cavePlans().stream().anyMatch(plan -> plan.source().sourceId() == course.id())));
    }

    @Test
    public void deterministicAcceptedCoveragePublishesEveryReachableFeatureType() {
        Map<HydrologyFeatureType, HydrologyFeatureRef> coverage =
                HydrologyPlannerProbe.deterministicAcceptedCoverage();

        assertEquals(HydrologyPlannerProbe.REQUIRED_FEATURE_TYPES, coverage.keySet());
        for (Map.Entry<HydrologyFeatureType, HydrologyFeatureRef> entry : coverage.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().type());
        }
    }

    @Test
    public void requiredCoverageExcludesTheUnproducedRidgeBore() {
        assertFalse(HydrologyPlannerProbe.REQUIRED_FEATURE_TYPES.contains(HydrologyFeatureType.RIDGE_BORE));
        assertEquals(
                EnumSet.complementOf(EnumSet.of(HydrologyFeatureType.RIDGE_BORE, HydrologyFeatureType.STANDING_POOL)),
                HydrologyPlannerProbe.REQUIRED_FEATURE_TYPES
        );
    }
}
