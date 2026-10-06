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
    private static final String PROPERTIES_START = "[";
    private static final String PROPERTIES_END = "]";
    private static final String PROPERTY_SEPARATOR = ",";

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

    /** Reads what {@link #key()} writes: {@code minecraft:oak_log} or {@code minecraft:oak_log[axis=y]}. */
    public static BlockState parse(String key) {
        Objects.requireNonNull(key, "key");
        int open = key.indexOf(PROPERTIES_START);
        if (open < 0) return new BlockState(key);
        if (!key.endsWith(PROPERTIES_END)) throw new IllegalArgumentException("Unclosed block properties: " + key);
        var properties = new TreeMap<String, String>();
        var list = key.substring(open + 1, key.length() - 1);
        for (var property : list.isEmpty() ? new String[0] : list.split(PROPERTY_SEPARATOR, -1)) {
            int equals = property.indexOf('=');
            if (equals < 0) throw new IllegalArgumentException("Block property without value: " + property + " in " + key);
            if (properties.put(property.substring(0, equals), property.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("Block property set twice: " + property + " in " + key);
            }
        }
        return new BlockState(ResourceId.parse(key.substring(0, open)), properties);
    }

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
                .collect(Collectors.joining(PROPERTY_SEPARATOR, PROPERTIES_START, PROPERTIES_END));
    }
}
