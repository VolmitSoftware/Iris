package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Objects;

final class HydrologyTerrainCaveVoxelView implements CaveVoxelView {
    private final HydrologyTerrainSampler sampler;
    private final Long2ObjectOpenHashMap<HydrologyTerrainSample> samples;
    private final int seaLevel;
    private final int minimumY;
    private final int maximumY;

    HydrologyTerrainCaveVoxelView(HydrologyTerrainSampler sampler, int seaLevel, int worldHeight) {
        this(sampler, seaLevel, 0, worldHeight);
    }

    HydrologyTerrainCaveVoxelView(
            HydrologyTerrainSampler sampler,
            int seaLevel,
            int minimumY,
            int maximumY
    ) {
        this(sampler, seaLevel, minimumY, maximumY, new Long2ObjectOpenHashMap<>());
    }

    private HydrologyTerrainCaveVoxelView(
            HydrologyTerrainSampler sampler,
            int seaLevel,
            int minimumY,
            int maximumY,
            Long2ObjectOpenHashMap<HydrologyTerrainSample> samples
    ) {
        this.sampler = Objects.requireNonNull(sampler);
        this.samples = Objects.requireNonNull(samples);
        this.seaLevel = seaLevel;
        if (maximumY - minimumY < 3) {
            throw new IllegalArgumentException("The cave view requires at least three vertical blocks");
        }
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    HydrologyTerrainCaveVoxelView withSampleCache(
            Long2ObjectOpenHashMap<HydrologyTerrainSample> sampleCache
    ) {
        return new HydrologyTerrainCaveVoxelView(
                sampler,
                seaLevel,
                minimumY,
                maximumY,
                sampleCache
        );
    }

    @Override
    public boolean isInWorld(CavePosition position) {
        return position.y() > minimumY && position.y() < maximumY - 1;
    }

    @Override
    public CaveVoxel voxelAt(CavePosition position) {
        HydrologyTerrainSample terrain = sample(position.x(), position.z());
        if (position.y() <= terrain.naturalHeight()) {
            return CaveVoxel.SOLID;
        }
        if (terrain.ocean() && position.y() <= seaLevel) {
            return CaveVoxel.COMPATIBLE_FLUID;
        }
        return CaveVoxel.CAVE_AIR;
    }

    @Override
    public boolean isOpenToSurface(CavePosition position) {
        return isAboveTerrainSurface(position);
    }

    @Override
    public boolean isAboveTerrainSurface(CavePosition position) {
        HydrologyTerrainSample terrain = sample(position.x(), position.z());
        return position.y() > terrain.naturalHeight();
    }

    @Override
    public boolean hasAboveTerrainSurface(int x, int z, int requestedMinimumY, int requestedMaximumY) {
        int firstY = Math.max(requestedMinimumY, minimumY + 1);
        int lastY = Math.min(requestedMaximumY, maximumY - 2);
        return firstY <= lastY && lastY > sample(x, z).naturalHeight();
    }

    private HydrologyTerrainSample sample(int x, int z) {
        long key = (long) x << 32 ^ z & 0xffffffffL;
        HydrologyTerrainSample cached = samples.get(key);
        if (cached != null) {
            return cached;
        }
        HydrologyTerrainSample sampled = Objects.requireNonNull(
                sampler.sample(x, z),
                "Hydrology terrain sampler returned null at " + x + "," + z
        );
        samples.put(key, sampled);
        return sampled;
    }
}
