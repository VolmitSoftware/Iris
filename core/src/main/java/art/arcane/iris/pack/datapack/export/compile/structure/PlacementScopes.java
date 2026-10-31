package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

final class PlacementScopes {
    private static final int MAX_CHILD_HOPS = 4;

    private PlacementScopes() {
    }

    static List<Scope> collect(PackCompileContext context, ExportedBiomes biomes) {
        IrisDimension dimension = context.dimension();
        IrisData data = context.data();
        Set<String> names = new HashSet<>();
        List<Scope> scopes = new ArrayList<>();
        scopes.add(new Scope(unique(names, "dimension"), "dimensions/" + dimension.getLoadKey(), biomes.ids(), new TreeSet<>(),
                placements(dimension.getStructures())));
        Map<String, IrisRegion> regions = new LinkedHashMap<>();
        Map<String, SortedSet<String>> regionIds = new LinkedHashMap<>();
        for (String regionKey : dimension.getRegions()) {
            IrisRegion region = regionKey == null ? null : data.getRegionLoader().load(regionKey, false);
            if (region != null && !region.isCompatExcluded() && !regions.containsKey(region.getLoadKey())) {
                regions.put(region.getLoadKey(), region);
                regionIds.put(region.getLoadKey(), biomes.idsFor(closure(data, region)));
            }
        }
        for (Map.Entry<String, IrisRegion> region : regions.entrySet()) {
            SortedSet<String> ids = regionIds.get(region.getKey());
            SortedSet<String> shared = new TreeSet<>();
            for (Map.Entry<String, SortedSet<String>> other : regionIds.entrySet()) {
                if (!other.getKey().equals(region.getKey())) {
                    for (String id : ids) {
                        if (other.getValue().contains(id)) {
                            shared.add(id);
                        }
                    }
                }
            }
            scopes.add(new Scope(unique(names, "region/" + VanillaResourceIdentifier.sanitizePath(region.getKey())), "regions/" + region.getKey(),
                    ids, shared, placements(region.getValue().getStructures())));
        }
        SortedMap<String, SortedSet<String>> idsByLoadKey = new TreeMap<>();
        SortedMap<String, IrisBiome> biomeByLoadKey = new TreeMap<>();
        for (Map.Entry<String, IrisBiome> biome : biomes.byId().entrySet()) {
            idsByLoadKey.computeIfAbsent(biome.getValue().getLoadKey(), ignored -> new TreeSet<>()).add(biome.getKey());
            biomeByLoadKey.putIfAbsent(biome.getValue().getLoadKey(), biome.getValue());
        }
        for (Map.Entry<String, SortedSet<String>> biome : idsByLoadKey.entrySet()) {
            scopes.add(new Scope(unique(names, "biome/" + VanillaResourceIdentifier.sanitizePath(biome.getKey())), "biomes/" + biome.getKey(),
                    biome.getValue(), new TreeSet<>(), placements(biomeByLoadKey.get(biome.getKey()).getStructures())));
        }
        return List.copyOf(scopes);
    }

    private static SortedSet<String> closure(IrisData data, IrisRegion region) {
        SortedSet<String> keys = new TreeSet<>();
        List<IrisBiome> frontier = new ArrayList<>();
        for (KList<String> pool : List.of(region.getLandBiomes(), region.getSeaBiomes(), region.getShoreBiomes(), region.getCaveBiomes())) {
            for (IrisBiome biome : data.getBiomeLoader().loadAll(pool)) {
                if (!biome.isCompatExcluded() && keys.add(biome.getLoadKey())) {
                    frontier.add(biome);
                }
            }
        }
        for (int hop = 0; hop < MAX_CHILD_HOPS && !frontier.isEmpty(); hop++) {
            List<IrisBiome> next = new ArrayList<>();
            for (IrisBiome parent : frontier) {
                for (IrisBiome child : parent.getRealChildren(() -> data)) {
                    if (child != null && !child.isCompatExcluded() && keys.add(child.getLoadKey())) {
                        next.add(child);
                    }
                }
            }
            frontier = next;
        }
        return keys;
    }

    private static List<IrisStructurePlacement> placements(KList<IrisStructurePlacement> placements) {
        List<IrisStructurePlacement> present = new ArrayList<>();
        if (placements != null) {
            for (IrisStructurePlacement placement : placements) {
                present.add(placement);
            }
        }
        return present;
    }

    private static String unique(Set<String> names, String name) {
        String candidate = name;
        for (int suffix = 1; !names.add(candidate); suffix++) {
            candidate = name + "_" + suffix;
        }
        return candidate;
    }

    record Scope(String name, String label, SortedSet<String> biomeIds, SortedSet<String> shared, List<IrisStructurePlacement> placements) {
        Scope {
            biomeIds = new TreeSet<>(biomeIds);
            shared = new TreeSet<>(shared);
            placements = new ArrayList<>(placements);
        }
    }
}
