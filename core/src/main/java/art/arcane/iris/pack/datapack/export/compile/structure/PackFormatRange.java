package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.DataVersion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

record PackFormatRange(int minMajor, int minMinor, int maxMajor, int maxMinor) {
    private static final int ANY_MINOR = Integer.MAX_VALUE;

    static Optional<PackFormatRange> of(JsonObject section, String legacyKey) {
        if (section.has("min_format") && section.has("max_format")) {
            int[] minimum = format(section.get("min_format"), 0);
            int[] maximum = format(section.get("max_format"), ANY_MINOR);
            return minimum == null || maximum == null
                    ? Optional.empty()
                    : Optional.of(new PackFormatRange(minimum[0], minimum[1], maximum[0], maximum[1]));
        }
        if (section.has(legacyKey)) {
            return legacy(section.get(legacyKey));
        }
        JsonElement single = section.get("pack_format");
        return integer(single) ? Optional.of(major(single.getAsInt(), single.getAsInt())) : Optional.empty();
    }

    boolean contains(DataVersion target) {
        return compare(minMajor, minMinor, target.getPackFormat(), target.getPackFormatMinor()) <= 0
                && compare(target.getPackFormat(), target.getPackFormatMinor(), maxMajor, maxMinor) <= 0;
    }

    String describe() {
        return bound(minMajor, minMinor) + " to " + bound(maxMajor, maxMinor);
    }

    private static Optional<PackFormatRange> legacy(JsonElement value) {
        if (integer(value)) {
            return Optional.of(major(value.getAsInt(), value.getAsInt()));
        }
        if (value.isJsonArray() && value.getAsJsonArray().size() == 2
                && integer(value.getAsJsonArray().get(0)) && integer(value.getAsJsonArray().get(1))) {
            return Optional.of(major(value.getAsJsonArray().get(0).getAsInt(), value.getAsJsonArray().get(1).getAsInt()));
        }
        if (value.isJsonObject()) {
            JsonObject bounds = value.getAsJsonObject();
            JsonElement minimum = bounds.get("min_inclusive");
            JsonElement maximum = bounds.get("max_inclusive");
            if (integer(minimum) && integer(maximum)) {
                return Optional.of(major(minimum.getAsInt(), maximum.getAsInt()));
            }
        }
        return Optional.empty();
    }

    private static PackFormatRange major(int minimum, int maximum) {
        return new PackFormatRange(minimum, 0, maximum, ANY_MINOR);
    }

    private static int[] format(JsonElement value, int defaultMinor) {
        if (integer(value)) {
            return new int[]{value.getAsInt(), defaultMinor};
        }
        if (value != null && value.isJsonArray()) {
            JsonArray parts = value.getAsJsonArray();
            if (parts.size() == 2 && integer(parts.get(0)) && integer(parts.get(1))) {
                return new int[]{parts.get(0).getAsInt(), parts.get(1).getAsInt()};
            }
        }
        return null;
    }

    private static boolean integer(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static int compare(int leftMajor, int leftMinor, int rightMajor, int rightMinor) {
        int major = Integer.compare(leftMajor, rightMajor);
        return major != 0 ? major : Integer.compare(leftMinor, rightMinor);
    }

    private static String bound(int major, int minor) {
        return minor == ANY_MINOR ? major + ".*" : major + "." + minor;
    }
}
