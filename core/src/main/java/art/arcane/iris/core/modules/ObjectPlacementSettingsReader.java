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

import art.arcane.iris.builtin.objects.ObjectPlacement;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Reads the object placements an Iris resource nests, such as a mod's placement injectors, the way {@code iris:objects}
 * reads its own: with the modules' Gson, keeping the JSON that declares the placement's module settings. Register it
 * before the factory that resolves snippets: Gson asks later registrations first, so that factory reads a snippet
 * reference and this one decodes the snippet's JSON.
 */
public final class ObjectPlacementSettingsReader implements TypeAdapterFactory {
    private final Supplier<ModuleSettingsDecoder> decoder;

    /** {@code decoder} is asked for on the first placement read, so reading no placement loads no modules. */
    public ObjectPlacementSettingsReader(Supplier<ModuleSettingsDecoder> decoder) {
        this.decoder = decoder;
    }

    @Override
    @SuppressWarnings("unchecked") // The type is checked to be ObjectPlacement.
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != ObjectPlacement.class) {
            return null;
        }
        TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
        return (TypeAdapter<T>) new TypeAdapter<ObjectPlacement>() {
            @Override
            public void write(JsonWriter out, ObjectPlacement placement) throws IOException {
                if (placement == null || placement.json() == null) {
                    out.nullValue();
                    return;
                }
                out.jsonValue(placement.json());
            }

            @Override
            public ObjectPlacement read(JsonReader in) throws IOException {
                JsonElement json = elements.read(in);
                if (json == null || json.isJsonNull()) {
                    return null;
                }
                return decoder.get().decodeResource(ObjectPlacement.class, json.toString());
            }
        };
    }
}
