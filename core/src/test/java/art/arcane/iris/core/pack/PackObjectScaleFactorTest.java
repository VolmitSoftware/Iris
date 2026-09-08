package art.arcane.iris.core.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PackObjectScaleFactorTest {
    @Test
    public void acceptsOmissionAndFiniteFactorsWithinResamplerLimits() {
        for (String json : List.of("{}", "{\"allObjectScaleFactor\":0.01}",
                "{\"allObjectScaleFactor\":0.5}", "{\"allObjectScaleFactor\":1}",
                "{\"allObjectScaleFactor\":2}", "{\"allObjectScaleFactor\":50}")) {
            List<String> errors = new ArrayList<>();
            PackDimensionValidator.validateObjectScaleFactor("main", new JSONObject(json), errors);
            assertEquals(json, List.of(), errors);
        }
    }

    @Test
    public void blocksInvalidTypesAndFactorsInPackAndRuntimeValidation() {
        for (String value : List.of("null", "true", "\"2\"", "[]", "{}", "0", "-1", "0.009", "50.001")) {
            JSONObject json = new JSONObject("{\"allObjectScaleFactor\":" + value + "}");
            List<String> errors = new ArrayList<>();
            PackDimensionValidator.validateObjectScaleFactor("main", json, errors);
            assertEquals(value, 1, errors.size());
            assertThrows(IllegalArgumentException.class,
                    () -> PackValidator.requireValidObjectSettings(null, "main", json));
        }
    }
}
