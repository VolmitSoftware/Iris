package com.volmit.resource;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Case-sensitive namespaced identity. {@link #parse} never supplies an implicit namespace; {@link #ofPackKey} and
 * {@link #ofPlatformKey} turn the keys packs write into ids. */
public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern NOT_PATH = Pattern.compile("[^a-z0-9_./-]");
    private static final String PATH_REPLACEMENT = "_";
    private static final String PLATFORM_NAMESPACE = "minecraft";

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!isNamespace(namespace) || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid resource ID: " + namespace + ":" + path);
        }
    }

    /** Whether {@code value} is a valid namespace on its own, such as {@code iris}. */
    public static boolean isNamespace(String value) {
        return NAMESPACE.matcher(value).matches();
    }

    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator < 0) throw new IllegalArgumentException("Expected a namespaced ID: " + value);
        return new ResourceId(value.substring(0, separator), value.substring(separator + 1));
    }

    /**
     * The id a pack gives the resource its files name by {@code key}, such as {@code dungeon/chest} for
     * {@code loot/dungeon/chest.json}: the pack's namespace and the key as the path, lower-cased, with every
     * character outside {@code [a-z0-9_./-]} replaced by {@code _}. Keys that differ only in such characters share
     * an id.
     */
    public static ResourceId ofPackKey(String namespace, String key) {
        Objects.requireNonNull(key, "key");
        return new ResourceId(namespace, NOT_PATH.matcher(key.toLowerCase(Locale.ROOT)).replaceAll(PATH_REPLACEMENT));
    }

    /**
     * The id of a platform key as packs write it, such as {@code minecraft:diamond_sword} for {@code DIAMOND_SWORD} or
     * {@code Diamond Sword}: trimmed, lower-cased, spaces as {@code _}, in the {@code minecraft} namespace when it names
     * none. Throws IllegalArgumentException when the result is still no id, such as for {@code a:b:c}.
     */
    public static ResourceId ofPlatformKey(String key) {
        Objects.requireNonNull(key, "key");
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return parse(normalized.indexOf(':') < 0 ? PLATFORM_NAMESPACE + ":" + normalized : normalized);
    }

    @Override
    public String toString() { return namespace + ":" + path; }

    @Override
    public int compareTo(ResourceId other) { return toString().compareTo(other.toString()); }
}
