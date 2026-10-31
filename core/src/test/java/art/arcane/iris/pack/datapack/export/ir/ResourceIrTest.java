package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ResourceIrTest {
    @Test
    public void dryAquiferUsesVanillaAquiferNoises() {
        Field surface = new Field.Const(63.0);
        AquiferDef aquifer = AquiferDef.dry(surface);
        assertEquals(new Field.Noise("minecraft:aquifer_barrier", 1.0, 0.5, null, null, null), aquifer.barrier());
        assertEquals(new Field.Const(-1.0), aquifer.floodedness());
        assertEquals(new Field.Noise("minecraft:aquifer_fluid_level_spread", 1.0, 0.7142857142857143, null, null, null), aquifer.spread());
        assertEquals(new Field.Noise("minecraft:aquifer_lava", 1.0, 1.0, null, null, null), aquifer.lava());
        assertEquals(new Field.Const(-1.0), aquifer.exclusion());
        assertSame(surface, aquifer.surfaceLevel());
    }

    @Test
    public void noiseDefValidatesVersionNeutralLimits() {
        NoiseDef noise = new NoiseDef("terrain/base", -7, List.of(1.0, 0.5, 0.0));
        assertEquals(List.of(1.0, 0.5, 0.0), noise.amplitudes());
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("terrain/base", -33, List.of(1.0)));
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("terrain/base", 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("terrain/base", 0, List.of(-1.0)));
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("terrain/base", 0, List.of(0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("Terrain", 0, List.of(1.0)));
        assertThrows(IllegalArgumentException.class, () -> new NoiseDef("iris:terrain", 0, List.of(1.0)));
    }

    @Test
    public void climateEntryStaysInsideClimateRange() {
        ClimateLayout.ClimateEntry entry = new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.TEMPERATURE, 0.1, 0.2);
        assertEquals(List.of(entry), new ClimateLayout(List.of(entry)).entries());
        assertThrows(IllegalArgumentException.class, () -> new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.DEPTH, -2.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.HUMIDITY, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new ClimateLayout.ClimateEntry("plains", ClimateLayout.ClimateAxis.HUMIDITY, 0.0, 0.1));
    }

    @Test
    public void rockEmitsSingleBlockRule() {
        MaterialProgram program = MaterialProgram.rock("minecraft:stone");
        assertEquals(JsonParser.parseString("{\"type\":\"minecraft:block\",\"result_state\":{\"id\":\"minecraft:stone\"}}"), program.rule());
        assertThrows(IllegalArgumentException.class, () -> MaterialProgram.rock("stone"));
    }

    @Test
    public void biomeJsonIsCopied() {
        JsonObject json = new JsonObject();
        json.addProperty("temperature", 0.8);
        BiomeDef biome = new BiomeDef("temperate/plains", json);
        json.addProperty("downfall", 0.4);
        assertEquals(1, biome.json().size());
        assertThrows(IllegalArgumentException.class, () -> new BiomeDef("Temperate Plains", json));
    }

    @Test
    public void exportIrCopiesLists() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        ExportIr ir = exportIr(DataVersion.V26_3, List.of(biome("plains")), List.of(entry("iris_export:plains")), diagnostics);
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.IMAGE_MAP, "x", "y"));
        assertEquals(0, ir.diagnostics().size());
        assertEquals("iris_export", ir.namespace());
    }

    @Test
    public void exportIrAcceptsVanillaBiomeReferences() {
        ExportIr ir = exportIr(DataVersion.V26_3, List.of(biome("plains")), List.of(entry("iris_export:plains"), entry("minecraft:desert")), List.of());
        assertEquals(2, ir.climate().entries().size());
    }

    @Test
    public void exportIrRejectsOlderTargets() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> exportIr(DataVersion.V26_2, List.of(biome("plains")), List.of(entry("iris_export:plains")), List.of()));
        assertEquals("Unsupported datapack export version '26.2'. Supported: 26.3", error.getMessage());
    }

    @Test
    public void exportIrRejectsClimateEntryForUndefinedBiome() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> exportIr(DataVersion.V26_3, List.of(biome("plains")), List.of(entry("iris_export:desert")), List.of()));
        assertEquals("Climate entry references undefined biome 'iris_export:desert'", error.getMessage());
    }

    @Test
    public void exportIrRejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> exportIr(DataVersion.V26_3, List.of(biome("plains"), biome("plains")), List.of(entry("iris_export:plains")), List.of()));
    }

    @Test
    public void exportIrRejectsDuplicateResourcePaths() {
        String path = "data/iris_export/worldgen/configured_feature/tree.json";
        JsonResource json = new JsonResource(path, JsonParser.parseString("{\"type\":\"minecraft:tree\"}"));
        BinaryResource binary = new BinaryResource(path, new byte[]{1, 2});
        List<BiomeDef> biomes = List.of(biome("plains"));
        List<ClimateLayout.ClimateEntry> entries = List.of(entry("iris_export:plains"));

        IllegalArgumentException jsonError = assertThrows(IllegalArgumentException.class,
                () -> exportIr(DataVersion.V26_3, biomes, entries, List.of(), List.of(json, json), List.of()));
        IllegalArgumentException mixedError = assertThrows(IllegalArgumentException.class,
                () -> exportIr(DataVersion.V26_3, biomes, entries, List.of(), List.of(json), List.of(binary)));

        assertEquals("Duplicate resource path '" + path + "'", jsonError.getMessage());
        assertEquals("Duplicate resource path '" + path + "'", mixedError.getMessage());
        ExportIr ir = exportIr(DataVersion.V26_3, biomes, entries, List.of(), List.of(json), List.of(new BinaryResource("data/iris_export/structure/tree.nbt", new byte[]{1})));
        assertEquals(List.of(json), ir.jsonResources());
        assertEquals(1, ir.binaryResources().size());
    }

    @Test
    public void resourcesCopyTheirContents() {
        byte[] bytes = {1, 2, 3};
        BinaryResource binary = new BinaryResource("data/iris_export/structure/tree.nbt", bytes);
        bytes[0] = 9;
        binary.bytes()[1] = 9;
        JsonObject json = new JsonObject();
        JsonResource resource = new JsonResource("data/iris_export/worldgen/placed_feature/tree.json", json);
        json.addProperty("feature", "iris_export:tree");

        assertArrayEquals(new byte[]{1, 2, 3}, binary.bytes());
        assertEquals(new BinaryResource("data/iris_export/structure/tree.nbt", new byte[]{1, 2, 3}), binary);
        assertEquals(0, resource.json().getAsJsonObject().size());
        assertThrows(IllegalArgumentException.class, () -> new JsonResource("/data/iris_export/x.json", json));
        assertThrows(IllegalArgumentException.class, () -> new BinaryResource("data/../x.nbt", bytes));
    }

    @Test
    public void exportIrRejectsUnsupportedTarget() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> exportIr(DataVersion.V1_21_1, List.of(biome("plains")), List.of(entry("iris_export:plains")), List.of()));
        assertEquals("Unsupported datapack export version '1.21.1'. Supported: 26.3", error.getMessage());
    }

    private static ExportIr exportIr(DataVersion target, List<BiomeDef> biomes, List<ClimateLayout.ClimateEntry> entries, List<ExportDiagnostic> diagnostics) {
        return exportIr(target, biomes, entries, diagnostics, List.of(), List.of());
    }

    private static ExportIr exportIr(
            DataVersion target,
            List<BiomeDef> biomes,
            List<ClimateLayout.ClimateEntry> entries,
            List<ExportDiagnostic> diagnostics,
            List<JsonResource> jsonResources,
            List<BinaryResource> binaryResources
    ) {
        DimensionDef dimension = new DimensionDef("overworld", -64, 384, 384, 63, "minecraft:stone", "minecraft:water", new JsonObject());
        Field height = new Field.Const(64.0);
        Field zero = new Field.Const(0.0);
        RouterDef router = new RouterDef(zero, zero, zero, zero, zero, zero, height, height, AquiferDef.dry(height));
        List<NoiseDef> noises = List.of(new NoiseDef("terrain/base", -7, List.of(1.0)));
        return new ExportIr("iris_export", target, WorldgenExportMode.PRESET, dimension, noises, router, new ClimateLayout(entries), biomes, MaterialProgram.rock("minecraft:stone"), diagnostics, jsonResources, binaryResources);
    }

    private static BiomeDef biome(String id) {
        JsonElement json = JsonParser.parseString("{\"has_precipitation\":true}");
        return new BiomeDef(id, json.getAsJsonObject());
    }

    private static ClimateLayout.ClimateEntry entry(String biomeId) {
        return new ClimateLayout.ClimateEntry(biomeId, ClimateLayout.ClimateAxis.TEMPERATURE, 0.0, 0.1);
    }
}
