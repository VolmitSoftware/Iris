package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CaveTestPacks {
    static final String SURFACE_BIOME = "surface";
    static final long SEED = 1337L;
    static final int MIN_Y = -64;
    static final int MAX_Y = 192;
    static final String LISTED_CAVE = "{\"tunnel\": {}}";
    private static final Gson GSON = new Gson();

    private CaveTestPacks() {
    }

    static JsonObject dimension(int lavaHeight) {
        JsonObject height = new JsonObject();
        height.addProperty("min", MIN_Y);
        height.addProperty("max", MAX_Y);
        JsonArray regions = new JsonArray();
        regions.add("region");
        JsonObject dimension = new JsonObject();
        dimension.add("regions", regions);
        dimension.add("dimensionHeight", height);
        dimension.addProperty("fluidHeight", 63);
        dimension.addProperty("caveLavaHeight", lavaHeight);
        return dimension;
    }

    static JsonObject listing(JsonObject resource, List<String> caves) {
        JsonArray listed = new JsonArray();
        caves.forEach(listed::add);
        resource.add("caves", listed);
        return resource;
    }

    static JsonObject region() {
        JsonArray surface = new JsonArray();
        surface.add(SURFACE_BIOME);
        JsonObject region = new JsonObject();
        region.add("landBiomes", surface);
        region.add("seaBiomes", surface.deepCopy());
        return region;
    }

    /** A pack with one region, one surface biome and a cave file for each entry of {@code caves}. */
    static File pack(File folder, JsonObject dimension, JsonObject region, Map<String, String> caves) throws IOException {
        CompatTestPlatform.write(folder, "dimensions/" + ExportTestPacks.DIMENSION + ".json", GSON.toJson(dimension));
        CompatTestPlatform.write(folder, "regions/region.json", GSON.toJson(region));
        CompatTestPlatform.write(folder, "biomes/" + SURFACE_BIOME + ".json", "{\"name\":\"Surface\"}");
        for (Map.Entry<String, String> entry : new TreeMap<>(caves).entrySet()) {
            CompatTestPlatform.write(folder, "caves/" + entry.getKey() + ".json", entry.getValue());
        }
        return folder;
    }

    static PackCompileContext context(IrisData data, StyleCompiler styles) {
        PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(styles).build();
        new DimensionStage().apply(context);
        return context;
    }

    static Map<String, NoiseDef> noises(NoiseRegistry registry) {
        Map<String, NoiseDef> noises = new TreeMap<>();
        for (NoiseDef definition : registry.definitions()) {
            noises.put(registry.namespace() + ":" + definition.id(), definition);
        }
        return noises;
    }

    static double sample(Field field, NoiseRegistry registry, int x, int y, int z) {
        return FieldEvaluator.sample(field, noises(registry), SEED, DataVersion.V26_3, x, y, z);
    }
}
