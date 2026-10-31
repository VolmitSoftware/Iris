package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ImageMapStage implements CompileStage {
    private static final String DIMENSION_MESSAGE = "Image maps are not exported; the dimension generates from its procedural layers";
    private static final String STYLE_MESSAGE = "Generator style image maps are not exported; the base noise style is used";

    @Override
    public String name() {
        return "image-maps";
    }

    @Override
    public void apply(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        IrisData data = context.data();
        Gson gson = data.getGson();
        if (usesImageMaps(gson.toJsonTree(dimension))) {
            report(context, "dimensions/" + dimension.getLoadKey(), DIMENSION_MESSAGE);
        }
        dimension.setImageMaps(new KList<>());
        SortedMap<String, IrisRegion> regions = new TreeMap<>();
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            regions.put(region.getLoadKey(), region);
        }
        reportStyles(context, gson, "regions/", regions);
        SortedMap<String, IrisBiome> biomes = new TreeMap<>();
        SortedSet<String> generatorKeys = new TreeSet<>();
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            biomes.put(biome.getLoadKey(), biome);
            collectGenerators(biome.getGenerators(), generatorKeys);
        }
        reportStyles(context, gson, "biomes/", biomes);
        SortedMap<String, IrisGenerator> generators = new TreeMap<>();
        for (String key : generatorKeys) {
            IrisGenerator generator = data.getGeneratorLoader().load(key, false);
            if (generator != null) {
                generators.put(key, generator);
            }
        }
        reportStyles(context, gson, "generators/", generators);
    }

    private static void collectGenerators(List<IrisBiomeGeneratorLink> links, SortedSet<String> generatorKeys) {
        if (links == null) {
            return;
        }
        for (IrisBiomeGeneratorLink link : links) {
            if (link != null && link.getGenerator() != null && !link.getGenerator().isBlank()) {
                generatorKeys.add(link.getGenerator().trim());
            }
        }
    }

    private static void reportStyles(PackCompileContext context, Gson gson, String folder, SortedMap<String, ?> resources) {
        for (Map.Entry<String, ?> entry : resources.entrySet()) {
            if (usesImageMaps(gson.toJsonTree(entry.getValue()))) {
                report(context, folder + entry.getKey(), STYLE_MESSAGE);
            }
        }
    }

    private static void report(PackCompileContext context, String resource, String message) {
        context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.IMAGE_MAP, resource, message));
    }

    private static boolean usesImageMaps(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (usesImageMaps(child)) {
                    return true;
                }
            }
            return false;
        }
        if (!element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        if (namesImageMap(object.get("imageMap")) || bindsImageMaps(object.get("imageMaps"))) {
            return true;
        }
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            if (usesImageMaps(member.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesImageMap(JsonElement value) {
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank();
    }

    private static boolean bindsImageMaps(JsonElement value) {
        return value != null && value.isJsonArray() && !value.getAsJsonArray().isEmpty();
    }
}
