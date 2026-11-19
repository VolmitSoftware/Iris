package art.arcane.iris.pack.value;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public abstract class WeightedReferenceAdapter<E extends WeightedReference> extends TypeAdapter<E> {
    private static final String WEIGHT_FIELD = "weight";

    private final String keyField;

    protected WeightedReferenceAdapter(String keyField) {
        this.keyField = keyField;
    }

    protected abstract E create(String key, int weight, boolean writtenAsObject);

    @Override
    public void write(JsonWriter writer, E entry) throws IOException {
        if (entry == null) {
            writer.nullValue();
            return;
        }
        if (!entry.isWrittenAsObject()) {
            writer.value(entry.getReferenceKey());
            return;
        }
        writer.beginObject();
        writer.name(keyField).value(entry.getReferenceKey());
        writer.name(WEIGHT_FIELD).value(entry.getWeight());
        writer.endObject();
    }

    @Override
    public E read(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token == JsonToken.STRING) {
            return create(reader.nextString(), Weighted.MIN_WEIGHT, false);
        }
        if (token != JsonToken.BEGIN_OBJECT) {
            throw new JsonParseException("Expected a " + keyField + " key or an object with \"" + keyField
                    + "\" and \"" + WEIGHT_FIELD + "\" at " + reader.getPath());
        }
        return readObject(reader);
    }

    private E readObject(JsonReader reader) throws IOException {
        String key = null;
        int weight = Weighted.MIN_WEIGHT;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals(keyField)) {
                key = reader.nextString();
            } else if (name.equals(WEIGHT_FIELD)) {
                weight = reader.nextInt();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (key == null) {
            throw new JsonParseException("Missing required \"" + keyField + "\" at " + reader.getPath());
        }
        if (weight < Weighted.MIN_WEIGHT || weight > Weighted.MAX_WEIGHT) {
            throw new JsonParseException("Weight must be from " + Weighted.MIN_WEIGHT + " to " + Weighted.MAX_WEIGHT
                    + ": " + weight + " at " + reader.getPath());
        }
        return create(key, weight, true);
    }
}
