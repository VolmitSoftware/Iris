package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BiomeCodesTest {
    private static final Field FLAT = new Field.Noise("iris_export:code", 1.0, 0.0, null, null, null);
    private static final Field VOLUME = new Field.Noise("iris_export:class", 1.0, 1.0, null, null, null);
    private static final ClimateLayout LAYOUT = new ClimateLayout(List.of(
            new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0)));

    @Test
    public void codeRootsAreTwoDimensional() {
        IllegalStateException surface = assertThrows(IllegalStateException.class,
                () -> new BiomeCodes(VOLUME, FLAT, VOLUME, LAYOUT, Map.of()));
        assertEquals("2D field 'surface code' depends on Y", surface.getMessage());
        IllegalStateException cave = assertThrows(IllegalStateException.class,
                () -> new BiomeCodes(FLAT, VOLUME, VOLUME, LAYOUT, Map.of()));
        assertEquals("2D field 'cave code' depends on Y", cave.getMessage());
    }

    @Test
    public void loadKeysAreCopiedInIdOrderAndMustUseFullIds() {
        Map<String, String> loadKeys = new HashMap<>();
        loadKeys.put("iris_export:zebra", "Zebra");
        loadKeys.put("iris_export:alpha", "Alpha");
        BiomeCodes codes = new BiomeCodes(FLAT, FLAT, VOLUME, LAYOUT, loadKeys);
        loadKeys.clear();

        assertEquals(List.of("iris_export:alpha", "iris_export:zebra"), List.copyOf(codes.loadKeyByBiomeId().keySet()));
        assertThrows(IllegalArgumentException.class, () -> new BiomeCodes(FLAT, FLAT, VOLUME, LAYOUT, Map.of("plains", "Plains")));
    }
}
