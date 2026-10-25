package art.arcane.iris.generation.hydrology.query;

import java.util.Objects;

public record HydrologyFeatureRef(long id, HydrologyFeatureType type, int x, int y, int z) {
    public HydrologyFeatureRef {
        Objects.requireNonNull(type, "type");
    }
}
