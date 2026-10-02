package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixed;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixedRule;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;

import static art.arcane.iris.testsupport.GeneratorSampling.sampleAtBlocks;
import static org.junit.Assert.assertArrayEquals;

public class IrisBiomeChildrenGeneratorTest {
    private static final String SHARED_CHILD_STYLE_GENERATOR_BUG =
            "IrisBiome scales the shared child style generator instead of its own. "
                    + "Ref.: https://github.com/VolmitSoftware/Iris/issues/1251";
    private static final long WORLD_SEED = 7L;
    private static final int CHILDREN_SIGNATURE = 123;
    private static final double CHILD_SHRINK_FACTOR = 4D;
    private static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};

    private final IrisData packData = RunningEnginePackData.create();
    private final IrisBiome biome = createBiomeWithSimplexChildStyle(packData);

    @Rule
    public final ExpectedToFailUntilFixedRule expectedFailures = new ExpectedToFailUntilFixedRule();

    @ExpectedToFailUntilFixed(SHARED_CHILD_STYLE_GENERATOR_BUG)
    @Test
    public void doesBuildingTheChildrenGeneratorLeaveTheSharedChildStyleGeneratorUnscaled() {
        assertArrayEquals(sampleUntouchedSharedChildStyleGenerator(), sampleSharedChildStyleGeneratorAfterChildrenGeneratorBuilt(), 0D);
    }

    private double[] sampleSharedChildStyleGeneratorAfterChildrenGeneratorBuilt() {
        buildChildrenGenerator();
        return sampleSharedChildStyleGenerator(biome.getChildStyle());
    }

    private void buildChildrenGenerator() {
        biome.getChildrenGenerator(new RNG(WORLD_SEED), CHILDREN_SIGNATURE, CHILD_SHRINK_FACTOR);
    }

    private double[] sampleUntouchedSharedChildStyleGenerator() {
        return sampleSharedChildStyleGenerator(createSimplexStyle());
    }

    private double[] sampleSharedChildStyleGenerator(IrisGeneratorStyle childStyle) {
        return sampleAtBlocks(childStyle.create(deriveSharedChildStyleSeed(), packData), SAMPLE_BLOCKS);
    }

    private static RNG deriveSharedChildStyleSeed() {
        return IrisBiome.deriveChildStyleSeed(new RNG(WORLD_SEED), CHILDREN_SIGNATURE);
    }

    private static IrisBiome createBiomeWithSimplexChildStyle(IrisData packData) {
        IrisBiome biome = new IrisBiome().setChildStyle(createSimplexStyle());
        biome.setLoader(packData);
        return biome;
    }

    private static IrisGeneratorStyle createSimplexStyle() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
    }
}
