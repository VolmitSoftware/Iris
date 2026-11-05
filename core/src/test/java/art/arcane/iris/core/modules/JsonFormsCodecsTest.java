package art.arcane.iris.core.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.volmit.config.JsonForms;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonFormsCodecsTest {
    private static final Gson GSON = DecodingContract.appliedTo(new GsonBuilder())
            .registerTypeAdapterFactory(new JsonFormsCodecs())
            .create();

    @Test
    public void eachFormIsReadThroughTheCodecAsPlainValues() {
        Settings settings = GSON.fromJson("{\"height\": 4, \"steps\": [[1, 3], 2]}", Settings.class);

        assertEquals(new Height(4, 4), settings.height);
        assertEquals(List.of(new Height(1, 3), new Height(2, 2)), settings.steps);
    }

    @Test
    public void aMemberWrittenAsNullKeepsItsDefault() {
        Settings settings = GSON.fromJson("{\"height\": null}", Settings.class);

        assertEquals(new Height(1, 1), settings.height);
    }

    @Test
    public void aValueIsWrittenBackInTheFormTheCodecGives() {
        assertEquals("{\"height\":[1.0,3.0],\"steps\":[]}", GSON.toJson(GSON.fromJson("{\"height\": [1, 3]}", Settings.class)));
    }

    @Test
    public void aFormTheCodecDoesNotKnowFailsNamingTheClass() {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"height\": \"tall\"}", Settings.class));

        assertTrue(failure.getMessage(), failure.getMessage().startsWith("Height: "));
    }

    private static final class Settings {
        private Height height = new Height(1, 1);
        private List<Height> steps = List.of();
    }

    @JsonForms(Height.Forms.class)
    private record Height(double min, double max) {
        static final class Forms implements JsonForms.Codec<Height> {
            @Override
            public Height read(Object json) {
                return switch (json) {
                    case Double blocks -> new Height(blocks, blocks);
                    case List<?> range -> new Height((Double) range.get(0), (Double) range.get(1));
                    default -> throw new IllegalArgumentException("a height is a number or [min, max], not " + json);
                };
            }

            @Override
            public Object write(Height value) {
                return value.min() == value.max() ? value.min() : List.of(value.min(), value.max());
            }
        }
    }
}
