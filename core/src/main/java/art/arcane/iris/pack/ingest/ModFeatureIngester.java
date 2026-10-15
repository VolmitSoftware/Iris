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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class ModFeatureIngester {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String REPLACEABLE_BLOCKS = "replaceableBlocks";
    private static final String PALETTE = "palette";

    private final IngestPackModel model;
    private final WorldgenCatalogIndex index;
    private final TreeFeatures trees;
    private final VegetationOcclusion occlusion;
    private final Map<String, FeaturePlan> plans = new TreeMap<>();
    private final Set<String> nativeKeys = new TreeSet<>();
    private final Set<String> vanillaScopedKeys = new TreeSet<>();

    ModFeatureIngester(IngestPackModel model, WorldgenCatalogIndex index, Set<String> namespaces) {
        this.model = model;
        this.index = index;
        this.trees = new TreeFeatures(index);
        this.occlusion = new VegetationOcclusion(index);
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
        for (FeaturePlan plan : hostProducersFirst(plans.values())) {
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

    static List<FeaturePlan> hostProducersFirst(Collection<FeaturePlan> plans) {
        List<FeaturePlan> pending = new ArrayList<>(plans);
        List<FeaturePlan> ordered = new ArrayList<>(pending.size());
        while (!pending.isEmpty()) {
            FeaturePlan next = pending.getFirst();
            for (FeaturePlan candidate : pending) {
                if (!waitsForHostProducer(candidate, pending)) {
                    next = candidate;
                    break;
                }
            }
            pending.remove(next);
            ordered.add(next);
        }
        return ordered;
    }

    private static boolean waitsForHostProducer(FeaturePlan consumer, List<FeaturePlan> pending) {
        Set<String> hosts = depositBlocks(consumer, REPLACEABLE_BLOCKS);
        if (hosts.isEmpty()) {
            return false;
        }
        for (FeaturePlan producer : pending) {
            if (producer != consumer && !Collections.disjoint(depositBlocks(producer, PALETTE), hosts)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> depositBlocks(FeaturePlan plan, String field) {
        if (plan.deposits() == null) {
            return Set.of();
        }
        Set<String> blocks = new TreeSet<>();
        for (JsonObject deposit : plan.deposits()) {
            JsonElement values = deposit.get(field);
            if (values == null || !values.isJsonArray()) {
                continue;
            }
            for (JsonElement value : values.getAsJsonArray()) {
                if (value.isJsonPrimitive()) {
                    blocks.add(value.getAsString());
                } else if (value.isJsonObject() && value.getAsJsonObject().has("block")) {
                    blocks.add(value.getAsJsonObject().get("block").getAsString());
                }
            }
        }
        return blocks;
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
                List<JsonObject> deposits = OreDepositConverter.convert(placed, model.verticalBounds(), model.packMinY(),
                        index.catalog().blockTags());
                return new FeaturePlan(key, declaring, steps, deposits, null, "");
            }
            if (PatchDecoratorConverter.handles(placed)) {
                return new FeaturePlan(key, declaring, steps, null,
                        PatchDecoratorConverter.convert(placed, index, occlusion.occlusion(key, declaring)), "");
            }
        } catch (IngestConversionException e) {
            return FeaturePlan.passthrough(key, declaring, steps, "generated natively: " + e.getMessage());
        } catch (RuntimeException e) {
            return FeaturePlan.passthrough(key, declaring, steps, "generated natively: malformed " + placed.featureType() + " data (" + e.getMessage() + ")");
        }
        return FeaturePlan.passthrough(key, declaring, steps, "generated natively: feature type " + placed.featureType() + " has no Iris equivalent");
    }

    private String applyDeposits(FeaturePlan plan, Map<String, ModBiomeAdopter.AdoptedBiome> adopted, Set<String> universe, Set<String> caveKeys) throws IOException {
        List<ModBiomeAdopter.AdoptedBiome> adoptedTargets = new ArrayList<>();
        boolean vanillaDeclared = false;
        for (String biome : plan.declaring()) {
            vanillaDeclared |= WorldgenCatalogIndex.namespaceOf(biome).equals("minecraft");
            ModBiomeAdopter.AdoptedBiome adoptedBiome = adopted.get(biome);
            if (adoptedBiome != null) {
                adoptedTargets.add(adoptedBiome);
            }
        }
        if (!vanillaDeclared && !adoptedTargets.isEmpty()) {
            List<String> targets = new ArrayList<>(adoptedTargets.size());
            Map<String, List<String>> caveBiomesByRegion = new TreeMap<>();
            for (ModBiomeAdopter.AdoptedBiome target : adoptedTargets) {
                if (target.role() == BiomeRole.CAVE) {
                    caveBiomesByRegion.computeIfAbsent(target.regionKey(), ignored -> new ArrayList<>()).add(target.irisKey());
                    continue;
                }
                appendAll(model.pack().require(IngestPack.biomePath(target.irisKey())), "deposits", plan.deposits());
                targets.add(target.irisKey());
            }
            for (Map.Entry<String, List<String>> region : caveBiomesByRegion.entrySet()) {
                appendAll(model.pack().edit(IngestPack.regionPath(region.getKey())), "deposits",
                        scoped(plan.deposits(), region.getValue(), "CAVE"));
                targets.add("region " + region.getKey());
            }
            return String.join(",", targets);
        }
        Set<String> included = new TreeSet<>(plan.declaring());
        included.retainAll(universe);
        List<JsonObject> deposits = included.containsAll(universe)
                ? plan.deposits()
                : scoped(plan.deposits(), List.copyOf(included), caveKeys.containsAll(included) ? "CAVE" : "SURFACE");
        appendAll(model.pack().edit(model.dimensionPath()), "deposits", deposits);
        return "dimension";
    }

    private static List<JsonObject> scoped(List<JsonObject> deposits, List<String> includedBiomes, String biomeScope) {
        List<JsonObject> scoped = new ArrayList<>(deposits.size());
        for (JsonObject deposit : deposits) {
            JsonObject copy = deposit.deepCopy();
            copy.add("includedBiomes", IngestJson.stringArray(includedBiomes));
            copy.addProperty("biomeScope", biomeScope);
            scoped.add(copy);
        }
        return scoped;
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
