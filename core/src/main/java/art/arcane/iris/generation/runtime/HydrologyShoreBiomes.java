package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;

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
    private volatile IdentityHashMap<IrisBiome, IdentityHashMap<IrisRegion, Policy>> policies = new IdentityHashMap<>();
    private int policyCount;

    HydrologyShoreBiomes(IrisDimension dimension, IrisData data, long seed) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.data = data;
        this.seed = seed;
    }

    IrisBiome select(IrisBiome biome, IrisRegion region, int x, int z, int shoreDistance) {
        Policy policy = policy(biome, region);
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

    private Policy policy(IrisBiome biome, IrisRegion region) {
        IdentityHashMap<IrisRegion, Policy> regions = policies.get(biome);
        Policy known = regions == null ? null : regions.get(region);
        return known != null ? known : remember(biome, region, resolve(biome, region));
    }

    private Policy remember(IrisBiome biome, IrisRegion region, Policy resolved) {
        synchronized (policyLock) {
            IdentityHashMap<IrisRegion, Policy> current = policies.get(biome);
            Policy raced = current == null ? null : current.get(region);
            if (raced != null) {
                return raced;
            }
            boolean full = policyCount >= CAPACITY;
            IdentityHashMap<IrisBiome, IdentityHashMap<IrisRegion, Policy>> next = full ? new IdentityHashMap<>() : new IdentityHashMap<>(policies);
            IdentityHashMap<IrisRegion, Policy> regions = full || current == null ? new IdentityHashMap<>() : new IdentityHashMap<>(current);
            regions.put(region, resolved);
            next.put(biome, regions);
            policyCount = full ? 1 : policyCount + 1;
            policies = next;
            return resolved;
        }
    }

    private Policy resolve(IrisBiome biome, IrisRegion region) {
        Integer width = IrisHydrologyPolicy.inherited(biome, region, dimension, IrisHydrologyPolicy::getShoreBiomeWidth);
        List<String> keys = IrisHydrologyPolicy.inherited(biome, region, dimension, IrisHydrologyPolicy::getShoreBiomes);
        if (width == null || width <= 0 || keys == null || keys.isEmpty()) {
            return NONE;
        }
        IrisBiome[] biomes = new IrisBiome[keys.size()];
        for (int index = 0; index < biomes.length; index++) {
            biomes[index] = load(keys.get(index), biome, region).withInferredType(InferredType.SHORE);
        }
        return new Policy(biomes, width);
    }

    private IrisBiome load(String key, IrisBiome biome, IrisRegion region) {
        IrisBiome loaded = key == null || key.isBlank() || data == null ? null : data.getBiomeLoader().load(key);
        if (loaded == null) {
            throw new IllegalStateException("Hydrology shore biome '" + key + "' for biome " + (biome == null ? "none" : biome.getLoadKey())
                    + " in region " + (region == null ? "none" : region.getLoadKey()) + " does not exist");
        }
        return loaded;
    }

    private record Policy(IrisBiome[] biomes, int width) {
    }
}
