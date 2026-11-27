package art.arcane.iris.world.history;

import java.util.List;
import java.util.Objects;

/**
 * A Studio update's registry contract and the custom biomes it keeps at their loaded definition until restart.
 */
public record StudioRegistryCapture(GenerationRegistryContract contract, List<String> deferredBiomes) {
    public StudioRegistryCapture {
        contract = Objects.requireNonNull(contract, "contract");
        deferredBiomes = List.copyOf(deferredBiomes);
    }
}
