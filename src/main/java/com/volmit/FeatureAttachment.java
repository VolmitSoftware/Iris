package com.volmit;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Where settings appear in pack JSON: the targets, and the optional short alias a built-in module
 * may set besides the feature id, which is always the JSON key.
 */
public record FeatureAttachment(Optional<String> alias, Set<Target> targets) {
    public enum Target { DIMENSION, REGION, BIOME, OBJECT_PLACEMENT }

    private static final char NAMESPACE_SEPARATOR = ':';

    public FeatureAttachment {
        Objects.requireNonNull(alias, "alias");
        alias.ifPresent(FeatureAttachment::requireFieldName);
        targets = Set.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
    }

    /** Checks a JSON field name that is not a feature id: an alias or a field the host itself uses.
     * It is not blank and has no ':', which every feature id has, so the two never collide.
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
