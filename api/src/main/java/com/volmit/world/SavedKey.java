package com.volmit.world;

import com.volmit.data.Codec;
import java.util.Objects;
import java.util.function.Supplier;

/** The identity and form of one value a module keeps with a running world across restarts
 * ({@link RunningWorld#saved}). The id is namespaced like a mantle key and should start with the owning module's id; the host
 * names its storage after it, so each segment of the namespace and of the {@code /} separated path must be a name,
 * never empty, {@code .} or {@code ..}.
 *
 * @param id      the stable storage identity, such as {@code example:moss/patches}
 * @param type    the value class
 * @param codec   how the value is stored; its version is written with the value
 * @param initial the value for a world that has none stored, or whose stored one the codec rejects
 */
public record SavedKey<T>(String id, Class<T> type, Codec<T> codec, Supplier<T> initial) {
    public SavedKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(initial, "initial");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Expected a namespaced saved ID: " + id);
        }
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Saved values require a reference type");
        }
        for (String segment : id.replace(':', '/').split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("A saved ID names files, so it has no empty, . or .. segment: " + id);
            }
        }
    }
}
