package art.arcane.iris.engine.object.terrain;

import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisGeneratorExponentTest {
    private static final long SUPER_SEED = 1234L;
    private static final double SAMPLE_X = 10D;
    private static final double SAMPLE_Z = -4D;

    private static IrisGenerator constantGenerator(double value) {
        IrisNoiseGenerator constant = new IrisNoiseGenerator(false).setOffsetY(value);
        return new IrisGenerator().setComposite(new KList<IrisNoiseGenerator>().qadd(constant));
    }

    private static double height(IrisGenerator generator) {
        return generator.getHeight(SAMPLE_X, SAMPLE_Z, SUPER_SEED);
    }

    @Test
    public void defaultExponentLeavesTheCompositeUntouched() {
        assertEquals(0.3D, height(constantGenerator(0.3D)), 0D);
    }

    @Test
    public void exponentCurvesThePositiveCompositeValue() {
        assertEquals(0.09D, height(constantGenerator(0.3D).setExponent(2D)), 1e-12);
        assertEquals(Math.sqrt(0.25D), height(constantGenerator(0.25D).setExponent(0.5D)), 1e-12);
    }

    @Test
    public void exponentKeepsTheSignOfNegativeCompositeValues() {
        assertEquals(-0.09D, height(constantGenerator(-0.3D).setExponent(2D)), 1e-12);
    }

    @Test
    public void exponentActsBeforeOpacity() {
        assertEquals(0.045D, height(constantGenerator(0.3D).setExponent(2D).setOpacity(0.5D)), 1e-12);
    }

    @Test
    public void exponentActsOnTheCombinedValueOfAllComposites() {
        IrisNoiseGenerator low = new IrisNoiseGenerator(false).setOffsetY(0.2D);
        IrisNoiseGenerator high = new IrisNoiseGenerator(false).setOffsetY(0.6D);
        IrisGenerator generator = new IrisGenerator()
                .setComposite(new KList<IrisNoiseGenerator>().qadd(low).qadd(high))
                .setExponent(2D);

        assertEquals(0.16D, height(generator), 1e-12);
    }

    @Test
    public void exponentCurvesTheProductOfMultiplicativeComposites() {
        IrisNoiseGenerator first = new IrisNoiseGenerator(false).setOffsetY(0.5D);
        IrisNoiseGenerator second = new IrisNoiseGenerator(false).setOffsetY(0.4D);
        IrisGenerator generator = new IrisGenerator()
                .setComposite(new KList<IrisNoiseGenerator>().qadd(first).qadd(second))
                .setMultiplicitive(true)
                .setExponent(2D);

        assertEquals(0.04D, height(generator), 1e-12);
    }
}
