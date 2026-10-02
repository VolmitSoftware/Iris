package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static art.arcane.iris.testsupport.GeneratorSampling.SAMPLE_BLOCKS;
import static art.arcane.iris.testsupport.GeneratorSampling.sampleSharedGenerator;
import static org.junit.Assert.assertArrayEquals;

public class IrisDecoratorVarianceGeneratorTest {
    private static final long WORLD_SEED = 7L;
    private static final double VARIANCE_ZOOM = 4D;

    private final IrisData packData = RunningEnginePackData.create();
    private final IrisDecorator decorator = createDecoratorWithZoomedSimplexVariance();

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
        return sampleSharedGenerator(varianceStyle, decorator.deriveVarianceSeed(new RNG(WORLD_SEED), packData), packData);
    }

    private static IrisDecorator createDecoratorWithZoomedSimplexVariance() {
        return new IrisDecorator().setPalette(new KList<IrisBlockData>()).setVariance(createZoomedSimplexStyle());
    }

    private static IrisGeneratorStyle createZoomedSimplexStyle() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(VARIANCE_ZOOM);
    }
}
