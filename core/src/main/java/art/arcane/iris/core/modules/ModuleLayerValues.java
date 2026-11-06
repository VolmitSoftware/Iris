/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import com.volmit.data.Codec;
import com.volmit.mantle.MantleKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The values module mantle layers hold at one block, by layer id. A value a stage wrote stays decoded together
 * with its layer's codec; a value read from disk stays encoded until a stage reads it with its layer's codec.
 * A layer no loaded module registers therefore keeps its stored bytes and is written back unchanged.
 */
public final class ModuleLayerValues {
    public static final ModuleLayerValues EMPTY = new ModuleLayerValues(new TreeMap<>());

    private final Map<String, LayerValue> layers;

    private ModuleLayerValues(TreeMap<String, LayerValue> layers) {
        this.layers = Collections.unmodifiableMap(layers);
    }

    /** The value of {@code key}, decoded with {@code codec} if it was read from disk; null when the layer holds none. */
    public <T> T get(MantleKey<T> key, Codec<T> codec) {
        LayerValue value = layers.get(key.id());
        return value == null ? null : key.type().cast(value.decode(key, codec));
    }

    public boolean has(String layerId) {
        return layers.containsKey(layerId);
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    /** These values with {@code key} holding {@code value}. */
    public <T> ModuleLayerValues with(MantleKey<T> key, Codec<T> codec, T value) {
        TreeMap<String, LayerValue> changed = new TreeMap<>(layers);
        changed.put(key.id(), new Decoded<>(value, codec));
        return new ModuleLayerValues(changed);
    }

    /** These values without {@code layerId}'s. */
    public ModuleLayerValues without(String layerId) {
        if (!layers.containsKey(layerId)) {
            return this;
        }
        TreeMap<String, LayerValue> changed = new TreeMap<>(layers);
        changed.remove(layerId);
        return changed.isEmpty() ? EMPTY : new ModuleLayerValues(changed);
    }

    /** These values without any of {@code layerIds}'. */
    public ModuleLayerValues withoutAll(Set<String> layerIds) {
        if (layerIds.stream().noneMatch(layers::containsKey)) {
            return this;
        }
        TreeMap<String, LayerValue> changed = new TreeMap<>(layers);
        changed.keySet().removeAll(layerIds);
        return changed.isEmpty() ? EMPTY : new ModuleLayerValues(changed);
    }

    /** Layer count, then per layer its id, codec version, payload length and payload. */
    public void write(DataOutputStream output) throws IOException {
        output.writeInt(layers.size());
        for (Map.Entry<String, LayerValue> layer : layers.entrySet()) {
            output.writeUTF(layer.getKey());
            layer.getValue().encoded().write(output);
        }
    }

    public static ModuleLayerValues read(DataInputStream input) throws IOException {
        int count = input.readInt();
        TreeMap<String, LayerValue> layers = new TreeMap<>();
        for (int index = 0; index < count; index++) {
            layers.put(input.readUTF(), Encoded.read(input));
        }
        return layers.isEmpty() ? EMPTY : new ModuleLayerValues(layers);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleLayerValues values && layers.equals(values.layers);
    }

    @Override
    public int hashCode() {
        return layers.hashCode();
    }

    private sealed interface LayerValue permits Decoded, Encoded {
        Object decode(MantleKey<?> key, Codec<?> codec);

        Encoded encoded() throws IOException;
    }

    private record Decoded<T>(T value, Codec<T> codec) implements LayerValue {
        @Override
        public Object decode(MantleKey<?> key, Codec<?> requested) {
            return value;
        }

        @Override
        public Encoded encoded() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            codec.write(new DataOutputStream(bytes), value);
            return new Encoded(codec.version(), bytes.toByteArray());
        }
    }

    private record Encoded(int version, byte[] payload) implements LayerValue {
        static Encoded read(DataInputStream input) throws IOException {
            int version = input.readInt();
            byte[] payload = new byte[input.readInt()];
            input.readFully(payload);
            return new Encoded(version, payload);
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(version);
            output.writeInt(payload.length);
            output.write(payload);
        }

        @Override
        public Object decode(MantleKey<?> key, Codec<?> codec) {
            try {
                return codec.read(new DataInputStream(new ByteArrayInputStream(payload)), version);
            } catch (IOException failure) {
                throw new UncheckedIOException("Mantle layer " + key.id() + " holds version " + version
                        + " data its codec cannot read", failure);
            }
        }

        @Override
        public Encoded encoded() {
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Encoded encoded && version == encoded.version && Arrays.equals(payload, encoded.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, Arrays.hashCode(payload));
        }
    }
}
