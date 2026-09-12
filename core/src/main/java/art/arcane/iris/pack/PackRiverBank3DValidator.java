package art.arcane.iris.pack;

import art.arcane.iris.generation.hydrology.IrisRiverBank3DConfig;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.math.BigDecimal;
import java.util.List;

final class PackRiverBank3DValidator {
    private static final Gson GSON = new Gson();

    private PackRiverBank3DValidator() {
    }

    static void validate(String path, JSONObject banks, List<String> errors) {
        int previousErrors = errors.size();
        PackJsonFieldChecks.validateOptionalBoolean(path, banks, "enabled", errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "amplitude", 0D, 32D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "horizontalScale", 4D, 4096D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "verticalScale", 4D, 256D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, banks, "maximumOverhang", 1, 16, errors);
        if (banks.has("seed")) {
            Object seed = banks.opt("seed");
            try {
                if (!(seed instanceof Number)) {
                    throw new NumberFormatException();
                }
                new BigDecimal(seed.toString()).longValueExact();
            } catch (NumberFormatException | ArithmeticException invalid) {
                errors.add(path + ".seed must be a 64-bit integer.");
            }
        }
        JSONObject normalized = new JSONObject(banks.toString());
        if (banks.has("densityStyle")) {
            Object style = banks.opt("densityStyle");
            if (style instanceof String reference && !reference.isBlank()) {
                normalized.remove("densityStyle");
            } else if (!(style instanceof JSONObject)) {
                errors.add(path + ".densityStyle must be an object or snippet reference.");
            }
        }
        if (errors.size() != previousErrors) {
            return;
        }
        try {
            GSON.fromJson(normalized.toString(), IrisRiverBank3DConfig.class).validate();
        } catch (JsonParseException | IllegalArgumentException invalid) {
            errors.add(path + ": " + invalid.getMessage());
        }
    }
}
