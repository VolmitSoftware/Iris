package com.volmit.data;

import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.function.Supplier;

/** The identity and form of one value a module keeps with a running world across restarts
 * ({@link com.volmit.world.RunningWorld#saved}). The module registers it in its setup
 * ({@link com.volmit.FeatureRegistry#registerSaved}), and the id is the module's id or starts with it and {@code /},
 * as a mantle key's does. The host names its storage after the id, so each segment of the namespace and of the
 * {@code /} separated path must be a name, never empty, {@code .} or {@code ..}.
 *
 * @param id      the stable storage identity, such as {@code example:moss/patches}
 * @param type    the value class
 * @param codec   how the value is stored; its version is written with the value
 * @param initial the value for a world that has none stored, or whose stored one the codec rejects
 */
public record SavedKey<T>(ResourceId id, Class<T> type, StorageCodec<T> codec, Supplier<T> initial) {
    public SavedKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(initial, "initial");
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Saved values require a reference type");
        }
        for (String segment : id.toString().replace(':', '/').split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("A saved ID names files, so it has no empty, . or .. segment: " + id);
            }
        }
    }
}
