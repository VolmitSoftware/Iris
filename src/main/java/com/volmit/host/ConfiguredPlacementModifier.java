package com.volmit.host;

import com.volmit.generation.Dependencies;
import com.volmit.placement.PlacementModifier;
import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Pack-load placement declaration snapshot. The configured modifier must be immutable. */
public final class ConfiguredPlacementModifier {
    private final ResourceId occurrence;
    private final PlacementModifier modifier;
    private final ResourceId id;
    private final Dependencies dependencies;
    private final int writeRadius;

    public ConfiguredPlacementModifier(ResourceId occurrence, PlacementModifier modifier) {
        this.occurrence = Objects.requireNonNull(occurrence, "occurrence");
        this.modifier = Objects.requireNonNull(modifier, "modifier");
        id = ResourceId.parse(modifier.id());
        dependencies = Objects.requireNonNull(modifier.depends(), "dependencies");
        writeRadius = modifier.writeRadius();
        if (writeRadius < 0) {
            throw new IllegalArgumentException("Negative placement radius: " + id);
        }
    }

    public ResourceId occurrence() {
        return occurrence;
    }

    public PlacementModifier modifier() {
        return modifier;
    }

    public ResourceId id() {
        return id;
    }

    public Dependencies dependencies() {
        return dependencies;
    }

    public int writeRadius() {
        return writeRadius;
    }
}
