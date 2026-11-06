package com.volmit.tooling;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry of a {@link SceneView}'s catalogue.
 *
 * @param key      what the view names the entry by in {@link SceneView#scene}, such as an object's {@code trees/oak}
 * @param name     what the studio lists it as
 * @param group    the folder the studio lists it under, such as {@code trees}; empty to list it at the top
 * @param variants how many scenes the entry has, at least 1
 */
public record SceneEntry(String key, String name, Optional<String> group, int variants) {
    public SceneEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A scene entry needs a key");
        }
        if (group.isPresent() && group.get().isBlank()) {
            throw new IllegalArgumentException("Scene entry " + key + " has a blank group; leave it empty instead");
        }
        if (variants < 1) {
            throw new IllegalArgumentException("Scene entry " + key + " has " + variants + " variants; an entry has at least 1");
        }
    }
}
