package art.arcane.iris.world.history;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationBlendTest {
    @Test
    public void smootherstepUsesExactEndpointsAndMidpoint() {
        assertEquals(0D, GenerationBlend.newEpochWeight(0D, 128), 0D);
        assertEquals(0.5D, GenerationBlend.newEpochWeight(64D, 128), 0D);
        assertEquals(1D, GenerationBlend.newEpochWeight(128D, 128), 0D);
        assertEquals(1D, GenerationBlend.newEpochWeight(256D, 128), 0D);
    }

    @Test
    public void smootherstepIsMonotonicAcrossTransition() {
        double previous = 0D;
        for (int distance = 1; distance <= 128; distance++) {
            double weight = GenerationBlend.newEpochWeight(distance, 128);
            assertTrue(weight > previous);
            previous = weight;
        }
    }

    @Test
    public void interpolationPreservesExactBoundaryValues() {
        assertEquals(72D, GenerationBlend.interpolate(72D, 160D, 0D), 0D);
        assertEquals(160D, GenerationBlend.interpolate(72D, 160D, 1D), 0D);
        assertEquals(116D, GenerationBlend.interpolate(72D, 160D, 0.5D), 0D);
        assertEquals(72, GenerationBlend.interpolateHeight(72, 160, 0D));
        assertEquals(160, GenerationBlend.interpolateHeight(72, 160, 1D));
        assertEquals(116, GenerationBlend.interpolateHeight(72, 160, 0.5D));
    }

    @Test
    public void rejectsInvalidWeightsDistancesAndWidths() {
        assertThrows(IllegalArgumentException.class, () -> GenerationBlend.newEpochWeight(-1D, 32));
        assertThrows(IllegalArgumentException.class, () -> GenerationBlend.newEpochWeight(Double.NaN, 32));
        assertThrows(IllegalArgumentException.class, () -> GenerationBlend.newEpochWeight(1D, 0));
        assertThrows(IllegalArgumentException.class, () -> GenerationBlend.interpolate(0D, 1D, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> GenerationBlend.interpolateHeight(0, 1, 1.1D));
    }
}
