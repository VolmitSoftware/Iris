package com.volmit.mantle;

import java.util.Objects;

/** Stable storage identity. Different IDs may use the same value class without sharing data. */
public record MantleKey<T>(String id, Class<T> type) {
    public MantleKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Expected a namespaced mantle ID: " + id);
        }
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Mantle values require a reference type");
        }
    }
}
