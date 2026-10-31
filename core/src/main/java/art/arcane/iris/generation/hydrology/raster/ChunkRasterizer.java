package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;

import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public final class ChunkRasterizer {
    private static final int CHUNK = 16;
    private static final int CANDIDATE_HALO = 48;

    private final HydrologySettings settings;
    private final int halo;

    public ChunkRasterizer(HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        float widest = 0f;
        for (HydrologyTier tier : settings.tiers()) {
            widest = Math.max(widest, tier.maxHalfWidth());
        }
        int reach = 1 + Math.max(ChunkWaterRaster.MAX_SHORE_DISTANCE, settings.ponds().shore().taper()) + (int) Math.ceil(widest);
        this.halo = Math.max(CANDIDATE_HALO, reach);
    }

    public static List<RegionKey> lakeRegions(int chunkX, int chunkZ) {
        int halo = RiffleProfile.RADIUS + 1;
        return RegionKey.lakeOwners(chunkX * CHUNK - halo, chunkZ * CHUNK - halo,
                chunkX * CHUNK + CHUNK - 1 + halo, chunkZ * CHUNK + CHUNK - 1 + halo);
    }

    /** The fluid an area's hydrology policy names for the water flowing through it, or null to keep the tier's fluid. */
    @FunctionalInterface
    public interface AreaFluid {
        AreaFluid NONE = (x, z) -> null;

        String at(int x, int z);
    }

    public ChunkWaterRaster rasterize(int chunkX, int chunkZ, IntBinaryOperator naturalHeight, List<IndexedWindow> windows,
                                      List<RegionPlan> lakeRegionPlans) {
        return rasterize(chunkX, chunkZ, naturalHeight, windows, lakeRegionPlans, AreaFluid.NONE);
    }

    public ChunkWaterRaster rasterize(int chunkX, int chunkZ, IntBinaryOperator naturalHeight, List<IndexedWindow> windows,
                                      List<RegionPlan> lakeRegionPlans, AreaFluid areaFluid) {
        Objects.requireNonNull(naturalHeight, "naturalHeight");
        ChunkFeatures features = ChunkFeatures.gather(settings, chunkX, chunkZ, halo, windows, lakeRegionPlans, lakeRegions(chunkX, chunkZ));
        if (features.empty()) {
            return ChunkWaterRaster.EMPTY;
        }
        return new RasterComposer(settings, features, chunkX, chunkZ, areaFluid).compose(naturalHeight);
    }
}
