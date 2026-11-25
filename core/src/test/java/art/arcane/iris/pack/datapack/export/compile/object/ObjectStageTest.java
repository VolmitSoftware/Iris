package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class ObjectStageTest {
    private static final String NAMESPACE = WorldgenExportRequest.DEFAULT_NAMESPACE;
    private static final String FOREST = NAMESPACE + ":forest";
    private static final String PLAINS = NAMESPACE + ":plains";
    private static final Field ZERO = new Field.Const(0.0);

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public BoundPlatform platform = new BoundPlatform();

    @Test
    public void objectsFalseExportsNoObjects() throws Exception {
        File pack = pack();
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr ir = compile(data, DataVersion.V26_3, false);

            assertTrue(ir.jsonResources().isEmpty());
            assertTrue(ir.binaryResources().isEmpty());
            for (BiomeDef biome : ir.biomes()) {
                for (JsonElement step : biome.json().getAsJsonArray("features")) {
                    assertEquals(0, step.getAsJsonArray().size());
                }
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void objectsExportTemplatesFeaturesAndBiomeLists() throws Exception {
        File pack = pack();
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr ir = compile(data, DataVersion.V26_3, true);

            assertTrue(paths(ir).stream().anyMatch(path -> path.startsWith("data/iris_export/worldgen/feature/object/")));
            assertTrue(paths(ir).stream().anyMatch(path -> path.startsWith("data/iris_export/worldgen/placed_feature/object/")));
            assertTrue(paths(ir).stream().noneMatch(path -> path.contains("configured_feature")));
            assertFalse(ir.binaryResources().isEmpty());
            for (BinaryResource template : ir.binaryResources()) {
                assertTrue(template.path(), template.path().startsWith("data/iris_export/structure/object/"));
                assertTrue(template.path().endsWith(".nbt"));
                CompoundTag root = (CompoundTag) NBTUtil.read(new ByteArrayInputStream(template.bytes()), true).getTag();
                assertEquals(5023, root.getInt("DataVersion"));
            }
            JsonArray forest = features(ir, "forest");
            assertEquals(1, forest.get(GenerationStep.VEGETAL_DECORATION.ordinal()).getAsJsonArray().size());
            assertEquals(1, forest.get(GenerationStep.SURFACE_STRUCTURES.ordinal()).getAsJsonArray().size());
            JsonArray plains = features(ir, "plains");
            assertEquals(0, plains.get(GenerationStep.VEGETAL_DECORATION.ordinal()).getAsJsonArray().size());
            assertEquals(1, plains.get(GenerationStep.SURFACE_STRUCTURES.ordinal()).getAsJsonArray().size());
            Set<String> referenced = new HashSet<>();
            for (BiomeDef biome : ir.biomes()) {
                for (JsonElement step : biome.json().getAsJsonArray("features")) {
                    for (JsonElement feature : step.getAsJsonArray()) {
                        referenced.add("data/iris_export/worldgen/placed_feature/" + feature.getAsString().substring(NAMESPACE.length() + 1) + ".json");
                    }
                }
            }
            assertTrue(paths(ir).containsAll(referenced));
            assertTrue(ir.diagnostics().stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.STRUCTURE_AVOIDANCE));
        } finally {
            data.close();
        }
    }

    @Test
    public void lootBoundPerBiome() throws Exception {
        File pack = pack();
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr modern = compile(data, DataVersion.V26_3, true);

            String forestHut = features(modern, "forest").get(GenerationStep.SURFACE_STRUCTURES.ordinal()).getAsJsonArray().get(0).getAsString();
            String plainsHut = features(modern, "plains").get(GenerationStep.SURFACE_STRUCTURES.ordinal()).getAsJsonArray().get(0).getAsString();
            assertNotEquals(forestHut, plainsHut);
            String forestTable = appendedLoot(modern, forestHut);
            String plainsTable = appendedLoot(modern, plainsHut);
            assertNotEquals(forestTable, plainsTable);
            assertTrue(lootReferences(modern, forestTable).contains("iris_export:iris/forest_loot"));
            assertTrue(lootReferences(modern, plainsTable).contains("iris_export:iris/plains_loot"));
            assertTrue(paths(modern).contains("data/iris_export/loot_table/iris/forest_loot.json"));

        } finally {
            data.close();
        }
    }

    @Test
    public void exportIsDeterministic() throws Exception {
        File pack = pack();
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr first = compile(data, DataVersion.V26_3, true);
            ExportIr second = compile(data, DataVersion.V26_3, true);

            assertEquals(first.jsonResources(), second.jsonResources());
            assertEquals(first.binaryResources().size(), second.binaryResources().size());
            for (int index = 0; index < first.binaryResources().size(); index++) {
                assertEquals(first.binaryResources().get(index).path(), second.binaryResources().get(index).path());
                assertArrayEquals(first.binaryResources().get(index).bytes(), second.binaryResources().get(index).bytes());
            }
            assertEquals(first.biomes(), second.biomes());
        } finally {
            data.close();
        }
    }

    private static ExportIr compile(IrisData data, DataVersion target, boolean objects) {
        IrisDimension dimension = data.getDimensionLoader().load("main", false);
        PackCompileContext context = PackCompileContext.builder()
                .data(data)
                .dimension(dimension)
                .capabilities(ExportCapabilities.forTarget(target))
                .namespace(NAMESPACE)
                .mode(WorldgenExportMode.PRESET)
                .objects(objects)
                .build();
        CompileStage biomes = new CompileStage() {
            @Override
            public String name() {
                return "biomes";
            }

            @Override
            public void apply(PackCompileContext context) {
                JsonObject body = new JsonObject();
                body.addProperty("has_precipitation", true);
                context.setBiomes(List.of(new BiomeDef("forest", body), new BiomeDef("plains", body)));
                ClimateLayout layout = new ClimateLayout(List.of(
                        new ClimateLayout.ClimateEntry(FOREST, ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 0.0),
                        new ClimateLayout.ClimateEntry(PLAINS, ClimateLayout.ClimateAxis.TEMPERATURE, 0.0, 1.0)));
                context.setBiomeCodes(new BiomeCodes(ZERO, ZERO, ZERO, layout, Map.of(FOREST, "forest", PLAINS, "plains")));
            }
        };
        return new PackCompiler(List.of(new DimensionStage(), biomes, new ObjectStage())).compile(context, WorldgenExportProgress.NONE);
    }

    private File pack() throws IOException {
        File pack = temporaryFolder.newFolder("pack");
        CompatTestPlatform.write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        CompatTestPlatform.write(pack, "regions/region.json", """
                {"landBiomes":["forest","plains"],
                 "objects":[{"place":["huts/hut"],"chance":0.1,"density":1,"rotation":{"yAxis":{"enabled":true,"interval":45}}}]}
                """);
        CompatTestPlatform.write(pack, "biomes/forest.json", """
                {"name":"Forest","loot":{"tables":["forest_loot"]},
                 "objects":[{"place":["trees/oak"],"chance":0.5,"density":3,"scale":{"minimumScale":0.75,"maximumScale":1.5,"variations":3}}]}
                """);
        CompatTestPlatform.write(pack, "biomes/plains.json", "{\"name\":\"Plains\",\"loot\":{\"tables\":[\"plains_loot\"]}}");
        CompatTestPlatform.write(pack, "loot/forest_loot.json", "{\"name\":\"Forest\",\"loot\":[{\"type\":\"DIAMOND\",\"rarity\":2}]}");
        CompatTestPlatform.write(pack, "loot/plains_loot.json", "{\"name\":\"Plains\",\"loot\":[{\"type\":\"WHEAT\",\"rarity\":1}]}");
        writeObject(pack, "trees/oak", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:oak_leaves");
        writeObject(pack, "huts/hut", "minecraft:cobblestone", "minecraft:chest", "minecraft:cobblestone");
        return pack;
    }

    private static void writeObject(File pack, String key, String... column) throws IOException {
        File file = new File(pack, "objects/" + key + ".iob");
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
            throw new IOException("Could not create " + file.getParentFile());
        }
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(column.length);
            out.writeInt(1);
            out.writeUTF("Iris V2 IOB;");
            out.writeShort(column.length);
            for (String entry : column) {
                out.writeUTF(entry);
            }
            out.writeInt(column.length);
            for (int index = 0; index < column.length; index++) {
                out.writeShort(0);
                out.writeShort(index - column.length / 2);
                out.writeShort(0);
                out.writeShort(index);
            }
            out.writeInt(0);
        }
    }

    private static JsonArray features(ExportIr ir, String biome) {
        for (BiomeDef definition : ir.biomes()) {
            if (definition.id().equals(biome)) {
                return definition.json().getAsJsonArray("features");
            }
        }
        throw new AssertionError("No biome " + biome);
    }

    private static String appendedLoot(ExportIr ir, String placedFeature) {
        JsonObject placed = json(ir, "data/iris_export/worldgen/placed_feature/" + placedFeature.substring(NAMESPACE.length() + 1) + ".json");
        String featureId = placed.get("feature").getAsString();
        JsonObject feature = json(ir, "data/iris_export/worldgen/feature/" + featureId.substring(NAMESPACE.length() + 1) + ".json");
        for (JsonElement processor : feature.getAsJsonObject("processors").getAsJsonArray("processors")) {
            for (JsonElement rule : processor.getAsJsonObject().getAsJsonArray("rules")) {
                JsonObject modifier = rule.getAsJsonObject().getAsJsonObject("block_entity_modifier");
                if (modifier != null) {
                    return modifier.get("loot_table").getAsString();
                }
            }
        }
        throw new AssertionError("No append_loot rule in " + featureId);
    }

    private static List<String> lootReferences(ExportIr ir, String table) {
        JsonObject loot = json(ir, "data/iris_export/loot_table/" + table.substring(NAMESPACE.length() + 1) + ".json");
        List<String> references = new ArrayList<>();
        for (JsonElement pool : loot.getAsJsonArray("pools")) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                references.add(entry.getAsJsonObject().get("value").getAsString());
            }
        }
        return references;
    }

    private static JsonObject json(ExportIr ir, String path) {
        for (JsonResource resource : ir.jsonResources()) {
            if (resource.path().equals(path)) {
                return resource.json().getAsJsonObject();
            }
        }
        throw new AssertionError("No resource " + path);
    }

    private static Set<String> paths(ExportIr ir) {
        Set<String> paths = new HashSet<>();
        for (JsonResource resource : ir.jsonResources()) {
            paths.add(resource.path());
        }
        for (BinaryResource resource : ir.binaryResources()) {
            paths.add(resource.path());
        }
        return paths;
    }

    public static final class BoundPlatform extends ExternalResource {
        private static final List<String> BLOCKS = List.of("minecraft:air", "minecraft:stone", "minecraft:water",
                "minecraft:grass_block", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:cobblestone", "minecraft:chest");
        private IrisSettings previousSettings;
        private CompatTestPlatform platform;
        private Path folder;

        @Override
        protected void before() throws IOException {
            previousSettings = IrisSettings.settings;
            IrisSettings.settings = new IrisSettings();
            folder = Files.createTempDirectory("iris-object-export-platform");
            platform = CompatTestPlatform.bind(folder.toFile(), BLOCKS);
            doReturn("0.9").when(IrisPlatforms.get()).minecraftVersion();
        }

        @Override
        protected void after() {
            platform.unbind();
            IrisSettings.settings = previousSettings;
            try (Stream<Path> paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not remove test platform folder " + folder, exception);
            }
        }
    }
}
