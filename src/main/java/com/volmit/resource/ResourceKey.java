package com.volmit.resource;

import java.util.Objects;

/** A resource ID paired with its public value type; the host owns loading and caching. */
public record ResourceKey<T>(ResourceId id, Class<T> type) {
    public ResourceKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (type.isPrimitive()) throw new IllegalArgumentException("Resources require a reference type");
    }

    public ResourceKey(String id, Class<T> type) { this(ResourceId.parse(id), type); }
}
