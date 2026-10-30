package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;

import java.util.Objects;

public record WeightedState(VanillaBlockState state, int weight) {
    public WeightedState {
        Objects.requireNonNull(state);
        if (weight < 1) {
            throw new IllegalArgumentException("Block state weight must be positive, got " + weight + " for " + state.canonical());
        }
    }
}
