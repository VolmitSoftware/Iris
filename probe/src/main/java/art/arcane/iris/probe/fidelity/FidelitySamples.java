package art.arcane.iris.probe.fidelity;

import java.util.Objects;

public record FidelitySamples(FidelityStats iris, FidelityStats export, FidelitySampling sampling) {
    public FidelitySamples {
        Objects.requireNonNull(iris, "iris");
        Objects.requireNonNull(export, "export");
        Objects.requireNonNull(sampling, "sampling");
        if (iris.minY() != export.minY() || iris.maxY() != export.maxY()) {
            throw new IllegalArgumentException("Iris height range [" + iris.minY() + ", " + iris.maxY()
                    + ") differs from export height range [" + export.minY() + ", " + export.maxY() + ")");
        }
    }
}
