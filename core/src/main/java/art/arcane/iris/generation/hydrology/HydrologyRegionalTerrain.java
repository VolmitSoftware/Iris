package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.cache.CacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

final class HydrologyRegionalTerrain implements HydrologyTerrainSampler {
    private static final int MAXIMUM_CACHED_SAMPLES = 4194304;
    private static final int[][] CARDINAL = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private final HydrologyPlanner planner;
    private final HydrologyCacheBudget budget;
    private volatile Cache<Long, Terrain> terrain;

    HydrologyRegionalTerrain(HydrologyPlanner planner) {
        this.planner = planner;
        this.budget = HydrologyCacheBudget.runtime(planner.settings.routing().regional().enabled());
        this.terrain = cache();
    }

    void clear() {
        terrain = cache();
    }

    @Override
    public HydrologyTerrainSample sample(int x, int z) {
        Cache<Long, Terrain> active = terrain;
        long key = CacheKey.mix(RiverFootprint.pack(x, z));
        Terrain cached = active.getIfPresent(key);
        if (cached != null) {
            return cached.sample();
        }
        Terrain sampled = new Terrain(planner.naturalSampler == null
                ? planner.sampler.sample(x, z) : planner.naturalSampler.sampleBasisWithoutSlope(x, z));
        Terrain previous = active.asMap().putIfAbsent(key, sampled);
        return (previous == null ? sampled : previous).sample();
    }

    boolean receivingTerminal(HydrologyPoint point, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        HydrologyTerrainSample land = sample(point.x(), point.z());
        if (land == null || land.ocean() || land.naturalHeight() < planner.settings.seaLevel() || !land.outletAllowed()) {
            return false;
        }
        for (int index = 0; index < 4; index++) {
            int[] offset = CARDINAL[index];
            HydrologyTerrainSample ocean = sample(point.x() + offset[0], point.z() + offset[1]);
            if (ocean != null && receiver.receivingWater(point.x() + offset[0], point.z() + offset[1], planner.settings.seaLevel())
                    && ocean.preferredProfileKeys().contains(profile) && land.drainsInto(ocean)
                    && (!coastal || ocean.drainsInto(land))) {
                return true;
            }
        }
        return false;
    }

    private Cache<Long, Terrain> cache() {
        return Caffeine.newBuilder()
                .maximumWeight(budget.regionalTerrainBytes())
                .weigher((Long key, Terrain value) -> HydrologyCacheWeights.bounded(
                        HydrologyCacheWeights.terrain(value.sample()), budget.regionalTerrainBytes(),
                        (int) Math.max(65536L, Math.min(MAXIMUM_CACHED_SAMPLES, Runtime.getRuntime().maxMemory() / 8192L))))
                .build();
    }

    private record Terrain(HydrologyTerrainSample sample) {
    }
}
