package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.DataProvider;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BiomeLineages {
    private final Set<String> heirs;
    private final Object lock = new Object();
    private volatile IdentityHashMap<IrisBiome, List<IrisBiome>> roots = new IdentityHashMap<>();
    private volatile Map<Chain, List<IrisBiome>> chains = new HashMap<>();

    private BiomeLineages(Set<String> heirs) {
        this.heirs = heirs;
    }

    static BiomeLineages compile(Collection<IrisBiome> biomes, DataProvider provider) {
        Set<String> heirs = new HashSet<>();
        for (IrisBiome biome : biomes) {
            if (biome.getHydrologyPolicy() != null) {
                addDescendants(biome, provider, heirs);
            }
        }
        return new BiomeLineages(heirs);
    }

    boolean inherits(IrisBiome biome) {
        return heirs.contains(biome.getLoadKey());
    }

    List<IrisBiome> root(IrisBiome biome) {
        List<IrisBiome> known = roots.get(biome);
        if (known != null) {
            return known;
        }
        synchronized (lock) {
            List<IrisBiome> raced = roots.get(biome);
            if (raced != null) {
                return raced;
            }
            List<IrisBiome> created = List.of(biome);
            IdentityHashMap<IrisBiome, List<IrisBiome>> next = new IdentityHashMap<>(roots);
            next.put(biome, created);
            roots = next;
            return created;
        }
    }

    List<IrisBiome> chain(IrisBiome[] leafFirst) {
        if (leafFirst.length == 1) {
            return root(leafFirst[0]);
        }
        Chain key = new Chain(leafFirst);
        List<IrisBiome> known = chains.get(key);
        if (known != null) {
            return known;
        }
        synchronized (lock) {
            List<IrisBiome> raced = chains.get(key);
            if (raced != null) {
                return raced;
            }
            List<IrisBiome> created = List.of(leafFirst);
            Map<Chain, List<IrisBiome>> next = new HashMap<>(chains);
            next.put(key, created);
            chains = next;
            return created;
        }
    }

    private static void addDescendants(IrisBiome parent, DataProvider provider, Set<String> heirs) {
        Set<IrisBiome> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<IrisBiome> pending = new ArrayDeque<>();
        addChildren(parent, provider, pending);
        while (!pending.isEmpty()) {
            IrisBiome child = pending.poll();
            if (visited.add(child)) {
                heirs.add(child.getLoadKey());
                addChildren(child, provider, pending);
            }
        }
    }

    private static void addChildren(IrisBiome parent, DataProvider provider, ArrayDeque<IrisBiome> pending) {
        for (IrisBiome child : parent.getRealChildren(provider)) {
            if (child != null) {
                pending.add(child);
            }
        }
    }

    private record Chain(IrisBiome[] biomes) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Chain chain) || chain.biomes.length != biomes.length) {
                return false;
            }
            for (int index = 0; index < biomes.length; index++) {
                if (chain.biomes[index] != biomes[index]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            for (IrisBiome biome : biomes) {
                hash = 31 * hash + System.identityHashCode(biome);
            }
            return hash;
        }
    }
}
