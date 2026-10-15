/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.pack.ingest;

import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ModBiomeAdopter {
    static final String INGEST_BIOME_FOLDER = "ingest/";
    private static final List<String> OVERRIDE_FIELDS = List.of("customDerivitives", "vanillaDerivative", "biomeScatter", "biomeSkyScatter", "children");
    private static final int VEGETAL_STEP = IrisDecorationStep.VEGETAL_DECORATION.ordinal();
    private static final double CATEGORY_WEIGHT = 1D;
    private static final double REGION_CLIMATE_WEIGHT = 0.25D;
    private static final double REUSE_WEIGHT = 0.05D;
    private static final double THEMED_REGION_PENALTY = 0.6D;
    private static final double BARE_SURFACE_PENALTY = 3D;
    private static final int THEMED_RARITY_FACTOR = 5;

    private final IngestPackModel model;
    private final ModBiomeClassifier classifier;
    private final Set<String> decoratorFeatures;
    private final Set<String> excludedRegions;
    private final Set<String> themedRegions;
    private final Map<String, Integer> templateUses = new HashMap<>();

    ModBiomeAdopter(IngestPackModel model, WorldgenCatalogIndex index, Set<String> decoratorFeatures, Set<String> excludedRegions) {
        this.model = model;
        this.classifier = new ModBiomeClassifier(model.environment(), index.catalog());
        this.decoratorFeatures = decoratorFeatures;
        this.excludedRegions = Set.copyOf(excludedRegions);
        this.themedRegions = themedRegions(model.regions());
    }

    Map<String, AdoptedBiome> adopt(List<CatalogBiome> modBiomes, List<ModIngestItem> items) throws IOException {
        Map<String, AdoptedBiome> adopted = new LinkedHashMap<>();
        for (CatalogBiome biome : modBiomes) {
            String namespace = WorldgenCatalogIndex.namespaceOf(biome.key());
            ModBiomeClassifier.Classification classification = classifier.classify(biome);
            if (classification.skipped()) {
                items.add(new ModIngestItem(namespace, biome.key(), "biome", ModIngestTier.SKIPPED, "", classification.skipReason()));
                continue;
            }
            BiomeRole role = classification.role();
            boolean ownsVegetation = ownsVegetation(biome, namespace);
            Placement placement = place(role, IngestClimate.of(biome), BiomeCategories.of(biome.tags()), ownsVegetation);
            if (placement == null) {
                items.add(new ModIngestItem(namespace, biome.key(), "biome", ModIngestTier.SKIPPED, "",
                        "no region in this dimension accepts a " + role.label() + " biome"));
                continue;
            }
            IngestPackModel.RegionProfile region = placement.region();
            String template = placement.template();
            templateUses.merge(template, 1, Integer::sum);
            String irisKey = irisKey(biome.key());
            JsonObject json = model.pack().require(IngestPack.biomePath(template)).deepCopy();
            transform(json, biome, ownsVegetation);
            model.pack().create(IngestPack.biomePath(irisKey), json);
            appendToPool(region.key(), role, irisKey);
            adopted.put(biome.key(), new AdoptedBiome(biome.key(), irisKey, role, region.key()));
            items.add(new ModIngestItem(namespace, biome.key(), "biome", ModIngestTier.ADOPTED, irisKey,
                    role.label() + " biome in region " + region.key() + " from template " + template));
        }
        return adopted;
    }

    static String irisKey(String modBiomeKey) {
        int colon = modBiomeKey.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : modBiomeKey.substring(0, colon);
        return INGEST_BIOME_FOLDER + namespace + "/" + modBiomeKey.substring(colon + 1);
    }

    private Placement place(BiomeRole role, IngestClimate climate, Set<String> categories, boolean ownsVegetation) throws IOException {
        Placement best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (IngestPackModel.RegionProfile region : model.regions()) {
            if (excludedRegions.contains(region.key())) {
                continue;
            }
            double regionPenalty = (themedRegions.contains(region.key()) ? THEMED_REGION_PENALTY : 0D)
                    + (region.climate() == null ? 0D : REGION_CLIMATE_WEIGHT * climate.distance(region.climate()));
            for (String template : region.pool(role)) {
                IngestClimate templateClimate = model.biomeClimate(template);
                double score = climate.distance(templateClimate == null ? region.climate() : templateClimate)
                        + CATEGORY_WEIGHT * BiomeCategories.distance(categories, model.biomeCategories(template))
                        + REUSE_WEIGHT * templateUses.getOrDefault(template, 0)
                        + (ownsVegetation ? BARE_SURFACE_PENALTY * (1D - model.plantableShare(template)) : 0D)
                        + regionPenalty;
                if (best == null || score < bestScore) {
                    best = new Placement(region, template);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static Set<String> themedRegions(List<IngestPackModel.RegionProfile> regions) {
        if (regions.isEmpty()) {
            return Set.of();
        }
        List<Integer> rarities = new ArrayList<>(regions.size());
        for (IngestPackModel.RegionProfile region : regions) {
            rarities.add(region.rarity());
        }
        rarities.sort(Comparator.naturalOrder());
        int median = Math.max(1, rarities.get(rarities.size() / 2));
        Set<String> themed = new HashSet<>();
        for (IngestPackModel.RegionProfile region : regions) {
            if (region.rarity() >= median * THEMED_RARITY_FACTOR) {
                themed.add(region.key());
            }
        }
        return themed;
    }

    private void transform(JsonObject json, CatalogBiome biome, boolean ownsVegetation) throws IOException {
        json.addProperty("name", readableName(biome.key()));
        json.addProperty("derivative", biome.key());
        for (String field : OVERRIDE_FIELDS) {
            json.remove(field);
        }
        if (!ownsVegetation) {
            return;
        }
        json.remove("objects");
        JsonObject procedural = model.pack().snippet(json.get("proceduralObjects"), "procedural-objects");
        if (procedural == null) {
            return;
        }
        JsonObject withoutTrees = procedural.deepCopy();
        withoutTrees.remove("trees");
        json.add("proceduralObjects", withoutTrees);
    }

    private boolean ownsVegetation(CatalogBiome biome, String namespace) {
        if (biome.featureSteps().size() <= VEGETAL_STEP) {
            return false;
        }
        for (String feature : biome.featureSteps().get(VEGETAL_STEP)) {
            if (WorldgenCatalogIndex.namespaceOf(feature).equals(namespace) && !decoratorFeatures.contains(feature)) {
                return true;
            }
        }
        return false;
    }

    private void appendToPool(String regionKey, BiomeRole role, String irisKey) throws IOException {
        JsonObject region = model.pack().edit(IngestPack.regionPath(regionKey));
        JsonElement pool = region.get(role.poolField());
        JsonArray array = pool != null && pool.isJsonArray() ? pool.getAsJsonArray() : new JsonArray();
        array.add(irisKey);
        region.add(role.poolField(), array);
    }

    private static String readableName(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder(leaf.length());
        for (String word : leaf.split("[_\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.isEmpty() ? key : name.toString();
    }

    record AdoptedBiome(String modKey, String irisKey, BiomeRole role, String regionKey) {
    }

    private record Placement(IngestPackModel.RegionProfile region, String template) {
    }
}
