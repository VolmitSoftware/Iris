package com.volmit.block;

import com.volmit.resource.ResourceId;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Immutable, platform-neutral block description with value equality.
 * Construction validates syntax, not platform availability or valid material properties.
 * Resolve through BlockRegistry before writing. Custom-content IDs are allowed.
 */
public record BlockState(ResourceId material, Map<String, String> properties) {
    private static final String PROPERTIES_START = "[";
    private static final String PROPERTIES_END = "]";
    private static final String PROPERTY_SEPARATOR = ",";
    private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern PROPERTY_VALUE = Pattern.compile("[a-z0-9_.-]+");

    public BlockState {
        Objects.requireNonNull(material, "material");
        if (!(properties instanceof CheckedProperties)) {
            var copy = new TreeMap<String, String>();
            properties.forEach((name, value) -> copy.put(requireValid(name, value), value));
            properties = new CheckedProperties(copy);
        }
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

    /** This state with {@code name} set to {@code value}. Copies the properties once and checks only the new one. */
    public BlockState withProperty(String name, String value) {
        var changed = new TreeMap<>(properties);
        changed.put(requireValid(name, value), value);
        return new BlockState(material, new CheckedProperties(changed));
    }

    /** Stable representation with properties in lexical order. */
    public String key() {
        if (properties.isEmpty()) return material.toString();
        return material + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(PROPERTY_SEPARATOR, PROPERTIES_START, PROPERTIES_END));
    }

    /** Returns {@code name} once it and {@code value} are a valid property. */
    private static String requireValid(String name, String value) {
        if (name == null || value == null) throw new NullPointerException("Block property");
        if (!PROPERTY_NAME.matcher(name).matches() || !PROPERTY_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid block property: " + name + "=" + value);
        }
        return name;
    }

    /** Validated properties in key order that nobody can change. Only this class makes them, so the constructor takes
     * them as they are instead of checking and copying them again.
     */
    private static final class CheckedProperties extends AbstractMap<String, String> {
        private final Map<String, String> sorted;

        private CheckedProperties(TreeMap<String, String> sorted) {
            this.sorted = Collections.unmodifiableMap(sorted);
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return sorted.entrySet();
        }

        @Override
        public String get(Object key) {
            return sorted.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return sorted.containsKey(key);
        }

        @Override
        public int size() {
            return sorted.size();
        }
    }
}
