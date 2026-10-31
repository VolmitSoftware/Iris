package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportIrFixtures;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Format26_3WriterTest {
    private static final String SETTINGS = "data/iris_export/worldgen/noise_settings/flat.json";
    private static final String PRESET = "data/iris_export/worldgen/world_preset/flat.json";
    private static final String NORMAL_TAG = "data/minecraft/tags/worldgen/world_preset/normal.json";
    private static final String DIMENSION = "data/iris_export/dimension/flat.json";
    private static final String OVERWORLD = "data/minecraft/dimension/overworld.json";
    private static final String DENSITY_PREFIX = "data/iris_export/worldgen/density_function/";
    private static final String FEATURE = "data/iris_export/worldgen/feature/ore.json";
    private static final String TEMPLATE = "data/iris_export/structure/ore.nbt";

    @Test
    public void writesTheIrisPackIcon() {
        byte[] icon = write(fixture(WorldgenExportMode.OVERWORLD)).get("pack.png");

        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, Arrays.copyOf(icon, 8));
        assertEquals(128, ByteBuffer.wrap(icon, 16, 4).getInt());
        assertEquals(128, ByteBuffer.wrap(icon, 20, 4).getInt());
    }

    @Test
    public void forTargetSelectsTheFormatWriter() {
        assertTrue(WorldgenJsonWriter.forTarget(DataVersion.V26_3) instanceof Format26_3Writer);
        assertThrows(IllegalArgumentException.class, () -> WorldgenJsonWriter.forTarget(DataVersion.V26_2));
        assertThrows(IllegalArgumentException.class, () -> WorldgenJsonWriter.forTarget(DataVersion.V26_1_2));
    }

    @Test
    public void packMcmetaDeclaresFormatRange() {
        JsonObject pack = json(write(fixture(WorldgenExportMode.PRESET)), "pack.mcmeta").getAsJsonObject("pack");
        assertEquals(121, pack.get("min_format").getAsInt());
        assertEquals(121, pack.get("max_format").getAsInt());
        assertFalse(pack.has("pack_format"));
        assertTrue(pack.get("description").getAsString().length() > 0);
    }

    @Test
    public void packDescriptionTellsPlayersToKeepTheVanillaPack() {
        String description = json(write(fixture(WorldgenExportMode.OVERWORLD)), "pack.mcmeta").getAsJsonObject("pack").get("description").getAsString();
        assertTrue(description, description.contains("Keep Default selected"));
    }

    @Test
    public void noiseFileUsesNormalNoiseParameters() {
        JsonObject noise = json(write(fixture(WorldgenExportMode.PRESET)), "data/iris_export/worldgen/noise/flat/height.json");
        assertEquals(JsonParser.parseString("{\"amplitude_modifiers\":[1.0,1.0,1.0],\"base_amplitude\":1.0,\"base_octave\":-7,\"normalize\":false,\"octave_count\":3}"), noise);
    }

    @Test
    public void noiseSettingsRouterHasEightFields26_3() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.PRESET));
        JsonObject settings = json(files, SETTINGS);
        JsonObject router = settings.getAsJsonObject("noise_router");
        assertEquals(Set.of("temperature", "vegetation", "continents", "erosion", "depth", "ridges", "chunk_surface_level", "final_density"), router.keySet());
        assertEquals(Set.of("barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "exclusion", "surface_level"), settings.getAsJsonObject("aquifers").keySet());
        assertEquals(Set.of("aquifers", "default_block", "default_fluid", "disable_mob_generation", "legacy_random_source", "material_rule", "noise",
                "noise_router", "sea_level", "spawn_target"), settings.keySet());
        assertEquals(JsonParser.parseString("{\"height\":384,\"min_y\":-64}"), settings.get("noise"));
        assertEquals("iris_export:flat", settings.get("material_rule").getAsString());
        assertTrue(files.containsKey("data/iris_export/worldgen/material_rule/flat.json"));
        assertEquals(ExportIrFixtures.FLUID_HEIGHT + 1, settings.get("sea_level").getAsInt());
        assertEquals(0.0, router.get("temperature").getAsDouble(), 0.0);
        assertResolves(files, router.get("final_density"));
        assertResolves(files, router.get("chunk_surface_level"));
        assertResolves(files, settings.getAsJsonObject("aquifers").get("surface_level"));
    }

    @Test
    public void spawnTargetIsKeyedByRegisteredDensityFunctions() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.PRESET));
        JsonArray targets = json(files, SETTINGS).getAsJsonArray("spawn_target");
        assertEquals(1, targets.size());
        for (Map.Entry<String, JsonElement> entry : targets.get(0).getAsJsonObject().entrySet()) {
            assertTrue(entry.getKey(), files.containsKey(DENSITY_PREFIX + entry.getKey().substring("iris_export:".length()) + ".json"));
            JsonArray range = entry.getValue().getAsJsonArray();
            assertTrue(range.get(0).getAsDouble() >= -2.0 && range.get(1).getAsDouble() <= 2.0);
            assertTrue(range.get(0).getAsDouble() < range.get(1).getAsDouble());
        }
    }

    @Test
    public void presetModeWritesWorldPresetAndNormalTag() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.PRESET));
        JsonObject dimensions = json(files, PRESET).getAsJsonObject("dimensions");
        assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), dimensions.keySet());
        JsonObject overworld = dimensions.getAsJsonObject("minecraft:overworld");
        assertEquals("iris_export:flat", overworld.get("type").getAsString());
        assertEquals("minecraft:noise", overworld.getAsJsonObject("generator").get("type").getAsString());
        assertEquals("iris_export:flat", overworld.getAsJsonObject("generator").get("settings").getAsString());
        assertEquals(JsonParser.parseString("{\"values\":[\"iris_export:flat\"]}"), json(files, NORMAL_TAG));
        assertFalse(files.containsKey(DIMENSION));
        assertFalse(files.containsKey(OVERWORLD));
    }

    @Test
    public void dimensionModeWritesNamespacedDimension() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.DIMENSION));
        JsonObject stem = json(files, DIMENSION);
        assertEquals("iris_export:flat", stem.get("type").getAsString());
        assertFalse(files.containsKey(PRESET));
        assertFalse(files.containsKey(NORMAL_TAG));
        assertFalse(files.containsKey(OVERWORLD));
    }

    @Test
    public void overworldModeOverridesMinecraftDimension() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.OVERWORLD));
        JsonObject stem = json(files, OVERWORLD);
        assertEquals("iris_export:flat", stem.get("type").getAsString());
        assertEquals("iris_export:flat", stem.getAsJsonObject("generator").get("settings").getAsString());
        assertFalse(files.containsKey(PRESET));
        assertFalse(files.containsKey(DIMENSION));
    }

    @Test
    public void climateLayoutPutsCodesOnTemperature() {
        ExportIr base = fixture(WorldgenExportMode.DIMENSION);
        ClimateLayout climate = new ClimateLayout(List.of(new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.TEMPERATURE, 0.1, 0.2)));
        ExportIr ir = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), base.router(), climate, base.biomes(), base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
        JsonObject source = json(write(ir), DIMENSION).getAsJsonObject("generator").getAsJsonObject("biome_source");
        assertEquals("minecraft:multi_noise", source.get("type").getAsString());
        JsonArray biomes = source.getAsJsonArray("biomes");
        assertEquals(1, biomes.size());
        JsonObject entry = biomes.get(0).getAsJsonObject();
        assertEquals("iris_export:plains", entry.get("biome").getAsString());
        JsonObject parameters = entry.getAsJsonObject("parameters");
        assertEquals(JsonParser.parseString("[0.1,0.2]"), parameters.get("temperature"));
        assertEquals("[0.1,0.2]", parameters.get("temperature").toString());
        for (String axis : List.of("humidity", "continentalness", "erosion", "depth", "weirdness", "offset")) {
            assertEquals(axis, 0.0, parameters.get(axis).getAsDouble(), 0.0);
        }
    }

    @Test
    public void blockStateSyntaxPerFamily() {
        SortedMap<String, byte[]> files = write(fixture(WorldgenExportMode.PRESET));
        JsonObject settings = json(files, SETTINGS);
        assertEquals(JsonParser.parseString("{\"id\":\"minecraft:stone\"}"), settings.get("default_block"));
        assertEquals(JsonParser.parseString("{\"id\":\"minecraft:water\"}"), settings.get("default_fluid"));
        assertEquals(JsonParser.parseString("{\"type\":\"minecraft:block\",\"result_state\":{\"id\":\"minecraft:stone\"}}"),
                json(files, "data/iris_export/worldgen/material_rule/flat.json"));
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        MaterialProgram legacy = new MaterialProgram(JsonParser.parseString("{\"type\":\"minecraft:block\",\"result_state\":{\"Name\":\"minecraft:stone\"}}"));
        ExportIr wrong = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), base.router(), base.climate(), base.biomes(), legacy, base.diagnostics(), base.jsonResources(), base.binaryResources());
        assertThrows(IllegalArgumentException.class, () -> write(wrong));
    }

    @Test
    public void dimensionTypeHeightsFollowNoiseSettings() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        JsonObject type = json(write(base), "data/iris_export/dimension_type/flat.json");
        assertEquals(-64, type.get("min_y").getAsInt());
        assertEquals(384, type.get("height").getAsInt());
        assertEquals(384, type.get("logical_height").getAsInt());
        assertEquals(true, type.get("has_skylight").getAsBoolean());
    }

    @Test
    public void unknownKeysAreRejected() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        JsonObject biome = ExportIrFixtures.biome();
        biome.add("spawners", new JsonObject());
        ExportIr legacyBiome = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), base.router(), base.climate(),
                List.of(new BiomeDef("plains", biome)), base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
        IllegalArgumentException biomeFailure = assertThrows(IllegalArgumentException.class, () -> write(legacyBiome));
        assertTrue(biomeFailure.getMessage(), biomeFailure.getMessage().contains("spawners"));
        JsonObject type = ExportIrFixtures.dimensionType();
        type.addProperty("bogus", 1);
        ExportIr bogusType = new ExportIr(base.namespace(), base.target(), base.mode(),
                new DimensionDef("flat", -64, 384, 384, 63, "minecraft:stone", "minecraft:water", type),
                base.noises(), base.router(), base.climate(), base.biomes(), base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
        assertThrows(IllegalArgumentException.class, () -> write(bogusType));
    }

    @Test
    public void unregisteredNoiseIsRejected() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        RouterDef router = base.router();
        Field missing = new Field.Noise("iris_export:missing", 1.0, 0.0, null, null, null);
        RouterDef broken = new RouterDef(missing, router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                router.chunkSurfaceLevel(), router.finalDensity(), router.aquifer());
        ExportIr ir = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), broken, base.climate(), base.biomes(), base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> write(ir));
        assertTrue(failure.getMessage(), failure.getMessage().contains("iris_export:missing"));
    }

    @Test
    public void noFileExceedsNestingLimit() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        Field chain = new Field.Noise("iris_export:flat/height", 1.0, 0.0, null, null, null);
        for (int index = 0; index < 700; index++) {
            chain = new Field.Binary(Field.BinaryOp.ADD, chain, new Field.Const(index * 0.001));
        }
        RouterDef router = base.router();
        RouterDef deep = new RouterDef(router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                router.chunkSurfaceLevel(), chain, AquiferDef.dry(router.aquifer().surfaceLevel()));
        ExportIr ir = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), deep, base.climate(), base.biomes(), base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
        SortedMap<String, byte[]> files = write(ir);
        for (String path : files.keySet()) {
            if (!isJson(path)) {
                continue;
            }
            JsonElement parsed = JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8));
            assertTrue(path, NestingSplitter.depth(parsed) <= 255);
            if (path.startsWith(DENSITY_PREFIX)) {
                assertReferencesResolve(files, parsed);
            }
        }
        assertResolves(files, json(files, SETTINGS).getAsJsonObject("noise_router").get("final_density"));
    }

    @Test
    public void finalDensityAlwaysHasItsNamedFile() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        RouterDef router = base.router();
        Field shared = new Field.Shared("flat/terrain", router.finalDensity());
        List<RouterDef> routers = List.of(
                new RouterDef(router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                        router.chunkSurfaceLevel(), new Field.Const(1.0), router.aquifer()),
                new RouterDef(router.finalDensity(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                        router.chunkSurfaceLevel(), router.finalDensity(), router.aquifer()),
                new RouterDef(router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                        router.chunkSurfaceLevel(), shared, router.aquifer()));
        for (RouterDef variant : routers) {
            ExportIr ir = new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), variant, base.climate(), base.biomes(),
                    base.material(), base.diagnostics(), base.jsonResources(), base.binaryResources());
            SortedMap<String, byte[]> files = write(ir);
            assertEquals("iris_export:flat/final_density", json(files, SETTINGS).getAsJsonObject("noise_router").get("final_density").getAsString());
            assertTrue(files.keySet().toString(), files.containsKey(DENSITY_PREFIX + "flat/final_density.json"));
        }
    }

    @Test
    public void outputIsSortedAndDeterministic() {
        SortedMap<String, byte[]> first = write(fixture(WorldgenExportMode.PRESET));
        SortedMap<String, byte[]> second = write(fixture(WorldgenExportMode.PRESET));
        assertEquals(first.keySet(), second.keySet());
        for (String path : first.keySet()) {
            assertArrayEquals(path, first.get(path), second.get(path));
            if (!isJson(path)) {
                continue;
            }
            String text = new String(first.get(path), StandardCharsets.UTF_8);
            assertTrue(path, text.endsWith("\n"));
            assertFalse(path, text.contains("\r"));
        }
        String settings = new String(first.get(SETTINGS), StandardCharsets.UTF_8);
        List<Integer> order = new ArrayList<>();
        for (String key : new TreeSet<>(json(first, SETTINGS).keySet())) {
            order.add(settings.indexOf("\"" + key + "\""));
        }
        List<Integer> sorted = new ArrayList<>(order);
        sorted.sort(Integer::compare);
        assertEquals(sorted, order);
    }

    private static void assertResolves(SortedMap<String, byte[]> files, JsonElement reference) {
        assertTrue(reference.toString(), reference.isJsonPrimitive() && reference.getAsJsonPrimitive().isString());
        assertTrue(reference.getAsString(), files.containsKey(DENSITY_PREFIX + reference.getAsString().substring("iris_export:".length()) + ".json"));
    }

    private static void assertReferencesResolve(SortedMap<String, byte[]> files, JsonElement element) {
        if (element.isJsonPrimitive()) {
            String value = element.getAsJsonPrimitive().isString() ? element.getAsString() : "";
            if (value.startsWith("iris_export:") && !value.equals("iris_export:flat/height")) {
                assertResolves(files, element);
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                assertReferencesResolve(files, member.getValue());
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                assertReferencesResolve(files, item);
            }
        }
    }

    @Test
    public void resourcesAreCopiedAtTheirPaths() {
        JsonObject feature = JsonParser.parseString("{\"type\":\"minecraft:ore\",\"config\":{\"size\":4,\"discard_chance_on_air_exposure\":0.0}}").getAsJsonObject();
        byte[] template = {10, 0, 0, 3, 1, 2};
        ExportIr ir = ExportIrFixtures.withResources(fixture(WorldgenExportMode.PRESET),
                List.of(new JsonResource(FEATURE, feature)), List.of(new BinaryResource(TEMPLATE, template)));
        SortedMap<String, byte[]> files = write(ir);
        assertEquals(feature, json(files, FEATURE));
        String text = new String(files.get(FEATURE), StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\n"));
        assertTrue(text.indexOf("\"config\"") < text.indexOf("\"type\""));
        assertTrue(text.indexOf("\"discard_chance_on_air_exposure\"") < text.indexOf("\"size\""));
        assertArrayEquals(template, files.get(TEMPLATE));
        assertArrayEquals(write(ir).get(FEATURE), files.get(FEATURE));
    }

    @Test
    public void resourcesCollidingWithGeneratedFilesAreRejected() {
        ExportIr base = fixture(WorldgenExportMode.PRESET);
        ExportIr jsonCollision = ExportIrFixtures.withResources(base, List.of(new JsonResource(SETTINGS, new JsonObject())), List.of());
        IllegalArgumentException jsonFailure = assertThrows(IllegalArgumentException.class, () -> write(jsonCollision));
        assertTrue(jsonFailure.getMessage(), jsonFailure.getMessage().contains(SETTINGS));
        ExportIr binaryCollision = ExportIrFixtures.withResources(base, List.of(), List.of(new BinaryResource("pack.mcmeta", new byte[] {1})));
        IllegalArgumentException binaryFailure = assertThrows(IllegalArgumentException.class, () -> write(binaryCollision));
        assertTrue(binaryFailure.getMessage(), binaryFailure.getMessage().contains("pack.mcmeta"));
    }

    private static ExportIr fixture(WorldgenExportMode mode) {
        return ExportIrFixtures.flatNoise(DataVersion.V26_3, mode);
    }

    private static SortedMap<String, byte[]> write(ExportIr ir) {
        return new Format26_3Writer().write(ir);
    }

    private static JsonObject json(SortedMap<String, byte[]> files, String path) {
        byte[] bytes = files.get(path);
        assertTrue(path, bytes != null);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean isJson(String path) {
        return path.endsWith(".json") || path.endsWith(".mcmeta");
    }
}
