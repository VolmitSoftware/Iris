package com.volmit.iris.util.data.registry;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class RegistryTypeAdapter<T> extends TypeAdapter<T> {
    private final KeyedRegistry<T> registry;

    private RegistryTypeAdapter(KeyedRegistry<T> type) {
        this.registry = type;
    }

    @Nullable
    public static <T> RegistryTypeAdapter<T> of(@NonNull Class<T> type) {
        final var registry = RegistryUtil.lookup(type);
        return registry.isEmpty() ? null : new RegistryTypeAdapter<>(registry);
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        final var key = registry.keyOf(value);
        if (key == null) out.nullValue();
        else out.value(key.toString());
    }

    @Override
    public T read(JsonReader in) throws IOException {
        final NamespacedKey key = NamespacedKey.fromString(in.nextString());
        return key == null ? null : registry.get(key);
    }
}
