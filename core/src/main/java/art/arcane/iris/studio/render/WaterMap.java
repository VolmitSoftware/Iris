package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.world.history.ChunkGenerationSemantics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class WaterMap {
    static final double RASTER_STEP = 2D;
    static final double MAX_STEP = 16D;
    static final List<HydrologyFeatureType> DRAWN = List.of(HydrologyFeatureType.WATERFALL, HydrologyFeatureType.CASCADE,
            HydrologyFeatureType.MOUTH, HydrologyFeatureType.MAJOR_RIVER, HydrologyFeatureType.RIVER, HydrologyFeatureType.STREAM,
            HydrologyFeatureType.SEA_INLET, HydrologyFeatureType.LAKE, HydrologyFeatureType.POND, HydrologyFeatureType.DAM,
            HydrologyFeatureType.CAVE_POOL);
    private static final int DRY = Integer.MAX_VALUE;
    private static final int[] RANK = ranks();

    private final HydrologyRuntime runtime;
    private final int resolution;
    private final double startX;
    private final double startZ;
    private final double step;
    private final BooleanSupplier cancelled;
    private final int[] ranks;
    private final IdentityHashMap<HydrologyTier, HydrologyFeatureType> channels = new IdentityHashMap<>();
    private boolean provisional;

    private WaterMap(HydrologyRuntime runtime, int resolution, double startX, double startZ, double step, BooleanSupplier cancelled) {
        this.runtime = runtime;
        this.resolution = resolution;
        this.startX = startX;
        this.startZ = startZ;
        this.step = step;
        this.cancelled = cancelled;
        this.ranks = new int[resolution * resolution];
        Arrays.fill(ranks, DRY);
    }

    static WaterMap draw(HydrologyRuntime runtime, int resolution, double startX, double startZ, double step, BooleanSupplier cancelled) {
        WaterMap map = new WaterMap(runtime, resolution, startX, startZ, step, cancelled);
        HydrologyPlanPreview plans = runtime.planPreview((int) Math.floor(startX), (int) Math.floor(startZ),
                (int) Math.ceil(startX + resolution * step) - 1, (int) Math.ceil(startZ + resolution * step) - 1);
        map.provisional = !plans.complete();
        if (step <= RASTER_STEP) {
            map.drawRasters(plans);
        } else {
            map.drawPlans(plans);
        }
        return map;
    }

    HydrologyFeatureType at(int pixelX, int pixelZ) {
        int rank = ranks[pixelZ * resolution + pixelX];
        return rank == DRY ? null : DRAWN.get(rank);
    }

    boolean provisional() {
        return provisional;
    }

    void drawCavePools(List<ChunkGenerationSemantics.BlockPosition> pools) {
        for (ChunkGenerationSemantics.BlockPosition pool : pools) {
            int pixelX = (int) Math.floor((pool.x() - startX) / step);
            int pixelZ = (int) Math.floor((pool.z() - startZ) / step);
            if (pixelX >= 0 && pixelX < resolution && pixelZ >= 0 && pixelZ < resolution) {
                stamp(pixelX, pixelZ, HydrologyFeatureType.CAVE_POOL);
            }
        }
    }

    private void drawRasters(HydrologyPlanPreview plans) {
        Set<Long> lakes = new HashSet<>();
        for (Pond lake : plans.lakes()) {
            lakes.add(lake.id());
        }
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        WaterMap planned = null;
        for (int pixelZ = 0; pixelZ < resolution; pixelZ++) {
            checkCancelled();
            int minimumZ = (int) Math.floor(startZ + pixelZ * step);
            int maximumZ = Math.max(minimumZ, (int) Math.ceil(startZ + (pixelZ + 1) * step) - 1);
            for (int pixelX = 0; pixelX < resolution; pixelX++) {
                int minimumX = (int) Math.floor(startX + pixelX * step);
                int maximumX = Math.max(minimumX, (int) Math.ceil(startX + (pixelX + 1) * step) - 1);
                for (int z = minimumZ; z <= maximumZ; z++) {
                    for (int x = minimumX; x <= maximumX; x++) {
                        ChunkWaterRaster raster = raster(rasters, x >> 4, z >> 4);
                        if (raster.provisional()) {
                            provisional = true;
                            if (planned == null) {
                                planned = new WaterMap(runtime, resolution, startX, startZ, step, cancelled);
                                planned.drawPlans(plans);
                            }
                            stamp(pixelX, pixelZ, planned.at(pixelX, pixelZ));
                        } else if (!raster.empty()) {
                            stamp(pixelX, pixelZ, columnType(raster, x & 15, z & 15, lakes));
                        }
                    }
                }
            }
        }
    }

    private ChunkWaterRaster raster(Map<Long, ChunkWaterRaster> rasters, int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        ChunkWaterRaster raster = rasters.get(key);
        if (raster == null) {
            raster = runtime.rasterPreview(chunkX, chunkZ);
            rasters.put(key, raster);
        }
        return raster;
    }

    private HydrologyFeatureType columnType(ChunkWaterRaster raster, int localX, int localZ, Set<Long> lakes) {
        return switch (raster.role(localX, localZ)) {
            case CHANNEL -> channel(raster.tier(localX, localZ));
            case CASCADE -> HydrologyFeatureType.CASCADE;
            case WATERFALL -> HydrologyFeatureType.WATERFALL;
            case MOUTH -> HydrologyFeatureType.MOUTH;
            case POND -> lakes.contains(raster.featureId(localX, localZ)) ? HydrologyFeatureType.LAKE : HydrologyFeatureType.POND;
            case DAM -> HydrologyFeatureType.DAM;
            case INLET -> HydrologyFeatureType.SEA_INLET;
            case NONE, BANK, SHORE, POND_SHORE -> null;
        };
    }

    private void drawPlans(HydrologyPlanPreview plans) {
        for (Reach reach : plans.reaches()) {
            checkCancelled();
            drawReach(reach);
        }
        for (Pond pond : plans.ponds()) {
            drawMask(pond.mask(), HydrologyFeatureType.POND);
        }
        for (Pond lake : plans.lakes()) {
            drawMask(lake.mask(), HydrologyFeatureType.LAKE);
        }
        for (SeaInlet inlet : plans.inlets()) {
            checkCancelled();
            drawInlet(inlet);
        }
    }

    private void drawInlet(SeaInlet inlet) {
        float[] xs = inlet.xs();
        float[] zs = inlet.zs();
        float[] halfWidth = inlet.halfWidth();
        for (int station = 0; station + 1 < xs.length; station++) {
            double radius = Math.max(halfWidth[station], halfWidth[station + 1]) + step / 2D;
            drawSegment(xs[station], zs[station], xs[station + 1], zs[station + 1], radius, HydrologyFeatureType.SEA_INLET);
        }
        drawSegment(inlet.chamberX(), inlet.chamberZ(), inlet.chamberX(), inlet.chamberZ(), inlet.chamberRadius() + step / 2D,
                HydrologyFeatureType.SEA_INLET);
    }

    private void drawReach(Reach reach) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        float[] halfWidth = reach.halfWidth();
        HydrologyFeatureType channel = channel(reach.tier());
        for (int point = 0; point < xs.length; point++) {
            int next = Math.min(point + 1, xs.length - 1);
            HydrologyFeatureType type = switch (reach.style()[point]) {
                case CASCADE -> HydrologyFeatureType.CASCADE;
                case WATERFALL -> HydrologyFeatureType.WATERFALL;
                case MOUTH -> HydrologyFeatureType.MOUTH;
                case RUN, RIFFLE, POND_OUTLET -> channel;
            };
            double radius = Math.max(halfWidth[point], halfWidth[next]) + step / 2D;
            drawSegment(xs[point], zs[point], xs[next], zs[next], radius, type);
        }
    }

    private void drawSegment(double fromX, double fromZ, double toX, double toZ, double radius, HydrologyFeatureType type) {
        int firstX = Math.max(0, (int) Math.floor((Math.min(fromX, toX) - radius - startX) / step));
        int lastX = Math.min(resolution - 1, (int) Math.floor((Math.max(fromX, toX) + radius - startX) / step));
        int firstZ = Math.max(0, (int) Math.floor((Math.min(fromZ, toZ) - radius - startZ) / step));
        int lastZ = Math.min(resolution - 1, (int) Math.floor((Math.max(fromZ, toZ) + radius - startZ) / step));
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double length = dx * dx + dz * dz;
        double reach = radius * radius;
        for (int pixelZ = firstZ; pixelZ <= lastZ; pixelZ++) {
            double centreZ = startZ + (pixelZ + 0.5D) * step;
            for (int pixelX = firstX; pixelX <= lastX; pixelX++) {
                double centreX = startX + (pixelX + 0.5D) * step;
                double t = length == 0D ? 0D : Math.clamp(((centreX - fromX) * dx + (centreZ - fromZ) * dz) / length, 0D, 1D);
                double offX = centreX - (fromX + t * dx);
                double offZ = centreZ - (fromZ + t * dz);
                if (offX * offX + offZ * offZ <= reach) {
                    stamp(pixelX, pixelZ, type);
                }
            }
        }
    }

    private void drawMask(PondMask mask, HydrologyFeatureType type) {
        int half = mask.step() >> 1;
        for (int j = 0; j < mask.depth(); j++) {
            int centreZ = mask.originZ() + j * mask.step();
            int firstZ = Math.max(0, (int) Math.floor((centreZ - half - startZ) / step));
            int lastZ = Math.min(resolution - 1, (int) Math.floor((centreZ + half - 1 - startZ) / step));
            if (firstZ > lastZ) {
                continue;
            }
            for (int i = 0; i < mask.width(); i++) {
                if (!mask.node(i, j)) {
                    continue;
                }
                int centreX = mask.originX() + i * mask.step();
                int firstX = Math.max(0, (int) Math.floor((centreX - half - startX) / step));
                int lastX = Math.min(resolution - 1, (int) Math.floor((centreX + half - 1 - startX) / step));
                for (int pixelZ = firstZ; pixelZ <= lastZ; pixelZ++) {
                    for (int pixelX = firstX; pixelX <= lastX; pixelX++) {
                        ranks[pixelZ * resolution + pixelX] = RANK[type.ordinal()];
                    }
                }
            }
        }
    }

    private HydrologyFeatureType channel(HydrologyTier tier) {
        return channels.computeIfAbsent(tier, runtime::channelType);
    }

    private void stamp(int pixelX, int pixelZ, HydrologyFeatureType type) {
        if (type == null) {
            return;
        }
        int index = pixelZ * resolution + pixelX;
        int rank = RANK[type.ordinal()];
        if (rank < ranks[index]) {
            ranks[index] = rank;
        }
    }

    private void checkCancelled() {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Vision render was cancelled");
        }
    }

    private static int[] ranks() {
        int[] ranks = new int[HydrologyFeatureType.values().length];
        Arrays.fill(ranks, DRY);
        for (int index = 0; index < DRAWN.size(); index++) {
            ranks[DRAWN.get(index).ordinal()] = index;
        }
        return ranks;
    }
}
