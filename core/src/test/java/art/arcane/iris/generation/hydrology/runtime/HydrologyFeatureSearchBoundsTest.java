package art.arcane.iris.generation.hydrology.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyFeatureSearchBoundsTest {
    @Test
    public void preservesRequestedDistanceWhenPublicationHaloFits() {
        int accepted = HydrologyFeatureSearchBounds.maximumDistance(
                0, 0, 1024, 256, 128, 1_089);

        assertEquals(1024, accepted);
    }

    @Test
    public void reducesDistanceToTheLargestSafeTileWindow() {
        int requested = 8192;
        int accepted = HydrologyFeatureSearchBounds.maximumDistance(
                255, -129, requested, 256, 2_730, 1_089);

        assertTrue(accepted < requested);
        assertTrue(HydrologyFeatureSearchBounds.tileCount(
                255, -129, accepted, 256, 2_730) <= 1_089L);
        assertTrue(HydrologyFeatureSearchBounds.tileCount(
                255, -129, accepted + 1, 256, 2_730) > 1_089L);
    }
}
