/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.stream.ProceduralStream;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

@Description("Represents a stream from the engine")
@JsonAdapter(IrisEngineStreamType.Adapter.class)
public enum IrisEngineStreamType {
    @Description("Represents the given slope at the x, z coordinates")
    SLOPE((f) -> f.getComplex().getSlopeStream()),

    @Description("Represents terrain height before river incision and river biome replacement.")
    NATURAL_HEIGHT((f) -> f.getComplex().getNaturalHeightStream()),

    @Description("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves.")
    HEIGHT((f) -> f.getComplex().getHeightStream()),

    @Description("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves. with Max(height, fluidHeight).")
    HEIGHT_OR_FLUID((f) -> f.getComplex().getHeightFluidStream()),

    @Description("Represents the overlay noise generators summed (dimension setting)")
    OVERLAY_NOISE((f) -> f.getComplex().getOverlayStream()),

    @Description("Represents the noise style of regions")
    REGION_STYLE((f) -> f.getComplex().getRegionStyleStream()),

    @Description("Represents the identity of regions. Each region has a unique number (very large numbers)")
    REGION_IDENTITY((f) -> f.getComplex().getRegionIdentityStream()),

    @Description("Represents the height of the water surface: the river, stream or pond surface where water stands, otherwise the dimension fluid height.")
    WATER_HEAD((f) -> f.getComplex().getWaterHeadStream()),

    @Description("Represents what hydrology made of the column, as a number: 0 dry, 1 channel, 2 bank, 3 shore, 4 pond, 5 pond shore, 6 dam, 7 river mouth, 8 cascade, 9 waterfall, 10 sea inlet.")
    WATER_ROLE((f) -> f.getComplex().getWaterRoleStream().convert((role) -> (double) role.code()));

    private final Function<Engine, ProceduralStream<Double>> getter;

    IrisEngineStreamType(Function<Engine, ProceduralStream<Double>> getter) {
        this.getter = getter;
    }

    public ProceduralStream<Double> get(Engine engine) {
        return getter.apply(engine);
    }

    private static IrisEngineStreamType parse(String name) {
        for (IrisEngineStreamType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new UnknownStreamException("Unknown engine stream '" + name + "'. Known engine streams: "
                + Arrays.toString(values()) + ".");
    }

    public static final class UnknownStreamException extends JsonParseException {
        private UnknownStreamException(String message) {
            super(message);
        }
    }

    public static final class Adapter extends TypeAdapter<IrisEngineStreamType> {
        @Override
        public void write(JsonWriter writer, IrisEngineStreamType type) throws IOException {
            if (type == null) {
                writer.nullValue();
                return;
            }
            writer.value(type.name());
        }

        @Override
        public IrisEngineStreamType read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            return parse(reader.nextString());
        }
    }
}
