package art.arcane.iris.world;

import java.util.Objects;
import java.util.regex.Pattern;

public record WorldSlotKey(String namespace, String key) {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9/._-]+$");
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    public WorldSlotKey {
        namespace = Objects.requireNonNull(namespace, "namespace");
        key = Objects.requireNonNull(key, "key");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("World slot namespace is invalid: " + namespace);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("World slot key is invalid: " + key);
        }
    }

    public static WorldSlotKey parse(String value) {
        String requiredValue = Objects.requireNonNull(value, "value");
        int separator = requiredValue.indexOf(':');
        if (separator <= 0 || separator != requiredValue.lastIndexOf(':') || separator == requiredValue.length() - 1) {
            throw new IllegalArgumentException("World slot key must use namespace:key syntax.");
        }
        return new WorldSlotKey(
                requiredValue.substring(0, separator),
                requiredValue.substring(separator + 1)
        );
    }

    public static WorldSlotKey minecraft(String key) {
        return new WorldSlotKey("minecraft", key);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
