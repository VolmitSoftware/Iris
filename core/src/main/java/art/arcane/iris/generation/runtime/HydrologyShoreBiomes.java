package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

final class HydrologyShoreBiomes {
    private static final int CAPACITY = 4096;
    private static final int PATCH = 64;
    private static final long SALT = 0x53484f5245L;
    private static final Policy NONE = new Policy(new IrisBiome[0], 0);

    private final IrisDimension dimension;
    private final IrisData data;
    private final long seed;
    private final Object policyLock = new Object();
    private volatile IdentityHashMap<List<IrisBiome>, IdentityHashMap<IrisRegion, Policy>> policies = new IdentityHashMap<>();
    private int policyCount;

    HydrologyShoreBiomes(IrisDimension dimension, IrisData data, long seed) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.data = data;
        this.seed = seed;
    }

    static boolean banded(IrisDimension dimension, Collection<IrisRegion> regions, Collection<IrisBiome> biomes) {
        if (banded(dimension.getHydrologyPolicy())) {
            return true;
        }
        for (IrisRegion region : regions) {
            if (region != null && banded(region.getHydrologyPolicy())) {
                return true;
            }
        }
        for (IrisBiome biome : biomes) {
            if (biome != null && banded(biome.getHydrologyPolicy())) {
                return true;
            }
        }
        return false;
    }

    IrisBiome select(List<IrisBiome> lineage, IrisRegion region, int x, int z, int shoreDistance) {
        Policy policy = policy(lineage, region);
        IrisBiome[] biomes = policy.biomes();
        if (shoreDistance > policy.width() || biomes.length == 0) {
            return null;
        }
        if (biomes.length == 1) {
            return biomes[0];
        }
        long patch = CellHash.mix(seed, SALT, Math.floorDiv(x, PATCH), Math.floorDiv(z, PATCH));
        return biomes[(int) Long.remainderUnsigned(patch, biomes.length)];
    }

    private Policy policy(List<IrisBiome> lineage, IrisRegion region) {
        IdentityHashMap<IrisRegion, Policy> regions = policies.get(lineage);
        Policy known = regions == null ? null : regions.get(region);
        return known != null ? known : remember(lineage, region, resolve(lineage, region));
    }

    private Policy remember(List<IrisBiome> lineage, IrisRegion region, Policy resolved) {
        synchronized (policyLock) {
            IdentityHashMap<IrisRegion, Policy> current = policies.get(lineage);
            Policy raced = current == null ? null : current.get(region);
            if (raced != null) {
                return raced;
            }
            boolean full = policyCount >= CAPACITY;
            IdentityHashMap<List<IrisBiome>, IdentityHashMap<IrisRegion, Policy>> next = full ? new IdentityHashMap<>() : new IdentityHashMap<>(policies);
            IdentityHashMap<IrisRegion, Policy> regions = full || current == null ? new IdentityHashMap<>() : new IdentityHashMap<>(current);
            regions.put(region, resolved);
            next.put(lineage, regions);
            policyCount = full ? 1 : policyCount + 1;
            policies = next;
            return resolved;
        }
    }

    private Policy resolve(List<IrisBiome> lineage, IrisRegion region) {
        Integer width = IrisHydrologyPolicy.inherited(lineage, region, dimension, IrisHydrologyPolicy::getShoreBiomeWidth);
        List<String> keys = IrisHydrologyPolicy.inherited(lineage, region, dimension, IrisHydrologyPolicy::getShoreBiomes);
        if (width == null || width <= 0 || keys == null || keys.isEmpty()) {
            return NONE;
        }
        IrisBiome[] biomes = new IrisBiome[keys.size()];
        for (int index = 0; index < biomes.length; index++) {
            biomes[index] = load(keys.get(index), lineage, region).withInferredType(InferredType.SHORE);
        }
        return new Policy(biomes, width);
    }

    private IrisBiome load(String key, List<IrisBiome> lineage, IrisRegion region) {
        IrisBiome loaded = key == null || key.isBlank() || data == null ? null : data.getBiomeLoader().load(key);
        if (loaded == null) {
            throw new IllegalStateException("Hydrology shore biome '" + key + "' listed by " + listedBy(lineage, region) + " for biome "
                    + (lineage.isEmpty() ? "none" : lineage.getFirst().getLoadKey()) + " in region " + (region == null ? "none" : region.getLoadKey())
                    + " does not exist");
        }
        return loaded;
    }

    private String listedBy(List<IrisBiome> lineage, IrisRegion region) {
        for (IrisBiome biome : lineage) {
            if (lists(biome.getHydrologyPolicy())) {
                return "biome " + biome.getLoadKey();
            }
        }
        return region != null && lists(region.getHydrologyPolicy()) ? "region " + region.getLoadKey() : "dimension " + dimension.getLoadKey();
    }

    private static boolean lists(IrisHydrologyPolicy policy) {
        return policy != null && policy.getShoreBiomes() != null;
    }

    private static boolean banded(IrisHydrologyPolicy policy) {
        return policy != null && policy.getShoreBiomeWidth() != null && policy.getShoreBiomeWidth() > 0;
    }

    private record Policy(IrisBiome[] biomes, int width) {
    }
}
