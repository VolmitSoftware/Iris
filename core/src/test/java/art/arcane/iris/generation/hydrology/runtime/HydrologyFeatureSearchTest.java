package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyFeatureSearchTest {
    @Test
    public void ringZeroIsTheOriginTile() {
        assertEquals(List.of(new HydrologyTileKey(3, -2)), HydrologyFeatureSearch.ring(3, -2, 0));
    }

    @Test
    public void ringsCoverEveryTileAtExactlyTheirChebyshevDistance() {
        HashSet<HydrologyTileKey> seen = new HashSet<>();
        for (int radius = 0; radius <= 3; radius++) {
            List<HydrologyTileKey> ring = HydrologyFeatureSearch.ring(1, 1, radius);
            assertEquals(radius == 0 ? 1 : radius * 8, ring.size());
            for (HydrologyTileKey key : ring) {
                int distance = Math.max(Math.abs(key.tileX() - 1), Math.abs(key.tileZ() - 1));
                assertEquals(radius, distance);
                assertTrue(seen.add(key));
            }
        }
        assertEquals(49, seen.size());
    }

    @Test
    public void ringLimitCoversTheRequestedDistancePlusPublication() {
        assertEquals(9, HydrologyFeatureSearch.ringLimit(8192, 1024, 358));
        assertEquals(1, HydrologyFeatureSearch.ringLimit(0, 1024, 358));
        assertEquals(0, HydrologyFeatureSearch.ringLimit(0, 1024, 0));
    }

    @Test
    public void lowerBoundGrowsWithTheRingAndNeverGoesNegative() {
        assertEquals(0L, HydrologyFeatureSearch.lowerBound(0, 1024, 358));
        assertEquals(0L, HydrologyFeatureSearch.lowerBound(1, 1024, 358));
        assertEquals(666L, HydrologyFeatureSearch.lowerBound(2, 1024, 358));
        assertEquals(1690L, HydrologyFeatureSearch.lowerBound(3, 1024, 358));
    }
}
