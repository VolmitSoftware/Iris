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

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class ModFeatureIngester {
    private static final String VANILLA_NAMESPACE = "minecraft";

    private final IngestPackModel model;
    private final WorldgenCatalogIndex index;
    private final TreeFeatures trees;
    private final CanopyEstimate canopy;
    private final Map<String, FeaturePlan> plans = new TreeMap<>();
    private final Set<String> nativeKeys = new TreeSet<>();
    private final Set<String> vanillaScopedKeys = new TreeSet<>();

    ModFeatureIngester(IngestPackModel model, WorldgenCatalogIndex index, Set<String> namespaces) {
        this.model = model;
        this.index = index;
        this.trees = new TreeFeatures(index);
        this.canopy = new CanopyEstimate(index);
        Map<String, Set<String>> declaring = new TreeMap<>();
        Map<String, Set<Integer>> steps = new TreeMap<>();
        for (CatalogBiome biome : index.catalog().biomes()) {
            for (int step = 0; step < biome.featureSteps().size(); step++) {
                for (String feature : biome.featureSteps().get(step)) {
                    if (!namespaces.contains(WorldgenCatalogIndex.namespaceOf(feature))) {
                        continue;
                    }
                    declaring.computeIfAbsent(feature, ignored -> new LinkedHashSet<>()).add(biome.key());
                    steps.computeIfAbsent(feature, ignored -> new TreeSet<>()).add(step);
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : declaring.entrySet()) {
            plans.put(entry.getKey(), plan(entry.getKey(), entry.getValue(), steps.get(entry.getKey())));
        }
    }

    Set<String> decoratorKeys() {
        Set<String> keys = new TreeSet<>();
        for (FeaturePlan plan : plans.values()) {
            if (plan.decorator() != null) {
                keys.add(plan.key());
            }
        }
        return keys;
    }

    Set<String> nativeKeys() {
        return nativeKeys;
    }

    Set<String> vanillaScopedKeys() {
        return vanillaScopedKeys;
    }

    Map<String, FeaturePlan> plans() {
        return plans;
    }

    List<ModIngestItem> apply(Map<String, ModBiomeAdopter.AdoptedBiome> adopted) throws IOException {
        Set<String> universe = new TreeSet<>(adopted.keySet());
        universe.addAll(model.reachableBiomes().values());
        Set<String> caveKeys = caveKeys(adopted);
        List<ModIngestItem> items = new ArrayList<>(plans.size());
        for (FeaturePlan plan : plans.values()) {
            String namespace = WorldgenCatalogIndex.namespaceOf(plan.key());
            Set<String> hosts = new TreeSet<>(plan.declaring());
            hosts.retainAll(universe);
            if (hosts.isEmpty()) {
                items.add(new ModIngestItem(namespace, plan.key(), "feature", ModIngestTier.SKIPPED, "",
                        "no biome in the target dimension declares it"));
                continue;
            }
            if (plan.deposits() != null) {
                items.add(new ModIngestItem(namespace, plan.key(), "feature", ModIngestTier.NATIVE,
                        applyDeposits(plan, adopted, universe, caveKeys), "ore converted to Iris deposits"));
                nativeKeys.add(plan.key());
                continue;
            }
            if (plan.decorator() != null) {
                items.add(new ModIngestItem(namespace, plan.key(), "feature", ModIngestTier.NATIVE,
                        applyDecorator(plan, adopted), "patch converted to an Iris decorator"));
                nativeKeys.add(plan.key());
                continue;
            }
            if (treeInPackVanillaBiomes(plan)) {
                vanillaScopedKeys.add(plan.key());
                items.add(new ModIngestItem(namespace, plan.key(), "feature", ModIngestTier.PASSTHROUGH, "importedFeatures",
                        plan.reason() + "; denied in minecraft biomes the pack already decorates, so its own trees are not doubled"));
                continue;
            }
            items.add(new ModIngestItem(namespace, plan.key(), "feature", ModIngestTier.PASSTHROUGH, "importedFeatures", plan.reason()));
        }
        return items;
    }

    private boolean treeInPackVanillaBiomes(FeaturePlan plan) {
        Optional<CatalogPlacedFeature> feature = index.placedFeature(plan.key());
        if (feature.isEmpty() || !trees.isTree(feature.get())) {
            return false;
        }
        for (String biome : plan.declaring()) {
            if (WorldgenCatalogIndex.namespaceOf(biome).equals(VANILLA_NAMESPACE) && model.reachableBiomes().containsValue(biome)) {
                return true;
            }
        }
        return false;
    }

    private FeaturePlan plan(String key, Set<String> declaring, Set<Integer> steps) {
        Optional<CatalogPlacedFeature> feature = index.placedFeature(key);
        if (feature.isEmpty()) {
            return FeaturePlan.passthrough(key, declaring, steps, "placed feature is missing from the catalog");
        }
        CatalogPlacedFeature placed = feature.get();
        try {
            if (OreDepositConverter.handles(placed)) {
                List<JsonObject> deposits = OreDepositConverter.convert(placed, model.verticalBounds(), model.packMinY());
                return new FeaturePlan(key, declaring, steps, deposits, null, "");
            }
            if (PatchDecoratorConverter.handles(placed)) {
                return new FeaturePlan(key, declaring, steps, null,
                        PatchDecoratorConverter.convert(placed, index, canopy.occlusion(key, declaring)), "");
            }
        } catch (IngestConversionException e) {
            return FeaturePlan.passthrough(key, declaring, steps, "generated natively: " + e.getMessage());
        } catch (RuntimeException e) {
            return FeaturePlan.passthrough(key, declaring, steps, "generated natively: malformed " + placed.featureType() + " data (" + e.getMessage() + ")");
        }
        return FeaturePlan.passthrough(key, declaring, steps, "generated natively: feature type " + placed.featureType() + " has no Iris equivalent");
    }

    private String applyDeposits(FeaturePlan plan, Map<String, ModBiomeAdopter.AdoptedBiome> adopted, Set<String> universe, Set<String> caveKeys) throws IOException {
        List<String> adoptedTargets = new ArrayList<>();
        boolean vanillaDeclared = false;
        for (String biome : plan.declaring()) {
            vanillaDeclared |= WorldgenCatalogIndex.namespaceOf(biome).equals("minecraft");
            ModBiomeAdopter.AdoptedBiome adoptedBiome = adopted.get(biome);
            if (adoptedBiome != null) {
                adoptedTargets.add(adoptedBiome.irisKey());
            }
        }
        if (!vanillaDeclared && !adoptedTargets.isEmpty()) {
            for (String irisKey : adoptedTargets) {
                appendAll(model.pack().require(IngestPack.biomePath(irisKey)), "deposits", plan.deposits());
            }
            return String.join(",", adoptedTargets);
        }
        Set<String> included = new TreeSet<>(plan.declaring());
        included.retainAll(universe);
        List<JsonObject> deposits = new ArrayList<>(plan.deposits().size());
        for (JsonObject deposit : plan.deposits()) {
            JsonObject copy = deposit.deepCopy();
            if (!included.containsAll(universe)) {
                copy.add("includedBiomes", IngestJson.stringArray(included));
                copy.addProperty("biomeScope", caveKeys.containsAll(included) ? "CAVE" : "SURFACE");
            }
            deposits.add(copy);
        }
        appendAll(model.pack().edit(model.dimensionPath()), "deposits", deposits);
        return "dimension";
    }

    private String applyDecorator(FeaturePlan plan, Map<String, ModBiomeAdopter.AdoptedBiome> adopted) throws IOException {
        List<String> targets = new ArrayList<>();
        for (String biome : plan.declaring()) {
            ModBiomeAdopter.AdoptedBiome adoptedBiome = adopted.get(biome);
            if (adoptedBiome != null) {
                appendAll(model.pack().require(IngestPack.biomePath(adoptedBiome.irisKey())), "decorators", List.of(plan.decorator()));
                targets.add(adoptedBiome.irisKey());
            }
        }
        for (Map.Entry<String, String> biome : model.reachableBiomes().entrySet()) {
            if (plan.declaring().contains(biome.getValue())) {
                appendAll(model.pack().edit(IngestPack.biomePath(biome.getKey())), "decorators", List.of(plan.decorator()));
                targets.add(biome.getKey());
            }
        }
        return String.join(",", targets);
    }

    private Set<String> caveKeys(Map<String, ModBiomeAdopter.AdoptedBiome> adopted) {
        Set<String> caves = new TreeSet<>();
        for (ModBiomeAdopter.AdoptedBiome biome : adopted.values()) {
            if (biome.role() == BiomeRole.CAVE) {
                caves.add(biome.modKey());
            }
        }
        Set<String> surface = new TreeSet<>();
        for (IngestPackModel.RegionProfile region : model.regions()) {
            for (BiomeRole role : BiomeRole.values()) {
                for (String biome : region.pool(role)) {
                    String derivative = model.reachableBiomes().get(biome);
                    (role == BiomeRole.CAVE ? caves : surface).add(derivative);
                }
            }
        }
        caves.removeAll(surface);
        return caves;
    }

    private static void appendAll(JsonObject target, String field, List<JsonObject> values) {
        JsonElement existing = target.get(field);
        JsonArray array = existing != null && existing.isJsonArray() ? existing.getAsJsonArray() : new JsonArray();
        for (JsonObject value : values) {
            array.add(value.deepCopy());
        }
        target.add(field, array);
    }

    record FeaturePlan(String key, Set<String> declaring, Set<Integer> steps, List<JsonObject> deposits, JsonObject decorator, String reason) {
        static FeaturePlan passthrough(String key, Set<String> declaring, Set<Integer> steps, String reason) {
            return new FeaturePlan(key, declaring, steps, null, null, reason);
        }
    }
}
