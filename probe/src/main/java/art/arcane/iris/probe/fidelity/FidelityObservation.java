package art.arcane.iris.probe.fidelity;

import java.util.Objects;

public record FidelityObservation(String name, double iris, double export) {
    public FidelityObservation {
        Objects.requireNonNull(name, "name");
        if (!Double.isFinite(iris) || !Double.isFinite(export)) {
            throw new IllegalArgumentException("Fidelity observation " + name + " has a non-finite value: iris=" + iris + " export=" + export);
        }
    }
}
