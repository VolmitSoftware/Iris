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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ModBiomeAdopter {
    static final String INGEST_BIOME_FOLDER = "ingest/";
    private static final List<String> OVERRIDE_FIELDS = List.of("customDerivitives", "vanillaDerivative", "biomeScatter", "biomeSkyScatter", "children");
    private static final int VEGETAL_STEP = IrisDecorationStep.VEGETAL_DECORATION.ordinal();

    private final IngestPackModel model;
    private final ModBiomeClassifier classifier;
    private final Set<String> decoratorFeatures;

    ModBiomeAdopter(IngestPackModel model, WorldgenCatalogIndex index, Set<String> decoratorFeatures) {
        this.model = model;
        this.classifier = new ModBiomeClassifier(model.environment(), index.catalog());
        this.decoratorFeatures = decoratorFeatures;
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
            IngestClimate climate = IngestClimate.of(biome);
            IngestPackModel.RegionProfile region = nearestRegion(role, climate);
            if (region == null) {
                items.add(new ModIngestItem(namespace, biome.key(), "biome", ModIngestTier.SKIPPED, "",
                        "no region in this dimension has a " + role.label() + " biome pool"));
                continue;
            }
            String template = nearestTemplate(region, role, climate);
            String irisKey = irisKey(biome.key());
            JsonObject json = model.pack().require(IngestPack.biomePath(template)).deepCopy();
            transform(json, biome, ownsVegetation(biome, namespace));
            model.pack().create(IngestPack.biomePath(irisKey), json);
            appendToPool(region.key(), role, irisKey);
            adopted.put(biome.key(), new AdoptedBiome(biome.key(), irisKey, role));
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

    private IngestPackModel.RegionProfile nearestRegion(BiomeRole role, IngestClimate climate) {
        IngestPackModel.RegionProfile best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (IngestPackModel.RegionProfile region : model.regions()) {
            if (region.pool(role).isEmpty()) {
                continue;
            }
            double distance = climate.distance(region.climate());
            if (best == null || distance < bestDistance) {
                best = region;
                bestDistance = distance;
            }
        }
        return best;
    }

    private String nearestTemplate(IngestPackModel.RegionProfile region, BiomeRole role, IngestClimate climate) {
        String best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (String candidate : region.pool(role)) {
            IngestClimate candidateClimate = model.biomeClimate(candidate);
            double distance = climate.distance(candidateClimate == null ? region.climate() : candidateClimate);
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
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

    record AdoptedBiome(String modKey, String irisKey, BiomeRole role) {
    }
}
