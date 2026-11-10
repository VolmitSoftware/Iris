package art.arcane.iris.util.project.noise;

import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CNGExpressionDimensionTest {
    private static final double TOLERANCE = 1.0E-12D;
    private static final double MISSING_AXIS_IN_2D = -1D;

    private static CNG expressionCng(String expression) {
        RNG rng = new RNG(7L);
        NoiseGenerator generator = new ExpressionNoise(rng, new IrisExpression().setExpression(expression));
        return new CNG(rng, generator, 1D, 1).bake();
    }

    @Test
    public void twoDimensionalSamplesReachTheExpressionAsTwoDimensional() {
        CNG cng = expressionCng("z");

        assertEquals(MISSING_AXIS_IN_2D, cng.noise(12D, 34D), TOLERANCE);
        assertEquals(MISSING_AXIS_IN_2D, cng.noiseFast2D(12D, 34D), TOLERANCE);
        assertEquals(MISSING_AXIS_IN_2D, cng.noise(new double[]{12D, 34D}), TOLERANCE);
    }

    @Test
    public void twoDimensionalSamplesKeepTheWorldsXAndZ() {
        CNG cng = expressionCng("x * 1000 + y");

        assertEquals(12034D, cng.noise(12D, 34D), TOLERANCE);
        assertEquals(12034D, cng.noiseFast2D(12D, 34D), TOLERANCE);
    }

    @Test
    public void signedTwoDimensionalSamplesStayTwoDimensional() {
        CNG cng = expressionCng("z");

        assertEquals((MISSING_AXIS_IN_2D * 2D) - 1D, cng.noiseFastSigned2D(12D, 34D), TOLERANCE);
    }

    @Test
    public void threeDimensionalSamplesKeepAllThreeAxes() {
        CNG cng = expressionCng("x * 1000000 + y * 1000 + z");

        assertEquals(12034056D, cng.noise(12D, 34D, 56D), TOLERANCE);
        assertEquals(12034056D, cng.noiseFast3D(12D, 34D, 56D), TOLERANCE);
        assertEquals(12034056D, cng.noise(new double[]{12D, 34D, 56D}), TOLERANCE);
    }

    @Test
    public void fracturedTwoDimensionalSamplesStayTwoDimensional() {
        CNG cng = expressionCng("z");
        cng.fractureWith(new CNG(new RNG(9L), 1D, 1), 10D).bake();

        assertEquals(MISSING_AXIS_IN_2D, cng.noise(12D, 34D), TOLERANCE);
        assertEquals(MISSING_AXIS_IN_2D, cng.noiseFast2D(12D, 34D), TOLERANCE);
    }

    @Test
    public void generatorsWithoutDimensionAwarenessReadThreeCoordinates() {
        NoiseGenerator generator = new NoiseGenerator() {
            @Override
            public double noise(double x) {
                return x;
            }

            @Override
            public double noise(double x, double z) {
                return x + z;
            }

            @Override
            public double noise(double x, double y, double z) {
                return x + y + z;
            }
        };

        assertEquals(3D, generator.noiseOf(2, 1D, 2D, 0D), TOLERANCE);
        assertEquals(6D, generator.noiseOf(3, 1D, 2D, 3D), TOLERANCE);
    }
}
