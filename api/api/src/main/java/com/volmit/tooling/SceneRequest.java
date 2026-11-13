package com.volmit.tooling;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Which scene of a {@link SceneView} the studio asks for.
 *
 * @param key     the entry, as {@link SceneEntry#key()} names it
 * @param variant from 0 to the entry's {@link SceneEntry#variants()}, exclusive
 * @param size    how many blocks across to build the scene, one of the view's {@link SceneView#sizes()}; empty when the
 *                view has none
 */
public record SceneRequest(String key, int variant, OptionalInt size) {
    public SceneRequest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(size, "size");
    }

    public static SceneRequest of(String key, int variant) {
        return new SceneRequest(key, variant, OptionalInt.empty());
    }
}
