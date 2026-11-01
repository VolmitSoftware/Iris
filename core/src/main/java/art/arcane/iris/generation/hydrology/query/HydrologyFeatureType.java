package art.arcane.iris.generation.hydrology.query;

import art.arcane.iris.generation.hydrology.PersistedCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum HydrologyFeatureType {
    STREAM(0),
    RIVER(1),
    MAJOR_RIVER(2),
    CASCADE(3),
    WATERFALL(4),
    MOUTH(5),
    POND(6),
    LAKE(7),
    DAM(8),
    SEA_INLET(9),
    CAVE_POOL(10);

    private static final List<String> KEYS = keyList();
    private static final PersistedCodes<HydrologyFeatureType> CODES =
            PersistedCodes.of(HydrologyFeatureType.class, HydrologyFeatureType::code);

    private final int code;

    HydrologyFeatureType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static HydrologyFeatureType fromCode(int code) {
        return CODES.decode(code);
    }

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
