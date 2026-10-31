package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeIdSanitizer;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class StageTestPacks {
    public static final String NAMESPACE = "iris_export";

    private StageTestPacks() {
    }

    public static File pack(File folder, String dimensionJson, Map<String, String> biomes) throws IOException {
        CompatTestPlatform.write(folder, "dimensions/" + ExportTestPacks.DIMENSION + ".json", dimensionJson);
        StringBuilder land = new StringBuilder();
        for (String key : biomes.keySet()) {
            land.append(land.isEmpty() ? "" : ",").append('"').append(key).append('"');
        }
        CompatTestPlatform.write(folder, "regions/region.json", "{\"landBiomes\":[" + land + "]}");
        for (Map.Entry<String, String> biome : biomes.entrySet()) {
            CompatTestPlatform.write(folder, "biomes/" + biome.getKey() + ".json", biome.getValue());
        }
        return folder;
    }

    public static PackCompileContext context(IrisData data, DataVersion target, List<String> biomeKeys) {
        PackCompileContext context = ExportTestPacks.context(data, target);
        new DimensionStage().apply(context);
        Map<String, String> loadKeys = new LinkedHashMap<>();
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>(biomeKeys.size());
        Set<String> taken = new HashSet<>();
        for (int index = 0; index < biomeKeys.size(); index++) {
            String id = BiomeIdSanitizer.sanitize(NAMESPACE, biomeKeys.get(index), taken);
            loadKeys.put(id, biomeKeys.get(index));
            entries.add(new ClimateLayout.ClimateEntry(id, ClimateLayout.ClimateAxis.TEMPERATURE, -1.0 + index * 0.01, -1.0 + (index + 1) * 0.01));
        }
        Field zero = new Field.Const(0.0);
        context.setBiomeCodes(new BiomeCodes(zero, zero, zero, new ClimateLayout(entries), loadKeys));
        return context;
    }

    public static String biomeId(String key) {
        return BiomeIdSanitizer.sanitize(NAMESPACE, key, new HashSet<>());
    }

    public static List<JsonObject> find(JsonElement root, Predicate<JsonObject> predicate) {
        List<JsonObject> found = new ArrayList<>();
        collect(root, predicate, found);
        return found;
    }

    public static List<JsonObject> ofType(JsonElement root, String type) {
        return find(root, object -> object.has("type") && object.get("type").isJsonPrimitive() && type.equals(object.get("type").getAsString()));
    }

    private static void collect(JsonElement element, Predicate<JsonObject> predicate, List<JsonObject> found) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                collect(item, predicate, found);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (predicate.test(object)) {
            found.add(object);
        }
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            collect(member.getValue(), predicate, found);
        }
    }
}
