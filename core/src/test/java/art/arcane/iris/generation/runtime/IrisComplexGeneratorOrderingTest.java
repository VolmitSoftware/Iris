package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.Assert.assertEquals;

public class IrisComplexGeneratorOrderingTest {
    private static final int[][] PERMUTATIONS = {
            {0, 1, 2, 3},
            {3, 2, 1, 0},
            {1, 3, 0, 2},
            {2, 0, 3, 1}
    };

    @Test
    public void frozenGroupsUseStableOrderAcrossInsertionPermutations() {
        IrisInterpolator bilinearWide = interpolator(InterpolationMethod.BILINEAR, 16D);
        IrisInterpolator starcast = interpolator(InterpolationMethod.STARCAST_3, 8D);
        IrisInterpolator bilinearNarrow = interpolator(InterpolationMethod.BILINEAR, 4D);
        List<IrisGenerator> generators = List.of(
                generator("zeta", starcast, 1L, 0D),
                generator("beta", bilinearWide, 2L, 0D),
                generator("alpha", bilinearWide, 3L, 0D),
                generator("delta", bilinearNarrow, 4L, 0D)
        );

        String expected = "BILINEAR@4.0[delta]|BILINEAR@16.0[alpha,beta]|STARCAST_3@8.0[zeta]";
        for (int[] permutation : PERMUTATIONS) {
            IrisComplex.GeneratorGroup[] groups = IrisComplex.freezeGeneratorGroups(
                    groupByInterpolator(generators, permutation)
            );
            assertEquals(expected, signature(groups));
        }
    }

    @Test
    public void frozenGeneratorOrderProducesBitIdenticalOutputAcrossInsertionPermutations() {
        IrisInterpolator interpolator = interpolator(InterpolationMethod.BILINEAR_STARCAST_6, 7D);
        List<IrisGenerator> generators = List.of(
                generator("alpha", interpolator, 1L, 1.0E16D),
                generator("beta", interpolator, 2L, -1.0E16D),
                generator("gamma", interpolator, 3L, 1D),
                generator("omega", interpolator, 4L, 0D)
        );
        long expectedBits = Double.doubleToRawLongBits(0.25D);

        for (int[] permutation : PERMUTATIONS) {
            IrisComplex.GeneratorGroup[] groups = IrisComplex.freezeGeneratorGroups(
                    groupByInterpolator(generators, permutation)
            );
            double output = 0D;
            for (IrisComplex.GeneratorGroup group : groups) {
                output += IrisComplex.averageGeneratorHeights(
                        group.generators(),
                        0D,
                        1D,
                        0D,
                        0D,
                        0L
                );
            }
            assertEquals(expectedBits, Double.doubleToRawLongBits(output));
        }
    }

    @Test
    public void emptyGeneratorCollectionFreezesAndAccumulatesAsEmpty() {
        Map<IrisInterpolator, Set<IrisGenerator>> generators = Map.of();

        assertEquals(0, IrisComplex.freezeGeneratorGroups(generators).length);
        assertEquals(0D, IrisComplex.averageGeneratorHeights(
                new IrisGenerator[0],
                -1D,
                1D,
                0D,
                0D,
                0L
        ), 0D);
    }

    private IrisInterpolator interpolator(InterpolationMethod function, double horizontalScale) {
        return new IrisInterpolator()
                .setFunction(function)
                .setHorizontalScale(horizontalScale);
    }

    private FixedGenerator generator(
            String loadKey,
            IrisInterpolator interpolator,
            long seed,
            double height
    ) {
        FixedGenerator generator = new FixedGenerator(height);
        generator.setLoadKey(loadKey);
        generator.setInterpolator(interpolator);
        generator.setSeed(seed);
        return generator;
    }

    private Map<IrisInterpolator, Set<IrisGenerator>> groupByInterpolator(
            List<IrisGenerator> generators,
            int[] permutation
    ) {
        Map<IrisInterpolator, Set<IrisGenerator>> groups = new LinkedHashMap<>();
        for (int index : permutation) {
            IrisGenerator generator = generators.get(index);
            groups.computeIfAbsent(generator.getInterpolator(), ignored -> new LinkedHashSet<>()).add(generator);
        }
        return groups;
    }

    private String signature(IrisComplex.GeneratorGroup[] groups) {
        StringJoiner groupSignature = new StringJoiner("|");
        for (IrisComplex.GeneratorGroup group : groups) {
            StringJoiner generatorKeys = new StringJoiner(",");
            for (IrisGenerator generator : group.generators()) {
                generatorKeys.add(generator.getLoadKey());
            }
            groupSignature.add(group.interpolator().getFunction().name()
                    + "@" + group.interpolator().getHorizontalScale()
                    + "[" + generatorKeys + "]");
        }
        return groupSignature.toString();
    }

    private static final class FixedGenerator extends IrisGenerator {
        private final double height;

        private FixedGenerator(double height) {
            this.height = height;
        }

        @Override
        public double getHeight(double x, double z, long seed) {
            return height;
        }
    }
}
