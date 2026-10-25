package art.arcane.iris.generation.hydrology.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum HydrologyFeatureType {
    STREAM,
    RIVER,
    MAJOR_RIVER,
    CASCADE,
    WATERFALL,
    MOUTH,
    POND,
    LAKE,
    DAM,
    SEA_INLET,
    CAVE_POOL;

    private static final List<String> KEYS = keyList();

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> keys() {
        return KEYS;
    }

    public static HydrologyFeatureType parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (HydrologyFeatureType type : values()) {
            if (type.key().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown hydrology feature type '" + (value == null ? "" : value.trim()) + "'. Use one of: "
                + String.join(", ", KEYS) + ".");
    }

    private static List<String> keyList() {
        List<String> keys = new ArrayList<>();
        for (HydrologyFeatureType type : values()) {
            keys.add(type.key());
        }
        return List.copyOf(keys);
    }
}
