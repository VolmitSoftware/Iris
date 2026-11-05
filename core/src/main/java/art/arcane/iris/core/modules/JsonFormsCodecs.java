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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.volmit.config.JsonForms;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes IrisApi's {@link JsonForms} classes through their codec, as the plain values Gson reads into an
 * {@code Object}: maps, lists, {@code Double}s, strings and booleans. Each codec is created once.
 */
public final class JsonFormsCodecs implements TypeAdapterFactory {
    private final Map<Class<?>, JsonForms.Codec<?>> codecs = new ConcurrentHashMap<>();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        JsonForms forms = type.getRawType().getAnnotation(JsonForms.class);
        if (forms == null) {
            return null;
        }
        JsonForms.Codec<T> codec = codecOf(forms);
        TypeAdapter<Object> plainValues = gson.getAdapter(Object.class);
        String typeName = type.getRawType().getSimpleName();
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                plainValues.write(out, codec.write(value));
            }

            @Override
            public T read(JsonReader in) throws IOException {
                Object json = plainValues.read(in);
                try {
                    return codec.read(json);
                } catch (IllegalArgumentException notAForm) {
                    throw new JsonParseException(typeName + ": " + notAForm.getMessage(), notAForm);
                }
            }
        }.nullSafe();
    }

    @SuppressWarnings("unchecked")
    private <T> JsonForms.Codec<T> codecOf(JsonForms forms) {
        return (JsonForms.Codec<T>) codecs.computeIfAbsent(forms.value(), JsonFormsCodecs::newCodec);
    }

    private static JsonForms.Codec<?> newCodec(Class<?> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (JsonForms.Codec<?>) constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Codec " + type.getName() + " cannot be created with its no-argument constructor: " + failure, failure);
        }
    }
}
