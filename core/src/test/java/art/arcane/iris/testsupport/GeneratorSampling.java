package art.arcane.iris.testsupport;

import art.arcane.volmlib.util.noise.CNG;

import java.util.Arrays;

public final class GeneratorSampling {
    public static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};

    private GeneratorSampling() {
    }

    public static double[] sampleAtBlocks(CNG generator, double[][] blocks) {
        return Arrays.stream(blocks).mapToDouble(block -> sampleAt(generator, block)).toArray();
    }

    private static double sampleAt(CNG generator, double[] block) {
        return generator.noise(block[0], block[1]);
    }
}
