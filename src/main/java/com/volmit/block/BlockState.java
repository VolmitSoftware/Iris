package com.volmit.block;

import com.volmit.resource.ResourceId;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Immutable, platform-neutral block description with value equality.
 * Construction validates syntax, not platform availability or valid material properties.
 * Resolve through BlockRegistry before writing. Custom-content IDs are allowed.
 */
public record BlockState(ResourceId material, Map<String, String> properties) {
    public BlockState {
        Objects.requireNonNull(material, "material");
        var copy = new TreeMap<String, String>();
        properties.forEach((name, value) -> {
            if (name == null || value == null) throw new NullPointerException("Block property");
            if (!name.matches("[a-z0-9_]+") || !value.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid block property: " + name + "=" + value);
            }
            copy.put(name, value);
        });
        properties = Collections.unmodifiableMap(copy);
    }

    public BlockState(String material) { this(ResourceId.parse(material), Map.of()); }

    public BlockState withProperty(String name, String value) {
        var changed = new TreeMap<>(properties);
        changed.put(name, value);
        return new BlockState(material, changed);
    }

    /** Stable representation with properties in lexical order. */
    public String key() {
        if (properties.isEmpty()) return material.toString();
        return material + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "[", "]"));
    }
}
