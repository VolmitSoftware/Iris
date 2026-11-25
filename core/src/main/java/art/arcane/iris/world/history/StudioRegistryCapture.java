package art.arcane.iris.world.history;

import java.util.List;
import java.util.Objects;

/**
 * A Studio update's registry contract and the custom biomes that wait for a server restart to generate with their
 * latest definition.
 */
public record StudioRegistryCapture(GenerationRegistryContract contract, List<String> pendingBiomes) {
    public StudioRegistryCapture {
        contract = Objects.requireNonNull(contract, "contract");
        pendingBiomes = List.copyOf(pendingBiomes);
    }
}
