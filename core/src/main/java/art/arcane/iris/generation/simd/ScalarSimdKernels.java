package art.arcane.iris.generation.simd;

public final class ScalarSimdKernels implements SimdKernels {
    @Override
    public String describe() {
        return "scalar";
    }

    @Override
    public void roundToInt(double[] source, int[] target, int length) {
        for (int index = 0; index < length; index++) {
            target[index] = (int) Math.round(source[index]);
        }
    }

    @Override
    public double sum(double[] values, int length) {
        double total = 0D;
        for (int index = 0; index < length; index++) {
            total += values[index];
        }

        return total;
    }

    @Override
    public double max(double[] values, int length) {
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < length; index++) {
            if (values[index] > best) {
                best = values[index];
            }
        }

        return best;
    }
}
