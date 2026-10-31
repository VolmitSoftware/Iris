package art.arcane.iris.probe.fidelity;

import java.util.Objects;

public record FidelityMetric(String name, double iris, double export, double delta, boolean pass) {
    public FidelityMetric {
        Objects.requireNonNull(name, "name");
        if (!Double.isFinite(iris) || !Double.isFinite(export) || !Double.isFinite(delta)) {
            throw new IllegalArgumentException("Fidelity metric " + name + " has a non-finite value: iris=" + iris
                    + " export=" + export + " delta=" + delta);
        }
    }
}
