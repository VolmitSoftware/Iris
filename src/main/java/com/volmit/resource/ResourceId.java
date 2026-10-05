package com.volmit.resource;

import java.util.Objects;

/** Case-sensitive namespaced identity. Parsing never supplies an implicit namespace. */
public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {
    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid resource ID: " + namespace + ":" + path);
        }
    }

    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator < 0) throw new IllegalArgumentException("Expected a namespaced ID: " + value);
        return new ResourceId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public String toString() { return namespace + ":" + path; }

    @Override
    public int compareTo(ResourceId other) { return toString().compareTo(other.toString()); }
}
