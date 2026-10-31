package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ContentTrim;
import art.arcane.iris.pack.datapack.export.ExportIrFixtures;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UnusedResourcePrunerTest {
    private static final String EXPORT = "iris_export";
    private static final String TOWNS = "towns";
    private static final String PACK = "pack.mcmeta";
    private static final String DIMENSION = "data/iris_export/dimension/main.json";
    private static final String DIMENSION_TYPE = "data/iris_export/dimension_type/main.json";
    private static final String SETTINGS = "data/iris_export/worldgen/noise_settings/main.json";
    private static final String FINAL_DENSITY = "data/iris_export/worldgen/density_function/main/final_density.json";
    private static final String HEIGHT_NOISE = "data/iris_export/worldgen/noise/main/height.json";
    private static final String MATERIAL = "data/iris_export/worldgen/material_rule/main.json";
    private static final String FOREST = "data/iris_export/worldgen/biome/forest.json";
    private static final String DISABLED_TAG = "data/iris_export/tags/worldgen/biome/structure/disabled.json";
    private static final String HOUSES = "data/towns/worldgen/structure_set/houses.json";
    private static final String HOUSE = "data/towns/worldgen/structure/house.json";
    private static final String HAS_HOUSE = "data/towns/tags/worldgen/biome/has_house.json";
    private static final String HOUSE_START = "data/towns/worldgen/template_pool/house/start.json";
    private static final String HOUSE_ROOMS = "data/towns/worldgen/template_pool/house/rooms.json";
    private static final String MOSSY = "data/towns/worldgen/processor_list/mossy.json";
    private static final String REPLACEABLE = "data/towns/tags/block/house_replaceable.json";
    private static final String HOUSE_BASE = "data/towns/structure/house/base.nbt";
    private static final String HOUSE_ROOM = "data/towns/structure/house/room.nbt";
    private static final String HOUSE_LOOT = "data/towns/loot_table/chests/house.json";
    private static final String SHARED_LOOT = "data/towns/loot_table/chests/shared.json";

    @Test
    public void unusedTemplateIsDropped() {
        SortedMap<String, byte[]> files = world("iris_export:object/oak");
        object(files, EXPORT, "object/oak", "feature");
        object(files, EXPORT, "object/hut", "feature");

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, placed(EXPORT, "object/oak"), registry(EXPORT, "feature", "object/oak"), template(EXPORT, "object/oak"));
        assertDropped(pruned, placed(EXPORT, "object/hut"), registry(EXPORT, "feature", "object/hut"), template(EXPORT, "object/hut"));
    }

    @Test
    public void templateReferencedFromPoolIsKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        files.put(template(TOWNS, "house/unused"), nbt());

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, HOUSES, HOUSE, HAS_HOUSE, HOUSE_START, HOUSE_ROOMS, MOSSY, HOUSE_BASE, HOUSE_ROOM);
        assertDropped(pruned, template(TOWNS, "house/unused"));
    }

    @Test
    public void lootTableReferencedFromTemplateNbtIsKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        files.put("data/towns/loot_table/chests/unused.json", json("{\"pools\":[]}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, HOUSE_LOOT, SHARED_LOOT);
        assertDropped(pruned, "data/towns/loot_table/chests/unused.json");
    }

    @Test
    public void packIconIsKeptWithoutReachingItsBytes() {
        SortedMap<String, byte[]> files = world();
        files.put("data/iris_export/worldgen/noise/main/unused.json", json("{\"firstOctave\":-7,\"amplitudes\":[1.0]}"));
        files.put("pack.png", "PNG iris_export:main/unused".getBytes(StandardCharsets.ISO_8859_1));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, "pack.png");
        assertDropped(pruned, "data/iris_export/worldgen/noise/main/unused.json");
    }

    @Test
    public void unusedNoiseAndDensityFunctionAreDropped() {
        SortedMap<String, byte[]> files = world();
        files.put("data/iris_export/worldgen/density_function/main/unused.json",
                json("{\"type\":\"minecraft:noise\",\"noise\":\"iris_export:main/unused\",\"xz_scale\":1.0,\"y_scale\":1.0}"));
        files.put("data/iris_export/worldgen/noise/main/unused.json", json("{\"firstOctave\":-7,\"amplitudes\":[1.0]}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, SETTINGS, FINAL_DENSITY, HEIGHT_NOISE, MATERIAL);
        assertDropped(pruned, "data/iris_export/worldgen/density_function/main/unused.json", "data/iris_export/worldgen/noise/main/unused.json");
    }

    @Test
    public void biomeNotInClimateLayoutIsDropped() {
        SortedMap<String, byte[]> files = world("iris_export:object/oak");
        object(files, EXPORT, "object/oak", "feature");
        files.put("data/iris_export/worldgen/biome/desert.json", biome(List.of("iris_export:canyon"), "iris_export:object/cactus"));
        files.put(registry(EXPORT, "carver", "canyon"), json("{\"type\":\"minecraft:canyon\"}"));
        object(files, EXPORT, "object/cactus", "feature");

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, FOREST, template(EXPORT, "object/oak"));
        assertDropped(pruned, "data/iris_export/worldgen/biome/desert.json", registry(EXPORT, "carver", "canyon"),
                placed(EXPORT, "object/cactus"), registry(EXPORT, "feature", "object/cactus"), template(EXPORT, "object/cactus"));
    }

    @Test
    public void tagReferencedByHashIsKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        files.put(HAS_HOUSE, json("{\"values\":[\"#towns:forests\"]}"));
        files.put("data/towns/tags/worldgen/biome/forests.json", json("{\"values\":[\"iris_export:forest\"]}"));
        files.put("data/towns/tags/worldgen/biome/unused.json", json("{\"values\":[\"iris_export:forest\"]}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, HAS_HOUSE, "data/towns/tags/worldgen/biome/forests.json", REPLACEABLE);
        assertDropped(pruned, "data/towns/tags/worldgen/biome/unused.json");
    }

    @Test
    public void nonWorldgenDatapackContentIsKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        List<String> gameplay = List.of(
                "data/towns/function/setup.mcfunction",
                "data/minecraft/tags/function/load.json",
                "data/towns/advancement/root.json",
                "data/towns/recipe/brick.json",
                "data/towns/tags/item/bricks.json");
        files.put(gameplay.get(0), "loot give @s loot towns:rewards/start\nplace template towns:house/statue ~ ~ ~\n".getBytes(StandardCharsets.UTF_8));
        files.put(gameplay.get(1), json("{\"values\":[\"towns:setup\"]}"));
        files.put(gameplay.get(2), json("{\"criteria\":{\"tick\":{\"trigger\":\"minecraft:tick\"}},\"rewards\":{\"loot\":[\"towns:rewards/advancement\"]}}"));
        files.put(gameplay.get(3), json("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:clay_ball\"],\"result\":{\"id\":\"minecraft:brick\"}}"));
        files.put(gameplay.get(4), json("{\"values\":[\"minecraft:brick\"]}"));
        files.put("data/towns/loot_table/rewards/start.json", json("{\"pools\":[]}"));
        files.put("data/towns/loot_table/rewards/advancement.json", json("{\"pools\":[]}"));
        files.put("data/towns/loot_table/rewards/unused.json", json("{\"pools\":[]}"));
        files.put(template(TOWNS, "house/statue"), nbt());

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, gameplay.toArray(String[]::new));
        assertKept(pruned, "data/towns/loot_table/rewards/start.json", "data/towns/loot_table/rewards/advancement.json", template(TOWNS, "house/statue"));
        assertDropped(pruned, "data/towns/loot_table/rewards/unused.json");
    }

    @Test
    public void disabledDatapackStructureChainIsDropped() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        towers(files);

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertDropped(pruned, "data/towns/worldgen/structure_set/towers.json", "data/towns/worldgen/structure/tower.json", DISABLED_TAG,
                "data/towns/worldgen/template_pool/tower/start.json", "data/towns/worldgen/template_pool/tower/top.json",
                template(TOWNS, "tower/base"), template(TOWNS, "tower/top"));
        assertKept(pruned, HOUSES, HOUSE, HOUSE_START, MOSSY, HOUSE_BASE);
    }

    @Test
    public void structureSetWithAnEnabledStructureIsKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        towers(files);
        files.put("data/towns/worldgen/structure_set/towers.json", json("""
                {"structures":[{"structure":"towns:tower","weight":1},{"structure":"towns:ruin","weight":1}],
                 "placement":{"type":"minecraft:random_spread","salt":2,"spacing":32,"separation":8}}
                """));
        files.put("data/towns/worldgen/structure/ruin.json", json("{\"type\":\"minecraft:jigsaw\",\"biomes\":[\"iris_export:forest\"],\"start_pool\":\"minecraft:empty\"}"));
        files.put("data/towns/worldgen/structure_set/nowhere.json", json("""
                {"structures":[{"structure":"towns:nowhere","weight":1}],
                 "placement":{"type":"minecraft:random_spread","salt":3,"spacing":32,"separation":8}}
                """));
        files.put("data/towns/worldgen/structure/nowhere.json", json("{\"type\":\"minecraft:jigsaw\",\"biomes\":[],\"start_pool\":\"minecraft:empty\"}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, "data/towns/worldgen/structure_set/towers.json", "data/towns/worldgen/structure/tower.json",
                "data/towns/worldgen/structure/ruin.json", DISABLED_TAG, template(TOWNS, "tower/top"));
        assertDropped(pruned, "data/towns/worldgen/structure_set/nowhere.json", "data/towns/worldgen/structure/nowhere.json");
    }

    @Test
    public void vanillaNamespaceContentIsKept() {
        SortedMap<String, byte[]> files = world();
        List<String> vanilla = List.of(
                "data/minecraft/worldgen/structure/village_plains.json",
                "data/minecraft/worldgen/template_pool/village/plains/houses.json",
                "data/minecraft/loot_table/chests/village/village_weaponsmith.json",
                "data/minecraft/tags/worldgen/biome/is_forest.json",
                "data/minecraft/structure/village/plains/houses/plains_small_house_1.nbt");
        files.put(vanilla.get(0), json("{\"type\":\"minecraft:jigsaw\",\"biomes\":\"#iris_export:structure/disabled\",\"start_pool\":\"minecraft:village/plains/town_centers\"}"));
        files.put(vanilla.get(1), json("{\"fallback\":\"minecraft:empty\",\"elements\":[]}"));
        files.put(vanilla.get(2), json("{\"pools\":[]}"));
        files.put(vanilla.get(3), json("{\"replace\":false,\"values\":[\"iris_export:forest\"]}"));
        files.put(vanilla.get(4), nbt());
        files.put(DISABLED_TAG, json("{\"replace\":false,\"values\":[]}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, vanilla.toArray(String[]::new));
        assertKept(pruned, DISABLED_TAG);
    }

    @Test
    public void tagsOfTagOnlyNamespacesAreKept() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        files.put("data/c/tags/worldgen/biome/is_forest.json", json("{\"values\":[\"iris_export:forest\"]}"));
        files.put("data/c/tags/block/ores.json", json("{\"values\":[\"minecraft:iron_ore\"]}"));
        files.put("data/towns/tags/worldgen/biome/unused.json", json("{\"values\":[\"iris_export:forest\"]}"));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, "data/c/tags/worldgen/biome/is_forest.json", "data/c/tags/block/ores.json");
        assertDropped(pruned, "data/towns/tags/worldgen/biome/unused.json");
    }

    @Test
    public void legacyFeatureAndCarverFoldersAreTrimmed() {
        SortedMap<String, byte[]> files = world();
        files.put(FOREST, biome(List.of("iris_export:cave"), "iris_export:object/oak"));
        files.put(registry(EXPORT, "configured_carver", "cave"), json("{\"type\":\"minecraft:cave\",\"config\":{}}"));
        files.put(registry(EXPORT, "configured_carver", "canyon"), json("{\"type\":\"minecraft:canyon\",\"config\":{}}"));
        object(files, EXPORT, "object/oak", "configured_feature");
        object(files, EXPORT, "object/hut", "configured_feature");

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, registry(EXPORT, "configured_carver", "cave"), registry(EXPORT, "configured_feature", "object/oak"), template(EXPORT, "object/oak"));
        assertDropped(pruned, registry(EXPORT, "configured_carver", "canyon"), registry(EXPORT, "configured_feature", "object/hut"), template(EXPORT, "object/hut"));
    }

    @Test
    public void unreadableFilesAreScannedAsText() {
        SortedMap<String, byte[]> files = world();
        houses(files);
        files.put(HOUSE_ROOM, "corrupt template towns:chests/house".getBytes(StandardCharsets.ISO_8859_1));
        files.put(HOUSE_ROOMS, "{\"elements\":[\"towns:house/room\"".getBytes(StandardCharsets.UTF_8));

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertKept(pruned, HOUSE_ROOMS, HOUSE_ROOM, HOUSE_LOOT, SHARED_LOOT);
        List<String> unreadable = new ArrayList<>();
        for (ExportDiagnostic diagnostic : pruned.diagnostics()) {
            if (diagnostic.severity() == ExportDiagnostic.Severity.WARNING) {
                assertSame(ExportLimit.TRIMMED_CONTENT, diagnostic.limit());
                unreadable.add(diagnostic.resource());
            }
        }
        assertEquals(List.of(HOUSE_ROOM, HOUSE_ROOMS), unreadable);
    }

    @Test
    public void trimReportsFileAndByteCounts() {
        SortedMap<String, byte[]> files = world();
        byte[] orphan = nbt();
        files.put(template(EXPORT, "object/orphan"), orphan);
        long bytes = 0L;
        for (byte[] content : files.values()) {
            bytes += content.length;
        }

        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

        assertEquals(new ContentTrim(files.size(), bytes, files.size() - 1, bytes - orphan.length), pruned.trim());
        List<ExportDiagnostic> summaries = new ArrayList<>();
        for (ExportDiagnostic diagnostic : pruned.diagnostics()) {
            if (diagnostic.severity() == ExportDiagnostic.Severity.INFO) {
                summaries.add(diagnostic);
            }
        }
        assertEquals(1, summaries.size());
        assertSame(ExportLimit.TRIMMED_CONTENT, summaries.getFirst().limit());
        String message = summaries.getFirst().message();
        assertTrue(message, message.contains("1 of " + files.size() + " files"));
        assertTrue(message, message.contains(orphan.length + " of " + bytes + " bytes"));
    }

    @Test
    public void prunerIsIdempotent() {
        SortedMap<String, byte[]> files = world("iris_export:object/oak");
        object(files, EXPORT, "object/oak", "feature");
        object(files, EXPORT, "object/hut", "feature");
        houses(files);
        towers(files);
        files.put("data/c/tags/worldgen/biome/is_forest.json", json("{\"values\":[\"iris_export:forest\"]}"));
        files.put("data/towns/tags/worldgen/biome/unused.json", json("{\"values\":[\"iris_export:forest\"]}"));
        files.put("data/towns/function/setup.mcfunction", "loot give @s loot towns:chests/shared\n".getBytes(StandardCharsets.UTF_8));

        UnusedResourcePruner.Pruned first = UnusedResourcePruner.prune(files);
        UnusedResourcePruner.Pruned second = UnusedResourcePruner.prune(first.files());

        assertTrue(first.trim().filesRemoved() > 0);
        assertEquals(first.files().keySet(), second.files().keySet());
        for (Map.Entry<String, byte[]> file : first.files().entrySet()) {
            assertArrayEquals(file.getKey(), file.getValue(), second.files().get(file.getKey()));
        }
        assertEquals(0, second.trim().filesRemoved());
        assertEquals(0L, second.trim().bytesRemoved());
    }

    @Test
    public void routerAndPresetAlwaysKept() {
        for (DataVersion target : WorldgenExportTargets.SUPPORTED) {
            for (WorldgenExportMode mode : WorldgenExportMode.values()) {
                SortedMap<String, byte[]> files = WorldgenJsonWriter.forTarget(target).write(ExportIrFixtures.flatNoise(target, mode));
                files.put("data/iris_export/worldgen/density_function/flat/orphan.json", json("{\"type\":\"minecraft:constant\",\"argument\":1.0}"));

                UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);

                String label = target.getVersion() + " " + mode;
                assertEquals(label, 1, pruned.trim().filesRemoved());
                assertDropped(pruned, "data/iris_export/worldgen/density_function/flat/orphan.json");
                assertKept(pruned, PACK, "data/iris_export/worldgen/noise_settings/flat.json", "data/iris_export/worldgen/density_function/flat/final_density.json",
                        "data/iris_export/worldgen/noise/flat/height.json", "data/iris_export/worldgen/biome/plains.json", "data/iris_export/dimension_type/flat.json");
                switch (mode) {
                    case PRESET -> assertKept(pruned, "data/iris_export/worldgen/world_preset/flat.json", "data/minecraft/tags/worldgen/world_preset/normal.json");
                    case DIMENSION -> assertKept(pruned, "data/iris_export/dimension/flat.json");
                    case OVERWORLD -> assertKept(pruned, "data/minecraft/dimension/overworld.json");
                }
            }
        }
    }

    private static SortedMap<String, byte[]> world(String... features) {
        SortedMap<String, byte[]> files = new TreeMap<>();
        files.put(PACK, json("{\"pack\":{\"description\":\"test\",\"min_format\":121,\"max_format\":121}}"));
        files.put(DIMENSION, json("""
                {"type":"iris_export:main","generator":{"type":"minecraft:noise","settings":"iris_export:main",
                 "biome_source":{"type":"minecraft:multi_noise","biomes":[{"biome":"iris_export:forest","parameters":{"temperature":[-2.0,2.0]}}]}}}
                """));
        files.put(DIMENSION_TYPE, json("{\"infiniburn\":\"#minecraft:infiniburn_overworld\",\"min_y\":-64,\"height\":384}"));
        files.put(SETTINGS, json("{\"material_rule\":\"iris_export:main\",\"noise_router\":{\"final_density\":\"iris_export:main/final_density\"}}"));
        files.put(FINAL_DENSITY, json("{\"type\":\"minecraft:noise\",\"noise\":\"iris_export:main/height\",\"xz_scale\":1.0,\"y_scale\":1.0}"));
        files.put(HEIGHT_NOISE, json("{\"firstOctave\":-7,\"amplitudes\":[1.0]}"));
        files.put(MATERIAL, json("{\"type\":\"minecraft:block\",\"result_state\":\"minecraft:stone\"}"));
        files.put(FOREST, biome(List.of(), features));
        return files;
    }

    private static byte[] biome(List<String> carvers, String... features) {
        StringBuilder steps = new StringBuilder("[[],[],[],[],[],[],[],[],[],[");
        for (int index = 0; index < features.length; index++) {
            steps.append(index == 0 ? "" : ",").append('"').append(features[index]).append('"');
        }
        steps.append("],[]]");
        StringBuilder carverList = new StringBuilder("[");
        for (int index = 0; index < carvers.size(); index++) {
            carverList.append(index == 0 ? "" : ",").append('"').append(carvers.get(index)).append('"');
        }
        carverList.append(']');
        return json("{\"has_precipitation\":true,\"temperature\":0.8,\"downfall\":0.4,\"effects\":{\"water_color\":\"#3f76e4\"},\"carvers\":"
                + carverList + ",\"features\":" + steps + "}");
    }

    private static void object(SortedMap<String, byte[]> files, String namespace, String path, String featureFolder) {
        String id = namespace + ":" + path;
        files.put(placed(namespace, path), json("{\"feature\":\"" + id + "\",\"placement\":[{\"type\":\"minecraft:count\",\"count\":1}]}"));
        files.put(registry(namespace, featureFolder, path), json("{\"type\":\"minecraft:template\",\"templates\":[{\"data\":{\"id\":\"" + id + "\"},\"weight\":1}]}"));
        files.put(template(namespace, path), nbt());
    }

    private static void houses(SortedMap<String, byte[]> files) {
        files.put(HOUSES, json("""
                {"structures":[{"structure":"towns:house","weight":1}],
                 "placement":{"type":"minecraft:random_spread","salt":1,"spacing":24,"separation":8}}
                """));
        files.put(HOUSE, json("""
                {"type":"minecraft:jigsaw","biomes":"#towns:has_house","step":"surface_structures","spawn_overrides":{},
                 "start_pool":"towns:house/start","size":3,"start_height":{"absolute":0},"max_distance_from_center":64,"use_expansion_hack":false}
                """));
        files.put(HAS_HOUSE, json("{\"values\":[\"iris_export:forest\"]}"));
        files.put(HOUSE_START, pool("towns:house/base", "towns:mossy"));
        files.put(MOSSY, json("""
                {"processors":[{"processor_type":"minecraft:rule","rules":[{
                 "input_predicate":{"predicate_type":"minecraft:tag_match","tag":"towns:house_replaceable"},
                 "location_predicate":{"predicate_type":"minecraft:always_true"},"output_state":{"Name":"minecraft:mossy_cobblestone"}}]}]}
                """));
        files.put(REPLACEABLE, json("{\"values\":[\"minecraft:cobblestone\"]}"));
        files.put(HOUSE_BASE, nbt(jigsaw("towns:house/rooms")));
        files.put(HOUSE_ROOMS, pool("towns:house/room", "minecraft:empty"));
        files.put(HOUSE_ROOM, nbt(chest("towns:chests/house")));
        files.put(HOUSE_LOOT, json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"towns:chests/shared\"}]}]}"));
        files.put(SHARED_LOOT, json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:bread\"}]}]}"));
    }

    private static void towers(SortedMap<String, byte[]> files) {
        files.put("data/towns/worldgen/structure_set/towers.json", json("""
                {"structures":[{"structure":"towns:tower","weight":1}],
                 "placement":{"type":"minecraft:random_spread","salt":2,"spacing":32,"separation":8}}
                """));
        files.put("data/towns/worldgen/structure/tower.json", json("""
                {"type":"minecraft:jigsaw","biomes":"#iris_export:structure/disabled","step":"surface_structures","spawn_overrides":{},
                 "start_pool":"towns:tower/start","size":2,"start_height":{"absolute":0},"max_distance_from_center":64,"use_expansion_hack":false}
                """));
        files.put(DISABLED_TAG, json("{\"replace\":false,\"values\":[]}"));
        files.put("data/towns/worldgen/template_pool/tower/start.json", pool("towns:tower/base", "towns:mossy"));
        files.put(template(TOWNS, "tower/base"), nbt(jigsaw("towns:tower/top")));
        files.put("data/towns/worldgen/template_pool/tower/top.json", pool("towns:tower/top", "minecraft:empty"));
        files.put(template(TOWNS, "tower/top"), nbt());
    }

    private static byte[] pool(String location, String processors) {
        return json("{\"fallback\":\"minecraft:empty\",\"elements\":[{\"weight\":1,\"element\":{\"element_type\":\"minecraft:single_pool_element\","
                + "\"location\":\"" + location + "\",\"processors\":\"" + processors + "\",\"projection\":\"rigid\"}}]}");
    }

    private static CompoundTag jigsaw(String pool) {
        CompoundTag entity = new CompoundTag();
        entity.putString("pool", pool);
        entity.putString("target", "towns:door");
        entity.putString("name", "towns:entrance");
        entity.putString("final_state", "minecraft:air");
        entity.putString("joint", "rollable");
        return entity;
    }

    private static CompoundTag chest(String lootTable) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:chest");
        entity.putString("LootTable", lootTable);
        return entity;
    }

    private static byte[] nbt(CompoundTag... blockEntities) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            NBTUtil.write(new NamedTag("", template(blockEntities)), gzip, false);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    private static CompoundTag template(CompoundTag... blockEntities) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 5023);
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        palette.add(state);
        root.put("palette", palette);
        ListTag<CompoundTag> blocks = new ListTag<>(CompoundTag.class);
        for (CompoundTag blockEntity : blockEntities) {
            CompoundTag block = new CompoundTag();
            block.putInt("state", 0);
            block.put("nbt", blockEntity);
            blocks.add(block);
        }
        root.put("blocks", blocks);
        return root;
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String placed(String namespace, String path) {
        return registry(namespace, "placed_feature", path);
    }

    private static String registry(String namespace, String folder, String path) {
        return "data/" + namespace + "/worldgen/" + folder + "/" + path + ".json";
    }

    private static String template(String namespace, String path) {
        return "data/" + namespace + "/structure/" + path + ".nbt";
    }

    private static void assertKept(UnusedResourcePruner.Pruned pruned, String... paths) {
        for (String path : paths) {
            assertTrue(path + " was dropped", pruned.files().containsKey(path));
        }
    }

    private static void assertDropped(UnusedResourcePruner.Pruned pruned, String... paths) {
        for (String path : paths) {
            assertFalse(path + " was kept", pruned.files().containsKey(path));
        }
    }
}
