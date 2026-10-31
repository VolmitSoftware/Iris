package art.arcane.iris.probe;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public final class DensityJsonBreakdownTest {
    private static final String TEMPERATURE = "{\"type\":\"add\",\"left\":\"x:biome/selector/0\",\"right\":1.0}";
    private static final String SELECTOR = "{\"type\":\"mul\",\"left\":\"x:cell/cellular/a/value\",\"right\":2.0}";
    private static final String CELL_A = "{\"type\":\"noise\",\"noise\":\"x:hash_a\",\"xz_scale\":0.5,\"y_scale\":0.0}";
    private static final String CELL_B = "{\"type\":\"noise\",\"noise\":\"x:hash_b\",\"xz_scale\":0.25,\"y_scale\":0.0}";
    private static final String FINAL_DENSITY = "{\"type\":\"min\",\"left\":\"x:cell/cellular/b/value\",\"right\":0.0}";
    private static final String NOISE = "{\"firstOctave\":-1,\"amplitudes\":[1.0]}";

    @Test
    public void categoriesFollowProducingCompilers() {
        assertEquals("router", DensityJsonBreakdown.category("overworld", "x:overworld/final_density"));
        assertEquals("cellular", DensityJsonBreakdown.category("overworld", "x:cell/cracks/abc/distance"));
        assertEquals("biome selector", DensityJsonBreakdown.category("overworld", "x:biome/selector/12"));
        assertEquals("biome decision", DensityJsonBreakdown.category("overworld", "x:biome/base_code"));
        assertEquals("terrain height", DensityJsonBreakdown.category("overworld", "x:height"));
        assertEquals("terrain bands", DensityJsonBreakdown.category("overworld", "x:terrain/plains/blend/3"));
        assertEquals("noise style", DensityJsonBreakdown.category("overworld", "x:style/shared/ab12"));
        assertEquals("cellular offset copies", DensityJsonBreakdown.category("overworld", "x:cell/cellular/abc/vx_0_0/offset_x4_z0"));
        assertEquals("biome selector offset copies", DensityJsonBreakdown.category("overworld", "x:biome/selector/4/offset_x0_z4"));
    }

    @Test
    public void reportsBytesShapesAndCellularOwners() {
        SortedMap<String, byte[]> files = new TreeMap<>();
        put(files, "density_function/overworld/temperature", TEMPERATURE);
        put(files, "density_function/biome/selector/0", SELECTOR);
        put(files, "density_function/cell/cellular/a/value", CELL_A);
        put(files, "density_function/cell/cellular/b/value", CELL_B);
        put(files, "density_function/overworld/final_density", FINAL_DENSITY);
        put(files, "noise/hash_a", NOISE);
        put(files, "noise/hash_b", NOISE);

        DensityJsonBreakdown.Report report = DensityJsonBreakdown.analyze(files, "overworld").report();

        assertEquals(5, report.files());
        assertEquals(bytes(TEMPERATURE, SELECTOR, CELL_A, CELL_B, FINAL_DENSITY), report.bytes());
        DensityJsonBreakdown.CategoryBytes cellular = category(report, "cellular");
        assertEquals(2, cellular.files());
        assertEquals(1, cellular.distinctShapes());
        assertEquals(bytes(CELL_B), cellular.structurallyDuplicateBytes());
        assertEquals(2, category(report, "router").files());
        assertEquals(2, report.cellularInstances());
        assertEquals(1, report.cellularShapes().size());
        assertEquals("biome selector+router", report.cellularShapes().getFirst().owners());
        assertEquals(bytes(CELL_A), owner(report, "cellular via biome selector"));
        assertEquals(bytes(CELL_B), owner(report, "cellular via router"));
    }

    private static DensityJsonBreakdown.CategoryBytes category(DensityJsonBreakdown.Report report, String name) {
        for (DensityJsonBreakdown.CategoryBytes category : report.categories()) {
            if (category.category().equals(name)) {
                return category;
            }
        }
        throw new AssertionError("No category " + name + " in " + report.categories());
    }

    private static long owner(DensityJsonBreakdown.Report report, String name) {
        for (DensityJsonBreakdown.Bytes owner : report.libraryOwners()) {
            if (owner.name().equals(name)) {
                return owner.bytes();
            }
        }
        throw new AssertionError("No owner " + name + " in " + report.libraryOwners());
    }

    private static long bytes(String... bodies) {
        long total = 0;
        for (String body : bodies) {
            total += body.getBytes(StandardCharsets.UTF_8).length;
        }
        return total;
    }

    private static void put(SortedMap<String, byte[]> files, String path, String body) {
        files.put("data/x/worldgen/" + path + ".json", body.getBytes(StandardCharsets.UTF_8));
    }
}
