package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.interpolation.NoiseBounds;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RegionBounds {
    private final Map<IrisRegion, Constants> regions;
    private final Map<IrisBiome, NoiseBounds[]> picks;

    private RegionBounds(Map<IrisRegion, Constants> regions, Map<IrisBiome, NoiseBounds[]> picks) {
        this.regions = regions;
        this.picks = picks;
    }

    static RegionBounds compile(Collection<IrisRegion> regions, int groups, Reach reach, GroupBounds bounds) {
        IdentityHashMap<IrisRegion, Constants> compiled = new IdentityHashMap<>(Math.max(16, regions.size() * 2));
        IdentityHashMap<IrisBiome, NoiseBounds[]> picks = new IdentityHashMap<>();
        for (IrisRegion region : regions) {
            if (compiled.containsKey(region)) {
                continue;
            }
            NoiseBounds[] land = typeConstants(reach.picks(region, InferredType.LAND), groups, reach, bounds, picks);
            NoiseBounds[] sea = typeConstants(reach.picks(region, InferredType.SEA), groups, reach, bounds, picks);
            NoiseBounds[] any = new NoiseBounds[groups];
            for (int group = 0; group < groups; group++) {
                any[group] = land[group] != null && sea[group] != null && sameBits(land[group], sea[group]) ? land[group] : null;
            }
            compiled.put(region, new Constants(any, land, sea));
        }
        return new RegionBounds(compiled, picks);
    }

    Constants of(IrisRegion region) {
        return regions.get(region);
    }

    NoiseBounds pick(IrisBiome picked, int group) {
        NoiseBounds[] constants = picks.get(picked);
        return constants == null ? null : constants[group];
    }

    private static NoiseBounds[] typeConstants(List<IrisBiome> picked, int groups, Reach reach, GroupBounds bounds,
                                               Map<IrisBiome, NoiseBounds[]> picks) {
        if (picked.isEmpty()) {
            return new NoiseBounds[groups];
        }
        Set<IrisBiome> reached = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IrisBiome biome : picked) {
            Collection<IrisBiome> descendants = reach.descendants(biome);
            reached.addAll(descendants);
            if (!picks.containsKey(biome)) {
                picks.put(biome, constants(descendants, groups, bounds));
            }
        }
        return constants(reached, groups, bounds);
    }

    private static NoiseBounds[] constants(Collection<IrisBiome> biomes, int groups, GroupBounds bounds) {
        NoiseBounds[] constants = new NoiseBounds[groups];
        for (int group = 0; group < groups; group++) {
            constants[group] = constant(biomes, group, bounds);
        }
        return constants;
    }

    private static NoiseBounds constant(Collection<IrisBiome> biomes, int group, GroupBounds bounds) {
        NoiseBounds first = null;
        for (IrisBiome biome : biomes) {
            NoiseBounds next = bounds.of(group, biome);
            if (first == null) {
                first = next;
            } else if (!sameBits(first, next)) {
                return null;
            }
        }
        return first;
    }

    private static boolean sameBits(NoiseBounds first, NoiseBounds second) {
        return Double.doubleToRawLongBits(first.min()) == Double.doubleToRawLongBits(second.min())
                && Double.doubleToRawLongBits(first.max()) == Double.doubleToRawLongBits(second.max());
    }

    record Constants(NoiseBounds[] any, NoiseBounds[] land, NoiseBounds[] sea) {
        NoiseBounds any(int group) {
            return any[group];
        }

        NoiseBounds typed(InferredType type, int group) {
            return switch (type) {
                case LAND -> land[group];
                case SEA -> sea[group];
                case SHORE, CAVE -> null;
            };
        }
    }

    record Pick(IrisRegion region, InferredType type, IrisBiome biome) {
    }

    interface Reach {
        List<IrisBiome> picks(IrisRegion region, InferredType type);

        Collection<IrisBiome> descendants(IrisBiome biome);
    }

    @FunctionalInterface
    interface GroupBounds {
        NoiseBounds of(int group, IrisBiome biome);
    }
}
