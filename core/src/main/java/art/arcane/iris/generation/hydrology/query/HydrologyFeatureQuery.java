package art.arcane.iris.generation.hydrology.query;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record HydrologyFeatureQuery(Set<HydrologyFeatureType> types) {
    public HydrologyFeatureQuery {
        Objects.requireNonNull(types, "types");
        if (types.isEmpty()) {
            throw new IllegalArgumentException("A hydrology feature query needs at least one feature type");
        }
        types = Collections.unmodifiableSet(EnumSet.copyOf(types));
    }

    public static HydrologyFeatureQuery of(HydrologyFeatureType first, HydrologyFeatureType... rest) {
        return new HydrologyFeatureQuery(EnumSet.of(first, rest));
    }

    public boolean matches(HydrologyFeatureType type) {
        return types.contains(type);
    }
}
