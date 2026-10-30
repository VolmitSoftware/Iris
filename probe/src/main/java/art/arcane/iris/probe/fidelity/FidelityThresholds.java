package art.arcane.iris.probe.fidelity;

public record FidelityThresholds(double biomeShare, double heightQuantile, double caveBand, double objectDensity, double wallShare) {
    public FidelityThresholds {
        requireThreshold("biomeShare", biomeShare);
        requireThreshold("heightQuantile", heightQuantile);
        requireThreshold("caveBand", caveBand);
        requireThreshold("objectDensity", objectDensity);
        requireThreshold("wallShare", wallShare);
    }

    public static FidelityThresholds defaults() {
        return new FidelityThresholds(0.15D, 0.10D, 0.15D, 0.15D, 0.25D);
    }

    private static void requireThreshold(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Fidelity threshold " + name + " must be a finite non-negative number, got " + value);
        }
    }
}
