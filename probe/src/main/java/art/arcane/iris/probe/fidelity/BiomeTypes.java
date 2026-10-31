package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class BiomeTypes {
    private static final String LAND = "land";
    private static final String SEA = "sea";
    private static final String SHORE = "shore";
    private static final String JOINER = "+";

    private BiomeTypes() {
    }

    public static SortedMap<String, String> of(List<IrisRegion> regions, Function<String, IrisBiome> biomes) {
        SortedMap<String, SortedSet<String>> types = new TreeMap<>();
        for (IrisRegion region : regions) {
            visit(region.getLandBiomes(), LAND, biomes, types);
            visit(region.getSeaBiomes(), SEA, biomes, types);
            visit(region.getShoreBiomes(), SHORE, biomes, types);
        }
        SortedMap<String, String> joined = new TreeMap<>();
        for (Map.Entry<String, SortedSet<String>> entry : types.entrySet()) {
            joined.put(entry.getKey(), String.join(JOINER, entry.getValue()));
        }
        return Collections.unmodifiableSortedMap(joined);
    }

    private static void visit(List<String> roots, String type, Function<String, IrisBiome> biomes, SortedMap<String, SortedSet<String>> types) {
        Deque<String> pending = new ArrayDeque<>(roots);
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String key = pending.pop();
            if (!seen.add(key)) {
                continue;
            }
            types.computeIfAbsent(key, ignored -> new TreeSet<>()).add(type);
            IrisBiome biome = biomes.apply(key);
            if (biome != null) {
                pending.addAll(biome.getChildren());
            }
        }
    }
}
