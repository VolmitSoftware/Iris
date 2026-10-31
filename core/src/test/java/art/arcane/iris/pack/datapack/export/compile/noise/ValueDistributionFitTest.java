package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ValueDistributionFitTest {
    private static final int SAMPLES = 20_000;

    @Test
    public void curveMapsSourceQuantilesOntoTargetQuantiles() {
        SplittableRandom random = new SplittableRandom(5L);
        double[] source = new double[SAMPLES];
        double[] target = new double[SAMPLES];
        for (int index = 0; index < SAMPLES; index++) {
            source[index] = random.nextDouble() * 2.0 - 1.0;
            double uniform = random.nextDouble();
            target[index] = uniform * uniform;
        }
        List<Field.Knot> knots = ValueDistributionFit.fit(source, target);
        for (double input = -0.9; input <= 0.9; input += 0.05) {
            double expected = Math.pow((input + 1.0) / 2.0, 2.0);
            Field curve = new Field.Curve(new Field.Const(input), knots);
            double actual = FieldEvaluator.sample(curve, Map.of(), 0L, DataVersion.V26_3, 0, 0, 0);
            assertEquals("quantile map at " + input, expected, actual, 0.02);
        }
    }

    @Test
    public void knotsIncreaseAndStayMonotone() {
        SplittableRandom random = new SplittableRandom(9L);
        double[] source = new double[SAMPLES];
        double[] target = new double[SAMPLES];
        for (int index = 0; index < SAMPLES; index++) {
            source[index] = random.nextGaussian();
            target[index] = Math.exp(random.nextGaussian());
        }
        List<Field.Knot> knots = ValueDistributionFit.fit(source, target);
        for (int index = 0; index < knots.size(); index++) {
            Field.Knot knot = knots.get(index);
            assertTrue(knot.derivative() >= 0.0);
            if (index > 0) {
                Field.Knot previous = knots.get(index - 1);
                assertTrue(knot.location() > previous.location());
                assertTrue(((Field.Const) knot.value()).value() >= ((Field.Const) previous.value()).value());
            }
        }
        assertEquals(0.0, knots.getFirst().derivative(), 0.0);
        assertEquals(0.0, knots.getLast().derivative(), 0.0);
    }

    @Test
    public void constantSourceIsRejected() {
        double[] constant = new double[256];
        double[] target = new double[256];
        for (int index = 0; index < target.length; index++) {
            target[index] = index;
        }
        assertThrows(IllegalArgumentException.class, () -> ValueDistributionFit.fit(constant, target));
    }

    @Test
    public void tooFewSamplesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValueDistributionFit.fit(new double[8], new double[8]));
    }
}
