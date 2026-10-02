package art.arcane.iris.testsupport;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.Arrays;

public final class GeneratorSampling {
    public static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};

    private GeneratorSampling() {
    }

    public static double[] sampleSharedGenerator(IrisGeneratorStyle style, RNG seed, IrisData packData) {
        return sampleAtBlocks(style.create(seed, packData), SAMPLE_BLOCKS);
    }

    public static double[] sampleAtBlocks(CNG generator, double[][] blocks) {
        return Arrays.stream(blocks).mapToDouble(block -> sampleAt(generator, block)).toArray();
    }

    private static double sampleAt(CNG generator, double[] block) {
        return generator.noise(block[0], block[1]);
    }
}
