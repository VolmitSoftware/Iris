package art.arcane.iris.pack.datapack.export;

import java.util.Locale;

public enum WorldgenExportMode {
    PRESET,
    DIMENSION,
    OVERWORLD;

    public static WorldgenExportMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OVERWORLD;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "preset" -> PRESET;
            case "dimension" -> DIMENSION;
            case "overworld" -> OVERWORLD;
            default -> throw new IllegalArgumentException("Unknown export mode '" + raw + "'. Expected: preset, dimension, overworld");
        };
    }
}
