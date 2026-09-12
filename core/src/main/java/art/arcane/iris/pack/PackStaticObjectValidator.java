package art.arcane.iris.pack;

import art.arcane.iris.structure.object.IrisObjectPlacementScaleInterpolator;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class PackStaticObjectValidator {
    private PackStaticObjectValidator() {
    }

    static void validate(File packFolder, String dimensionKey, JSONObject dimension,
                         int minY, int maxY, List<String> errors) {
        if (!dimension.has("staticObjects")) {
            return;
        }
        String path = "Dimension '" + dimensionKey + "'.staticObjects";
        JSONArray entries = dimension.optJSONArray("staticObjects");
        if (entries == null) {
            errors.add(path + " must be an array.");
            return;
        }
        Path objectsRoot = packFolder.toPath().toAbsolutePath().normalize().resolve("objects");
        for (int index = 0; index < entries.length(); index++) {
            String entryPath = path + "[" + index + "]";
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) {
                errors.add(entryPath + " must be an object.");
                continue;
            }
            validateObjectKey(entryPath, entry, objectsRoot, errors);
            validatePosition(packFolder, entryPath, entry, minY, maxY, errors);
            validateRotation(entryPath, entry, errors);
            validateNumber(entryPath, entry, "scale", IrisStaticObject.MINIMUM_SCALE,
                    IrisStaticObject.MAXIMUM_SCALE, errors);
            validateInterpolation(entryPath, entry, errors);
            validateBoolean(entryPath, entry, "bore", errors);
            validateBoolean(entryPath, entry, "smartBore", errors);
            validateSeed(entryPath, entry, errors);
            validateEdits(packFolder, entryPath, entry, errors);
        }
    }

    private static void validateObjectKey(String path, JSONObject entry, Path objectsRoot,
                                          List<String> errors) {
        Object raw = entry.opt("object");
        if (!(raw instanceof String key) || key.isBlank()) {
            errors.add(path + ".object must be a nonblank object load key.");
        } else {
            Path objectFile = resolveContained(objectsRoot, key + ".iob");
            if (objectFile == null || !Files.isRegularFile(objectFile)) {
                errors.add(path + ".object references missing object '" + key + "'.");
            }
        }
    }

    private static void validatePosition(File packFolder, String path, JSONObject entry,
                                         int minY, int maxY, List<String> errors) {
        JSONObject position = resolveObject(packFolder, entry.opt("position"), "position-3d",
                path + ".position", errors);
        if (position == null) {
            return;
        }
        validateInteger(path + ".position", position, "x", -IrisStaticObject.MAXIMUM_HORIZONTAL_POSITION,
                IrisStaticObject.MAXIMUM_HORIZONTAL_POSITION, 0, errors);
        validateInteger(path + ".position", position, "y", minY, maxY - 1, 0, errors);
        validateInteger(path + ".position", position, "z", -IrisStaticObject.MAXIMUM_HORIZONTAL_POSITION,
                IrisStaticObject.MAXIMUM_HORIZONTAL_POSITION, 0, errors);
    }

    private static void validateRotation(String path, JSONObject entry, List<String> errors) {
        if (!entry.has("rotation")) {
            return;
        }
        JSONObject rotation = entry.optJSONObject("rotation");
        if (rotation == null) {
            errors.add(path + ".rotation must be an object.");
            return;
        }
        validateNumber(path + ".rotation", rotation, "x", -360, 360, errors);
        validateNumber(path + ".rotation", rotation, "y", -360, 360, errors);
        validateNumber(path + ".rotation", rotation, "z", -360, 360, errors);
    }

    private static void validateInterpolation(String path, JSONObject entry, List<String> errors) {
        if (!entry.has("scaleInterpolation")) {
            return;
        }
        Object raw = entry.opt("scaleInterpolation");
        if (raw instanceof String name) {
            for (IrisObjectPlacementScaleInterpolator value : IrisObjectPlacementScaleInterpolator.values()) {
                if (value.name().equals(name)) {
                    return;
                }
            }
        }
        errors.add(path + ".scaleInterpolation must be NONE, TRILINEAR, TRICUBIC or TRIHERMITE.");
    }

    private static void validateSeed(String path, JSONObject entry, List<String> errors) {
        if (!entry.has("seed")) {
            return;
        }
        if (entry.opt("seed") instanceof Number number) {
            try {
                new BigDecimal(number.toString()).longValueExact();
                return;
            } catch (ArithmeticException | NumberFormatException ignored) {
            }
        }
        errors.add(path + ".seed must be an integer between " + Long.MIN_VALUE + " and " + Long.MAX_VALUE + ".");
    }

    private static void validateEdits(File packFolder, String path, JSONObject entry, List<String> errors) {
        if (!entry.has("edit")) {
            return;
        }
        JSONArray edits = entry.optJSONArray("edit");
        if (edits == null) {
            errors.add(path + ".edit must be an array.");
            return;
        }
        for (int index = 0; index < edits.length(); index++) {
            String editPath = path + ".edit[" + index + "]";
            JSONObject edit = resolveObject(packFolder, edits.opt(index), "object-block-replacer", editPath, errors);
            if (edit == null) {
                continue;
            }
            validateBlockList(editPath + ".find", edit.optJSONArray("find"), errors);
            JSONObject replacement = resolveObject(packFolder, edit.opt("replace"), "palette",
                    editPath + ".replace", errors);
            if (replacement != null) {
                validateBlockList(editPath + ".replace.palette", replacement.optJSONArray("palette"), errors);
            }
            validateNumber(editPath, edit, "chance", 0, 1, errors);
            validateBoolean(editPath, edit, "exact", errors);
        }
    }

    private static void validateBlockList(String path, JSONArray blocks, List<String> errors) {
        if (blocks == null || blocks.length() == 0) {
            errors.add(path + " must be a nonempty array of block objects.");
            return;
        }
        for (int index = 0; index < blocks.length(); index++) {
            JSONObject block = blocks.optJSONObject(index);
            if (block == null || !(block.opt("block") instanceof String key) || key.isBlank()) {
                errors.add(path + "[" + index + "] must be a block object with a nonblank block key.");
            }
        }
    }

    private static JSONObject resolveObject(File packFolder, Object value, String snippetType,
                                             String path, List<String> errors) {
        if (value instanceof JSONObject object) {
            return object;
        }
        if (value instanceof String reference && reference.startsWith("snippet/")) {
            String base = "snippet/" + snippetType + "/";
            String key = reference.startsWith(base) ? reference : base + reference.substring("snippet/".length());
            Path root = packFolder.toPath().toAbsolutePath().normalize();
            Path snippet = resolveContained(root, key + ".json");
            if (snippet == null || !snippet.startsWith(root.resolve(base)) || !Files.isRegularFile(snippet)) {
                errors.add(path + " references missing " + snippetType + " snippet '" + reference + "'.");
                return null;
            }
            try {
                return new JSONObject(Files.readString(snippet, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                errors.add(path + " could not read snippet '" + reference + "': " + e.getMessage());
                return null;
            }
        }
        errors.add(path + " must be an object or a " + snippetType + " snippet reference.");
        return null;
    }

    private static Path resolveContained(Path root, String relative) {
        try {
            Path path = root.resolve(relative).normalize();
            return path.startsWith(root) ? path : null;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static void validateNumber(String path, JSONObject owner, String field,
                                       double minimum, double maximum, List<String> errors) {
        if (!owner.has(field)) {
            return;
        }
        if (!(owner.opt(field) instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() > maximum) {
            errors.add(path + "." + field + " must be finite and between " + minimum + " and " + maximum + ".");
        }
    }

    private static void validateInteger(String path, JSONObject owner, String field, int minimum,
                                        int maximum, int fallback, List<String> errors) {
        Object raw = owner.has(field) ? owner.opt(field) : fallback;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() > maximum) {
            errors.add(path + "." + field + " must be an integer between " + minimum + " and " + maximum + ".");
        }
    }

    private static void validateBoolean(String path, JSONObject owner, String field, List<String> errors) {
        if (owner.has(field) && !(owner.opt(field) instanceof Boolean)) {
            errors.add(path + "." + field + " must be a boolean.");
        }
    }
}
