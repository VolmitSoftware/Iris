package art.arcane.iris.generation.hydrology;

import java.util.Objects;

public record DrainageNode(
        long id,
        int x,
        int z,
        HydrologyTerrainSample terrain,
        double potential,
        long outletId
) {
    public DrainageNode {
        Objects.requireNonNull(terrain, "terrain");
        if (!Double.isFinite(potential) || potential < 0D) {
            throw new IllegalArgumentException("Drainage potential must be finite and non-negative.");
        }
        if (terrain.ocean()) {
            throw new IllegalArgumentException("Drainage nodes must remain landward of the first ocean column.");
        }
    }

    public HydrologyPoint naturalPoint() {
        return new HydrologyPoint(x, terrain.naturalHeight(), z);
    }
}
