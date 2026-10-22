package art.arcane.iris.generation.mantle;

final class CaveCellKernel {
    static final int NONE = 0;
    static final int ALL = 1;
    static final int MIXED = 2;
    private static final double GUARD_ULPS = 32D;
    private static final double MAXIMUM_MAGNITUDE = 0x1p500;

    private CaveCellKernel() {
    }

    static int decide(double d00, double d01, double d10, double d11, double thresholdLow, double thresholdHigh) {
        double low = Math.min(Math.min(d00, d01), Math.min(d10, d11));
        double high = Math.max(Math.max(d00, d01), Math.max(d10, d11));
        double magnitude = Math.max(Math.abs(low), Math.abs(high));
        if (!(magnitude < MAXIMUM_MAGNITUDE)) {
            return MIXED;
        }
        double guard = Math.ulp(magnitude) * GUARD_ULPS;
        if (high + guard <= thresholdLow) {
            return ALL;
        }
        if (low - guard > thresholdHigh) {
            return NONE;
        }
        return MIXED;
    }

    static double threshold(double localThreshold, double normalizationFactor, double inverseNormalization) {
        return (localThreshold * normalizationFactor) * inverseNormalization;
    }
}
