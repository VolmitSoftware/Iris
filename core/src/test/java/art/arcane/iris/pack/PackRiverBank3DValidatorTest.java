package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackRiverBank3DValidatorTest {
    @Test
    public void acceptsVolumetricStylesAndFullLengthSeeds() {
        List<String> errors = validate("""
                {"enabled":true,"seed":-9223372036854775808,"amplitude":32,
                 "horizontalScale":4,"verticalScale":256,"maximumOverhang":16,
                 "densityStyle":{"style":"STRATA","fracture":{"style":"SIMPLEX","zoom":2}}}
                """);
        assertTrue(errors.toString(), errors.isEmpty());
        assertTrue(validate("{\"densityStyle\":\"river/bank-rock\"}").isEmpty());
    }

    @Test
    public void rejectsInvalidBoundsTypesAndStyles() {
        for (String fields : List.of("\"seed\":1.5", "\"seed\":9223372036854775808",
                "\"enabled\":\"true\"", "\"amplitude\":-1", "\"horizontalScale\":0",
                "\"verticalScale\":257", "\"maximumOverhang\":17", "\"maximumOverhang\":1.5",
                "\"densityStyle\":null", "\"densityStyle\":{\"style\":\"invalid\"}",
                "\"densityStyle\":{\"zoom\":0}")) {
            assertFalse(fields, validate("{" + fields + "}").isEmpty());
        }
    }

    private static List<String> validate(String json) {
        List<String> errors = new ArrayList<>();
        PackRiverBank3DValidator.validate("geometry.banks3D", new JSONObject(json), errors);
        return errors;
    }
}
