package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static art.arcane.iris.testsupport.GeneratorSampling.sampleAtBlocks;
import static org.junit.Assert.assertArrayEquals;

public class IrisDimensionCarvingEntryTest {
    private static final long WORLD_SEED = 7L;
    private static final String CARVING_ENTRY_ID = "deep-caves";
    private static final double CHILD_SHRINK_FACTOR = 4D;
    private static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};

    private final IrisData packData = RunningEnginePackData.create();
    private final IrisDimensionCarvingEntry carvingEntry = createCarvingEntry();

    @Test
    public void doesBuildingTheChildrenGeneratorLeaveTheSharedChildStyleGeneratorUnscaled() {
        assertArrayEquals(sampleUntouchedSharedChildStyleGenerator(), sampleSharedChildStyleGeneratorAfterChildrenGeneratorBuilt(), 0D);
    }

    private double[] sampleSharedChildStyleGeneratorAfterChildrenGeneratorBuilt() {
        carvingEntry.getChildrenGenerator(WORLD_SEED, packData);
        return sampleSharedChildStyleGenerator(carvingEntry.getChildStyle());
    }

    private double[] sampleUntouchedSharedChildStyleGenerator() {
        return sampleSharedChildStyleGenerator(createSimplexStyle());
    }

    private double[] sampleSharedChildStyleGenerator(IrisGeneratorStyle childStyle) {
        return sampleAtBlocks(childStyle.create(deriveSharedChildStyleSeed(), packData), SAMPLE_BLOCKS);
    }

    private RNG deriveSharedChildStyleSeed() {
        return IrisDimensionCarvingEntry.deriveChildStyleSeed(carvingEntry.deriveChildrenGeneratorSeed(WORLD_SEED));
    }

    private static IrisDimensionCarvingEntry createCarvingEntry() {
        return withShrinkingSimplexChildStyle(new IrisDimensionCarvingEntry().setId(CARVING_ENTRY_ID));
    }

    private static IrisDimensionCarvingEntry withShrinkingSimplexChildStyle(IrisDimensionCarvingEntry carvingEntry) {
        return carvingEntry.setChildStyle(createSimplexStyle()).setChildShrinkFactor(CHILD_SHRINK_FACTOR);
    }

    private static IrisGeneratorStyle createSimplexStyle() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
    }
}
