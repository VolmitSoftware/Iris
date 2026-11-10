package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;

public record JigsawStudioPoolMembership(String poolKey, int entryIndex, int weight, double chance) {
    public JigsawStudioPoolMembership {
        Objects.requireNonNull(poolKey, "Jigsaw Studio pool key");
        poolKey = poolKey.trim();
        if (poolKey.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio pool key cannot be blank");
        }
        if (entryIndex < 0) {
            throw new IllegalArgumentException("Jigsaw Studio pool entry index cannot be negative");
        }
        if (weight < 1) {
            throw new IllegalArgumentException("Jigsaw Studio pool membership weight must be positive");
        }
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D) {
            throw new IllegalArgumentException("Jigsaw Studio pool membership chance must be within 0 and 1");
        }
    }
}
