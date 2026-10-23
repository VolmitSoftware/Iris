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

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.volmit.config.Level;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Decodes the module settings of every object placement Gson reads, wherever pack JSON nests one, into
 * {@link IrisObjectPlacement#getModuleSettings()}. Register it before the factory that resolves snippets:
 * Gson asks later registrations first, so that factory reads a snippet reference and this one decodes the
 * snippet's JSON.
 */
public final class ObjectPlacementSettingsReader implements TypeAdapterFactory {
    private final Supplier<ModuleSettingsDecoder> decoder;

    /** {@code decoder} is asked for on the first placement read, so reading no placement loads no modules. */
    public ObjectPlacementSettingsReader(Supplier<ModuleSettingsDecoder> decoder) {
        this.decoder = decoder;
    }

    @Override
    @SuppressWarnings("unchecked") // The type is checked to be IrisObjectPlacement.
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != IrisObjectPlacement.class) {
            return null;
        }
        TypeAdapter<IrisObjectPlacement> placements = gson.getDelegateAdapter(this, (TypeToken<IrisObjectPlacement>) type);
        TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
        return (TypeAdapter<T>) new TypeAdapter<IrisObjectPlacement>() {
            @Override
            public void write(JsonWriter out, IrisObjectPlacement placement) throws IOException {
                placements.write(out, placement);
            }

            @Override
            public IrisObjectPlacement read(JsonReader in) throws IOException {
                String path = in.getPath();
                JsonElement json = elements.read(in);
                IrisObjectPlacement placement = placements.fromJsonTree(json);
                if (placement != null && json.isJsonObject()) {
                    placement.setModuleSettings(decoder.get().decode(Level.PLACEMENT,
                            new JSONObject(json.toString()), "object placement at " + path));
                }
                return placement;
            }
        };
    }
}
