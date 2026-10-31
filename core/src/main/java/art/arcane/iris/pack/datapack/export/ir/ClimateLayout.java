package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.List;
import java.util.Objects;

public record ClimateLayout(List<ClimateEntry> entries) {
    private static final double CLIMATE_LIMIT = 2.0;

    public ClimateLayout {
        entries = List.copyOf(entries);
    }

    public record ClimateEntry(String biomeId, ClimateAxis axis, double min, double max) {
        public ClimateEntry {
            VanillaResourceIdentifier.requireIdentifier(biomeId, "climate biome id");
            Objects.requireNonNull(axis);
            if (!(min >= -CLIMATE_LIMIT && min <= max && max <= CLIMATE_LIMIT)) {
                throw new IllegalArgumentException("Climate entry for '" + biomeId + "' must satisfy -2 <= min <= max <= 2, got [" + min + ", " + max + "]");
            }
        }
    }

    public enum ClimateAxis {
        TEMPERATURE,
        HUMIDITY,
        DEPTH
    }
}
