package art.arcane.iris.generation.simd;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

public final class VectorSimdKernels implements SimdKernels {
    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> INT_SPECIES = VectorSpecies.of(int.class, VectorShape.forBitSize(DOUBLE_SPECIES.length() * Integer.SIZE));

    @Override
    public String describe() {
        return DOUBLE_SPECIES.length() + "x64-bit lanes, " + DOUBLE_SPECIES.vectorShape();
    }

    @Override
    public void roundToInt(double[] source, int[] target, int length) {
        int lanes = DOUBLE_SPECIES.length();
        int bound = DOUBLE_SPECIES.loopBound(length);
        int index = 0;
        for (; index < bound; index += lanes) {
            DoubleVector shifted = DoubleVector.fromArray(DOUBLE_SPECIES, source, index).add(0.5D);
            IntVector truncated = (IntVector) shifted.convertShape(VectorOperators.D2I, INT_SPECIES, 0);
            DoubleVector truncatedBack = (DoubleVector) truncated.convertShape(VectorOperators.I2D, DOUBLE_SPECIES, 0);
            VectorMask<Integer> needsDecrement = shifted.lt(truncatedBack).cast(INT_SPECIES);
            truncated.sub(1, needsDecrement).intoArray(target, index);
        }

        for (; index < length; index++) {
            target[index] = (int) Math.round(source[index]);
        }
    }

    @Override
    public double sum(double[] values, int length) {
        // Deliberately scalar: lane-wise accumulation reassociates FP addition, so the vector
        // and scalar kernels returned different low bits for the same input. Generation-path
        // reductions must be bit-exact regardless of which kernel set is installed.
        double total = 0D;
        for (int index = 0; index < length; index++) {
            total += values[index];
        }

        return total;
    }

    @Override
    public double max(double[] values, int length) {
        int lanes = DOUBLE_SPECIES.length();
        int bound = DOUBLE_SPECIES.loopBound(length);
        DoubleVector accumulator = DoubleVector.broadcast(DOUBLE_SPECIES, Double.NEGATIVE_INFINITY);
        int index = 0;
        for (; index < bound; index += lanes) {
            accumulator = accumulator.max(DoubleVector.fromArray(DOUBLE_SPECIES, values, index));
        }

        double best = accumulator.reduceLanes(VectorOperators.MAX);
        for (; index < length; index++) {
            if (values[index] > best) {
                best = values[index];
            }
        }

        return best;
    }
}
