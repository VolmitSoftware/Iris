package art.arcane.iris.probe.fidelity;

import java.util.List;
import java.util.Objects;

public record FidelitySampling(String scope, int windowBlocks, int strideBlocks, List<Long> seeds, long columns) {
    public FidelitySampling {
        Objects.requireNonNull(scope, "scope");
        seeds = List.copyOf(seeds);
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("Fidelity sampling '" + scope + "' lists no seed");
        }
    }
}
