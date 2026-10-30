package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public record BiomeCodes(Field surfaceCode, Field caveCode, Field class3d, ClimateLayout layout, Map<String, String> loadKeyByBiomeId) {
    public static final String BASE_CODE = "biome/base_code";

    public BiomeCodes {
        Objects.requireNonNull(surfaceCode);
        Objects.requireNonNull(caveCode);
        Objects.requireNonNull(class3d);
        Objects.requireNonNull(layout);
        FieldValidator.requireTwoDimensional(surfaceCode, "surface code");
        FieldValidator.requireTwoDimensional(caveCode, "cave code");
        SortedMap<String, String> loadKeys = new TreeMap<>(loadKeyByBiomeId);
        for (Map.Entry<String, String> entry : loadKeys.entrySet()) {
            VanillaResourceIdentifier.requireIdentifier(entry.getKey(), "biome id");
            Objects.requireNonNull(entry.getValue());
        }
        loadKeyByBiomeId = Collections.unmodifiableSortedMap(loadKeys);
    }
}
