package com.volmit;

import java.util.Objects;
import java.util.Set;

/** Where settings appear in pack JSON: the targets. The JSON key is declared by {@link com.volmit.config.Feature#key}. */
public record FeatureAttachment(Set<Target> targets) {
    public enum Target { DIMENSION, REGION, BIOME, OBJECT_PLACEMENT }

    private static final char NAMESPACE_SEPARATOR = ':';

    public FeatureAttachment {
        targets = Set.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
    }

    /** Checks a JSON field name that is not a feature id: a settings key or a field the host itself uses.
     * It is not blank and has no ':', which every feature id has.
     */
    public static String requireFieldName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Field must not be blank");
        if (name.indexOf(NAMESPACE_SEPARATOR) >= 0) {
            throw new IllegalArgumentException("Field " + name + " contains ':', which only feature ids may contain");
        }
        return name;
    }
}
