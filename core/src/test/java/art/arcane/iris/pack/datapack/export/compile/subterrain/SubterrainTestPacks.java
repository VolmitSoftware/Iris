package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

final class SubterrainTestPacks {
    static final String NAMESPACE = "iris_export";
    static final String FOREST = "iris_export:forest_land";
    static final String MAPPED = "iris_export:mapped";
    static final String OCEAN = "iris_export:ocean";
    static final String DESERT = "iris_export:desert";
    static final Map<String, String> EXPORTED = Map.of(FOREST, "Forest Land", MAPPED, "mapped", OCEAN, "ocean", DESERT, "desert");
    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    static final String CENOTE = """
            {"id": "pools", "family": "CENOTE", "worldYRange": {"min": -60, "max": 40}, "spacing": 64, "probability": 1.0,
             "radius": 8, "height": 12, "fluid": "WATER", "fluidDepth": 2, "shapeWarp": 0.5, "pillarSpacing": 0,
             "formationFraction": 0.0, "solid": "minecraft:stone"}
            """;
    static final String ROOM_CENOTE = """
            {"id": "grottos", "family": "CENOTE", "biome": "room", "worldYRange": {"min": -60, "max": 40}, "spacing": 64,
             "probability": 1.0, "radius": 8, "height": 12, "fluidDepth": 0, "shapeWarp": 0.0, "pillarSpacing": 0,
             "formationFraction": 0.0, "solid": "minecraft:stone"}
            """;
    static final String TUBE = """
            {"id": "tubes", "family": "LAVA_TUBE", "allowedRegions": ["second"], "worldYRange": {"min": -60, "max": 40},
             "spacing": 320, "probability": 0.5, "length": 300, "radius": 6, "height": 8, "fluidDepth": 2, "chimneyHeight": 0,
             "shapeWarp": 0.0, "pillarSpacing": 0, "formationFraction": 0.0, "solid": "minecraft:stone"}
            """;
    static final String SHALLOW = """
            {"id": "shallow", "family": "CENOTE", "worldYRange": {"min": -60, "max": -50}, "spacing": 64, "probability": 1.0,
             "radius": 8, "height": 12, "fluidDepth": 2, "solid": "minecraft:stone"}
            """;
    static final String DISABLED = """
            {"id": "off", "enabled": false, "family": "CENOTE", "spacing": 64, "probability": 1.0, "radius": 8, "height": 12,
             "solid": "minecraft:stone"}
            """;
    private static final Field ZERO = new Field.Const(0.0);

    private SubterrainTestPacks() {
    }

    static File pack(File folder, String... features) throws IOException {
        return packWith(folder, "", features);
    }

    static File packWith(File folder, String extra, String... features) throws IOException {
        String dimension = "{\"regions\":[\"region\",\"second\"],\"subterrainFeatures\":[" + String.join(",", features) + "]"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
        File pack = ExportTestPacks.withDimension(folder, dimension);
        CompatTestPlatform.write(pack, "regions/second.json", "{\"landBiomes\":[\"desert\"],\"seaBiomes\":[\"ocean\"]}");
        CompatTestPlatform.write(pack, "biomes/desert.json", "{\"name\":\"Desert\"}");
        CompatTestPlatform.write(pack, "biomes/room.json", """
                {"name": "Room",
                 "layers": [{"palette": [{"block": "minecraft:grass_block"}], "minHeight": 1, "maxHeight": 1}],
                 "caveCeilingLayers": [{"palette": [{"block": "minecraft:grass_block"}], "minHeight": 1, "maxHeight": 1}],
                 "wall": {"palette": [{"block": "minecraft:grass_block"}]}}
                """);
        return pack;
    }

    static PackCompileContext context(IrisData data, DataVersion target) {
        PackCompileContext context = ExportTestPacks.context(data, target);
        new DimensionStage().apply(context);
        context.setBiomeCodes(new BiomeCodes(ZERO, ZERO, ZERO, new ClimateLayout(List.of()), EXPORTED));
        return context;
    }

    static SortedMap<String, JsonElement> json(PackCompileContext context) {
        SortedMap<String, JsonElement> resources = new TreeMap<>();
        for (JsonResource resource : context.jsonResources()) {
            if (resources.put(resource.path(), resource.json()) != null) {
                throw new AssertionError("Duplicate json resource " + resource.path());
            }
        }
        return resources;
    }

    static SortedMap<String, byte[]> binaries(PackCompileContext context) {
        SortedMap<String, byte[]> resources = new TreeMap<>();
        for (BinaryResource resource : context.binaryResources()) {
            if (resources.put(resource.path(), resource.bytes()) != null) {
                throw new AssertionError("Duplicate binary resource " + resource.path());
            }
        }
        return resources;
    }

    static List<ExportDiagnostic> diagnostics(List<ExportDiagnostic> diagnostics, ExportLimit limit) {
        List<ExportDiagnostic> matching = new ArrayList<>();
        for (ExportDiagnostic diagnostic : diagnostics) {
            if (diagnostic.limit() == limit) {
                matching.add(diagnostic);
            }
        }
        return matching;
    }
}
