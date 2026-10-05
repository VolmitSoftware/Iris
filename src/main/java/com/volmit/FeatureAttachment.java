package com.volmit;

import java.util.Objects;
import java.util.Set;

/** Maps settings to an ordinary existing JSON field, without an extension wrapper. */
public record FeatureAttachment(String field, Set<Target> targets) {
    public enum Target { DIMENSION, REGION, BIOME, OBJECT_PLACEMENT }

    public FeatureAttachment {
        Objects.requireNonNull(field, "field");
        if (field.isBlank()) throw new IllegalArgumentException("Field must not be blank");
        targets = Set.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
    }
}
