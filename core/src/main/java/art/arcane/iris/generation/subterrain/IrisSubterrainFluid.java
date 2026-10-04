package art.arcane.iris.generation.subterrain;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(IrisSubterrainFluid.Adapter.class)
public enum IrisSubterrainFluid {
    WATER,
    LAVA;

    public static final class Adapter extends TypeAdapter<IrisSubterrainFluid> {
        @Override
        public void write(JsonWriter writer, IrisSubterrainFluid value) throws IOException {
            writer.value(value.name());
        }

        @Override
        public IrisSubterrainFluid read(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.STRING) {
                throw new JsonParseException("Subterrain fluid must be WATER or LAVA");
            }
            String value = reader.nextString();
            try {
                return IrisSubterrainFluid.valueOf(value);
            } catch (IllegalArgumentException error) {
                throw new JsonParseException("Unknown subterrain fluid: " + value + "; expected WATER or LAVA", error);
            }
        }
    }
}
