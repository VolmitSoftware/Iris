package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorWorldBoundaryTest {
    @Test
    public void acceptsAbsentAndValidBoundaries() {
        List<String> absentErrors = validate("{}");
        List<String> configuredErrors = validate("{\"worldBoundary\":{\"center\":{\"x\":12.5,\"z\":-8},"
                + "\"size\":16384,\"warningDistance\":16,\"damageBuffer\":5,\"damageAmount\":0.2}}");

        assertTrue(absentErrors.toString(), absentErrors.isEmpty());
        assertTrue(configuredErrors.toString(), configuredErrors.isEmpty());
    }

    @Test
    public void rejectsMalformedBoundaryObjects() {
        assertEquals(List.of("Dimension 'main' worldBoundary must be an object."),
                validate("{\"worldBoundary\":null}"));
        assertEquals(List.of("Dimension 'main' worldBoundary must be an object."),
                validate("{\"worldBoundary\":42}"));
        assertEquals(List.of("Dimension 'main' worldBoundary.center must be an object."),
                validate("{\"worldBoundary\":{\"center\":null}}"));
    }

    @Test
    public void rejectsValuesOutsideNativeLimits() {
        List<String> errors = validate("{\"worldBoundary\":{\"center\":{\"x\":29999985,\"z\":-29999985},"
                + "\"size\":59999969,\"warningDistance\":1.5,\"damageBuffer\":-1,\"damageAmount\":-0.1}}");

        assertEquals(6, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.size")));
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.warningDistance")));
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.damageBuffer")));
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.damageAmount")));
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.center.x")));
        assertTrue(errors.toString(), errors.stream().anyMatch((String error) -> error.contains("worldBoundary.center.z")));
    }

    private static List<String> validate(String json) {
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateWorldBoundary("main", new JSONObject(json), errors);
        return errors;
    }
}
