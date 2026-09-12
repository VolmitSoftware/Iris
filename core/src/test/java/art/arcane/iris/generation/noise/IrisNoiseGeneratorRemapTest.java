package art.arcane.iris.generation.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisNoiseGeneratorRemapTest {
    @Test
    public void parametricCurvePreservesEndpointsAndShapesBothHalves() {
        double[][] samples = {{0D, 0D}, {0.25D, 0.1D}, {0.5D, 0.5D}, {0.75D, 0.9D}, {1D, 1D}};
        for (double[] sample : samples) {
            IrisNoiseGenerator layer = new IrisNoiseGenerator()
                    .setStyle(NoiseStyle.FLAT.style())
                    .setOpacity(sample[0])
                    .setParametric(true);
            assertEquals(sample[1], layer.getNoise(75L, 13D, -21D, null), 0.000000000001D);
        }
    }
}
