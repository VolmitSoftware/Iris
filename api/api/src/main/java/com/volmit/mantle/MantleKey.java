package com.volmit.mantle;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Stable storage identity. Different IDs may use the same value class without sharing data. */
public record MantleKey<T>(ResourceId id, Class<T> type) {
    public MantleKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Mantle values require a reference type");
        }
    }
}
