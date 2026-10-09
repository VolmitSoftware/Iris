package com.volmit;

import java.util.Set;

/** Where settings appear in pack JSON: the targets. The JSON key is always the feature id. */
public record FeatureAttachment(Set<Target> targets) {
    public enum Target { DIMENSION, REGION, BIOME, OBJECT_PLACEMENT }

    public FeatureAttachment {
        targets = Set.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
    }
}
