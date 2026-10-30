package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRaritySelection;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

final class BiomePools {
    static final int MAX_CHILD_HOPS = 4;
    static final List<InferredType> SURFACE_TYPES = List.of(InferredType.LAND, InferredType.SEA, InferredType.SHORE);

    private final List<RegionPools> regions;
    private final SortedMap<String, IrisBiome> biomes;
    private final SortedSet<String> surface;
    private final SortedSet<String> cave;
    private final Map<String, ChildPlan> plans;

    private BiomePools(List<RegionPools> regions, SortedMap<String, IrisBiome> biomes, SortedSet<String> surface, SortedSet<String> cave, Map<String, ChildPlan> plans) {
        this.regions = regions;
        this.biomes = biomes;
        this.surface = surface;
        this.cave = cave;
        this.plans = plans;
    }

    static BiomePools load(IrisDimension dimension, IrisData data) {
        List<RegionPools> regions = new ArrayList<>();
        SortedMap<String, IrisBiome> biomes = new TreeMap<>();
        for (IrisRegion region : data.getRegionLoader().loadAll(dimension.getRegions())) {
            if (region.isCompatExcluded()) {
                continue;
            }
            RegionPools pools = new RegionPools(
                    region,
                    pool(data, region.getLandBiomes(), biomes),
                    pool(data, region.getSeaBiomes(), biomes),
                    pool(data, region.getShoreBiomes(), biomes),
                    pool(data, region.getCaveBiomes(), biomes));
            if (pools.land().isEmpty() && pools.sea().isEmpty()) {
                throw new IllegalStateException("Region '" + region.getLoadKey() + "' has no land or sea biome to export");
            }
            regions.add(pools);
        }
        if (regions.isEmpty()) {
            throw new IllegalStateException("Dimension '" + dimension.getLoadKey() + "' has no exportable region");
        }
        SortedSet<String> cave = new TreeSet<>();
        SortedSet<String> frontier = new TreeSet<>();
        for (RegionPools pools : regions) {
            for (InferredType type : SURFACE_TYPES) {
                for (IrisBiome biome : pools.of(type)) {
                    frontier.add(biome.getLoadKey());
                }
            }
            for (IrisBiome biome : pools.cave()) {
                cave.add(biome.getLoadKey());
            }
        }
        Map<String, ChildPlan> plans = new HashMap<>();
        SortedSet<String> surface = new TreeSet<>(frontier);
        for (int hop = 0; hop < MAX_CHILD_HOPS && !frontier.isEmpty(); hop++) {
            SortedSet<String> next = new TreeSet<>();
            for (String key : frontier) {
                ChildPlan plan = plans.computeIfAbsent(key, ignored -> ChildPlan.of(biomes.get(key), data, biomes));
                for (String choice : plan.choices()) {
                    if (surface.add(choice)) {
                        next.add(choice);
                    }
                }
            }
            frontier = next;
        }
        return new BiomePools(List.copyOf(regions), biomes, surface, cave, plans);
    }

    static List<Double> rarityCuts(List<? extends Rarity> options) {
        double total = 0.0;
        for (Rarity option : options) {
            total += 1.0 / Rarity.get(option);
        }
        List<Double> cuts = new ArrayList<>(Math.max(0, options.size() - 1));
        double cumulative = 0.0;
        for (int index = 0; index + 1 < options.size(); index++) {
            cumulative += 1.0 / Rarity.get(options.get(index));
            cuts.add(cumulative / total);
        }
        return cuts;
    }

    static List<Double> selectionCuts(int size) {
        int maximum = size - 1;
        List<Double> cuts = new ArrayList<>(Math.max(0, maximum));
        for (int index = 0; index < maximum; index++) {
            cuts.add((index + 0.5) / maximum);
        }
        return cuts;
    }

    List<RegionPools> regions() {
        return regions;
    }

    IrisBiome biome(String loadKey) {
        return Objects.requireNonNull(biomes.get(loadKey), "Biome '" + loadKey + "' was not loaded");
    }

    SortedSet<String> surface() {
        return surface;
    }

    SortedSet<String> cave() {
        return cave;
    }

    ChildPlan plan(String loadKey) {
        return plans.get(loadKey);
    }

    private static List<IrisBiome> pool(IrisData data, KList<String> keys, SortedMap<String, IrisBiome> biomes) {
        List<IrisBiome> pool = new ArrayList<>();
        for (IrisBiome biome : data.getBiomeLoader().loadAll(keys)) {
            if (biome.isCompatExcluded()) {
                continue;
            }
            biomes.putIfAbsent(biome.getLoadKey(), biome);
            pool.add(biome);
        }
        return List.copyOf(pool);
    }

    record RegionPools(IrisRegion region, List<IrisBiome> land, List<IrisBiome> sea, List<IrisBiome> shore, List<IrisBiome> cave) {
        List<IrisBiome> of(InferredType type) {
            return switch (type) {
                case LAND -> land;
                case SEA -> sea;
                case SHORE -> shore;
                case CAVE -> cave;
            };
        }
    }

    record ChildPlan(String parent, List<String> choiceByIndex) {
        static ChildPlan of(IrisBiome parent, IrisData data, SortedMap<String, IrisBiome> biomes) {
            if (parent.getChildren().isEmpty()) {
                return new ChildPlan(parent.getLoadKey(), List.of(parent.getLoadKey()));
            }
            KList<IrisBiome> options = new KList<>();
            for (IrisBiome child : parent.getRealChildren(() -> data)) {
                if (child != null) {
                    biomes.putIfAbsent(child.getLoadKey(), child);
                    options.add(child);
                }
            }
            options.add(parent);
            IrisRaritySelection<IrisBiome> selection = IrisRaritySelection.create(options);
            long size = selection.size();
            List<String> choices = new ArrayList<>((int) size);
            for (long index = 0; index < size; index++) {
                choices.add(selection.get(index).getLoadKey());
            }
            return new ChildPlan(parent.getLoadKey(), List.copyOf(choices));
        }

        boolean moves() {
            for (String choice : choiceByIndex) {
                if (!choice.equals(parent)) {
                    return true;
                }
            }
            return false;
        }

        SortedSet<String> choices() {
            return new TreeSet<>(choiceByIndex);
        }

    }
}
