package art.arcane.iris.pack;

import art.arcane.iris.generation.terrain.IrisTerrain3D;
import art.arcane.iris.generation.noise.NoiseStyle;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PackTerrain3DValidator {
    private static final Gson GSON = new Gson();
    private static final Set<String> PROFILE_FIELDS = Set.of("$schema", "enabled", "seed", "amplitude",
            "horizontalScale", "verticalScale", "densityStyle", "crackDepth", "crackWidth", "crackScale",
            "crackStyle", "minimumSlope", "slopeFade", "fluidClearance", "fluidFade");
    private static final Set<String> STYLE_FIELDS = Set.of("$schema", "style", "cellularFrequency",
            "cellularZoom", "zoom", "expression", "imageMap", "multiplier", "fracture", "exponent", "cacheSize");

    private PackTerrain3DValidator() {
    }

    static List<String> validate(File packFolder) {
        List<String> errors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return errors;
        }
        File biomeFolder = new File(packFolder, "biomes");
        for (File biomeFile : files(biomeFolder)) {
            JsonObject biome = readObject(biomeFile, "Biome '" + PackValidationIo.deriveKey(biomeFolder, biomeFile)
                    + "'", errors);
            if (biome != null && biome.has("terrain3D") && !biome.get("terrain3D").isJsonNull()) {
                validateProfile(packFolder, biome.get("terrain3D"),
                        "Biome '" + PackValidationIo.deriveKey(biomeFolder, biomeFile) + "' terrain3D", errors);
            }
        }
        File snippetFolder = new File(packFolder, "snippet/terrain-3d");
        for (File snippetFile : files(snippetFolder)) {
            String location = "Terrain3D snippet '" + PackValidationIo.deriveKey(snippetFolder, snippetFile) + "'";
            JsonObject profile = readObject(snippetFile, location, errors);
            if (profile != null) {
                validateProfile(packFolder, profile, location, errors);
            }
        }
        return errors;
    }

    private static List<File> files(File folder) {
        if (!folder.isDirectory()) {
            return List.of();
        }
        List<File> files = PackValidationIo.listJsonRecursive(folder);
        files.sort(Comparator.comparing(File::getPath));
        return files;
    }

    private static void validateProfile(File packFolder, JsonElement raw, String location, List<String> errors) {
        JsonObject object = resolveObject(packFolder, raw, "terrain-3d", location, errors);
        if (object == null) {
            return;
        }
        JsonObject normalized = object.deepCopy();
        int initialErrors = errors.size();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String field = entry.getKey();
            JsonElement value = entry.getValue();
            String path = location + "." + field;
            if (!PROFILE_FIELDS.contains(field)) {
                errors.add(path + " is not a terrain3D field.");
                continue;
            }
            switch (field) {
                case "$schema" -> requireString(value, path, false, errors);
                case "enabled" -> {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        errors.add(path + " must be a JSON boolean.");
                    }
                }
                case "seed" -> requireInteger(value, path, errors);
                case "densityStyle", "crackStyle" -> {
                    JsonObject style = validateStyle(packFolder, value, path, 0, errors);
                    if (style != null) {
                        normalized.add(field, style);
                    }
                }
                default -> requireNumber(value, path, errors);
            }
        }
        if (initialErrors != errors.size()) {
            return;
        }
        try {
            GSON.fromJson(normalized, IrisTerrain3D.class).validate();
        } catch (JsonParseException | IllegalArgumentException invalid) {
            errors.add(location + ": " + invalid.getMessage());
        }
    }

    private static JsonObject validateStyle(File packFolder, JsonElement raw, String location,
                                            int depth, List<String> errors) {
        if (depth > 32) {
            errors.add(location + " exceeds the maximum style nesting depth of 32.");
            return null;
        }
        JsonObject object = resolveObject(packFolder, raw, "style", location, errors);
        if (object == null) {
            return null;
        }
        JsonObject normalized = object.deepCopy();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String field = entry.getKey();
            JsonElement value = entry.getValue();
            String path = location + "." + field;
            if (!STYLE_FIELDS.contains(field)) {
                errors.add(path + " is not a generator style field.");
                continue;
            }
            switch (field) {
                case "$schema" -> requireString(value, path, false, errors);
                case "expression", "imageMap" -> requireString(value, path, true, errors);
                case "style" -> {
                    if (requireString(value, path, false, errors)) {
                        try {
                            NoiseStyle.valueOf(value.getAsString());
                        } catch (IllegalArgumentException invalid) {
                            errors.add(path + " is not a known noise style.");
                        }
                    }
                }
                case "fracture" -> {
                    if (!value.isJsonNull()) {
                        JsonObject fracture = validateStyle(packFolder, value, path, depth + 1, errors);
                        if (fracture != null) {
                            normalized.add(field, fracture);
                        }
                    }
                }
                case "cacheSize" -> {
                    if (requireInteger(value, path, errors)) {
                        requireRange(value, path, 0D, 8192D, errors);
                    }
                }
                case "exponent" -> requireRange(value, path, 0.01562D, 64D, errors);
                case "cellularFrequency" -> requireRange(value, path, 0D, Double.MAX_VALUE, errors);
                default -> requireRange(value, path, 0.00001D, Double.MAX_VALUE, errors);
            }
        }
        return normalized;
    }

    private static JsonObject resolveObject(File packFolder, JsonElement raw, String snippetType,
                                            String location, List<String> errors) {
        if (raw.isJsonObject()) {
            return raw.getAsJsonObject();
        }
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()
                || !raw.getAsString().startsWith("snippet/")) {
            errors.add(location + " must be an object or snippet reference.");
            return null;
        }
        String reference = raw.getAsString();
        String prefix = "snippet/" + snippetType + "/";
        String resolved = reference.startsWith(prefix) ? reference : prefix + reference.substring(8);
        Path root = packFolder.toPath().toAbsolutePath().normalize();
        Path path = root.resolve(resolved + ".json").normalize();
        if (!path.startsWith(root.resolve(prefix)) || !Files.isRegularFile(path)) {
            errors.add(location + " references an unavailable " + snippetType + " snippet '" + reference + "'.");
            return null;
        }
        return readObject(path.toFile(), location + " (" + reference + ")", errors);
    }

    private static JsonObject readObject(File file, String location, List<String> errors) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            errors.add(location + " must contain a JSON object.");
        } catch (IOException | JsonParseException invalid) {
            errors.add(location + " contains unreadable JSON: " + invalid.getMessage());
        }
        return null;
    }

    private static boolean requireString(JsonElement value, String path, boolean nullable, List<String> errors) {
        if (nullable && value.isJsonNull()) {
            return true;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            errors.add(path + " must be a JSON string" + (nullable ? " or null." : "."));
            return false;
        }
        return true;
    }

    private static boolean requireNumber(JsonElement value, String path, List<String> errors) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())) {
            errors.add(path + " must be a finite JSON number.");
            return false;
        }
        return true;
    }

    private static boolean requireInteger(JsonElement value, String path, List<String> errors) {
        if (!requireNumber(value, path, errors)) {
            return false;
        }
        try {
            value.getAsBigDecimal().longValueExact();
            return true;
        } catch (ArithmeticException invalid) {
            errors.add(path + " must be an integer in the signed 64-bit range.");
            return false;
        }
    }

    private static void requireRange(JsonElement value, String path, double minimum,
                                     double maximum, List<String> errors) {
        if (requireNumber(value, path, errors)
                && (value.getAsDouble() < minimum || value.getAsDouble() > maximum)) {
            errors.add(path + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
