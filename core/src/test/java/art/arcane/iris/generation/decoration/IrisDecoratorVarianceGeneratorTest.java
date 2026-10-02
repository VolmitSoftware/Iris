package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixed;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixedRule;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;

import static art.arcane.iris.testsupport.GeneratorSampling.sampleAtBlocks;
import static org.junit.Assert.assertArrayEquals;

public class IrisDecoratorVarianceGeneratorTest {
    private static final String SHARED_VARIANCE_STYLE_GENERATOR_BUG =
            "IrisDecorator scales the shared variance style generator instead of its own. "
                    + "Ref.: https://github.com/VolmitSoftware/Iris/issues/1251";
    private static final long WORLD_SEED = 7L;
    private static final double VARIANCE_ZOOM = 4D;
    private static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};

    private final IrisData packData = RunningEnginePackData.create();
    private final IrisDecorator decorator = createDecoratorWithZoomedSimplexVariance();

    @Rule
    public final ExpectedToFailUntilFixedRule expectedFailures = new ExpectedToFailUntilFixedRule();

    @ExpectedToFailUntilFixed(SHARED_VARIANCE_STYLE_GENERATOR_BUG)
    @Test
    public void doesBuildingTheVarianceGeneratorLeaveTheSharedVarianceStyleGeneratorUnscaled() {
        assertArrayEquals(sampleUntouchedSharedVarianceStyleGenerator(), sampleSharedVarianceStyleGeneratorAfterVarianceBuilt(), 0D);
    }

    private double[] sampleSharedVarianceStyleGeneratorAfterVarianceBuilt() {
        decorator.getVarianceGenerator(new RNG(WORLD_SEED), packData);
        return sampleSharedVarianceStyleGenerator(decorator.getVariance());
    }

    private double[] sampleUntouchedSharedVarianceStyleGenerator() {
        return sampleSharedVarianceStyleGenerator(createZoomedSimplexStyle());
    }

    private double[] sampleSharedVarianceStyleGenerator(IrisGeneratorStyle varianceStyle) {
        return sampleAtBlocks(varianceStyle.create(decorator.deriveVarianceSeed(new RNG(WORLD_SEED), packData), packData), SAMPLE_BLOCKS);
    }

    private static IrisDecorator createDecoratorWithZoomedSimplexVariance() {
        return new IrisDecorator().setPalette(new KList<IrisBlockData>()).setVariance(createZoomedSimplexStyle());
    }

    private static IrisGeneratorStyle createZoomedSimplexStyle() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(VARIANCE_ZOOM);
    }
}
