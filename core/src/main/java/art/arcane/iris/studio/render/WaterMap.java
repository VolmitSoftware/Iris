package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
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
    static final List<HydrologyFeatureType> DRAWN = drawn();
    private static final int DRY = Integer.MAX_VALUE;

    private final HydrologyRuntime runtime;
    private final int resolution;
    private final double startX;
    private final double startZ;
    private final double step;
    private List<Grotto> grottos = List.of();
    private final int[] ranks;
    private final boolean[] resolved;
    private final IdentityHashMap<HydrologyTier, HydrologyFeatureType> channels = new IdentityHashMap<>();
    private final Set<Long> lakes = new HashSet<>();
    private BooleanSupplier cancelled;
    private int[] plannedRanks;
    private boolean plansComplete;
    private boolean provisional;

    WaterMap(HydrologyRuntime runtime, int resolution, double startX, double startZ, double step) {
        this.runtime = runtime;
        this.resolution = resolution;
        this.startX = startX;
        this.startZ = startZ;
        this.step = step;
        this.ranks = new int[resolution * resolution];
        this.resolved = step <= RASTER_STEP ? new boolean[ranks.length] : null;
        Arrays.fill(ranks, DRY);
        provisional = true;
    }

    void advance(BooleanSupplier cancelled) {
        if (!provisional) {
            return;
        }
        this.cancelled = cancelled;
        HydrologyPlanPreview plans = null;
        if (!plansComplete) {
            plans = preview();
            plannedRanks = null;
            lakes.clear();
            for (Pond lake : plans.lakes()) {
                lakes.add(lake.id());
            }
            if (step > RASTER_STEP) {
                prepareFallback(plans);
            }
            plansComplete = plans.complete();
            grottos = plans.grottos();
        }
        if (step <= RASTER_STEP) {
            provisional = drawRasters(plans) || !plansComplete;
            drawGrottos();
        } else {
            prepareFallback(plans);
            System.arraycopy(plannedRanks, 0, ranks, 0, ranks.length);
            provisional = !plansComplete;
        }
        if (!provisional) {
            plannedRanks = null;
            lakes.clear();
        }
    }

    private HydrologyPlanPreview preview() {
        return runtime.planPreview((int) Math.floor(startX), (int) Math.floor(startZ),
                (int) Math.ceil(startX + resolution * step) - 1, (int) Math.ceil(startZ + resolution * step) - 1);
    }

    private void prepareFallback(HydrologyPlanPreview plans) {
        if (plannedRanks != null) {
            return;
        }
        WaterMap planned = new WaterMap(runtime, resolution, startX, startZ, step);
        planned.cancelled = cancelled;
        planned.drawPlans(plans == null ? preview() : plans);
        plannedRanks = planned.ranks;
    }

    long bytes() {
        return (long) ranks.length * Integer.BYTES + (resolved == null ? 0L : resolved.length)
                + (plannedRanks == null ? 0L : (long) plannedRanks.length * Integer.BYTES)
                + (long) lakes.size() * 64L;
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

    private boolean drawRasters(HydrologyPlanPreview plans) {
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        boolean pending = false;
        for (int pixelZ = 0; pixelZ < resolution; pixelZ++) {
            checkCancelled();
            int minimumZ = (int) Math.floor(startZ + pixelZ * step);
            int maximumZ = Math.max(minimumZ, (int) Math.ceil(startZ + (pixelZ + 1) * step) - 1);
            for (int pixelX = 0; pixelX < resolution; pixelX++) {
                int index = pixelZ * resolution + pixelX;
                if (resolved[index]) {
                    continue;
                }
                ranks[index] = DRY;
                boolean ready = plansComplete;
                int minimumX = (int) Math.floor(startX + pixelX * step);
                int maximumX = Math.max(minimumX, (int) Math.ceil(startX + (pixelX + 1) * step) - 1);
                for (int z = minimumZ; z <= maximumZ; z++) {
                    for (int x = minimumX; x <= maximumX; x++) {
                        ChunkWaterRaster raster = raster(rasters, x >> 4, z >> 4);
                        if (raster.provisional()) {
                            pending = true;
                            ready = false;
                            prepareFallback(plans);
                            ranks[index] = Math.min(ranks[index], plannedRanks[index]);
                        } else if (!raster.empty()) {
                            stamp(pixelX, pixelZ, columnType(raster, x & 15, z & 15, lakes));
                        }
                    }
                }
                resolved[index] = ready;
            }
        }
        return pending;
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
        grottos = plans.grottos();
        drawGrottos();
    }

    private void drawGrottos() {
        for (Grotto grotto : grottos) {
            checkCancelled();
            float[] xs = grotto.xs();
            float[] zs = grotto.zs();
            float[] halfWidth = grotto.halfWidth();
            for (int point = 0; point + 1 < xs.length; point++) {
                double radius = Math.max(halfWidth[point], halfWidth[point + 1]) + step / 2D;
                drawSegment(xs[point], zs[point], xs[point + 1], zs[point + 1], radius, HydrologyFeatureType.GROTTO);
            }
        }
    }

    private void drawReach(Reach reach) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        float[] halfWidth = reach.halfWidth();
        HydrologyFeatureType channel = reach.style()[0] == ReachStyle.TIDAL ? HydrologyFeatureType.SEA_INLET : channel(reach.tier());
        for (int point = 0; point < xs.length; point++) {
            int next = Math.min(point + 1, xs.length - 1);
            HydrologyFeatureType type = switch (reach.style()[point]) {
                case CASCADE -> HydrologyFeatureType.CASCADE;
                case WATERFALL -> HydrologyFeatureType.WATERFALL;
                case MOUTH -> HydrologyFeatureType.MOUTH;
                case RUN, RIFFLE, POND_OUTLET, TIDAL -> channel;
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
                        stamp(pixelX, pixelZ, type);
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
        int rank = rank(type);
        if (rank < ranks[index]) {
            ranks[index] = rank;
        }
    }

    private void checkCancelled() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Vision render was cancelled");
        }
    }

    private static int rank(HydrologyFeatureType type) {
        return switch (type) {
            case WATERFALL -> 0;
            case CASCADE -> 1;
            case MOUTH -> 2;
            case MAJOR_RIVER -> 3;
            case RIVER -> 4;
            case STREAM -> 5;
            case SEA_INLET -> 6;
            case GROTTO -> 7;
            case POND -> 8;
            case LAKE -> 9;
            case DAM -> 10;
            case CAVE_POOL -> 11;
        };
    }

    private static List<HydrologyFeatureType> drawn() {
        HydrologyFeatureType[] drawn = new HydrologyFeatureType[HydrologyFeatureType.values().length];
        for (HydrologyFeatureType type : HydrologyFeatureType.values()) {
            int rank = rank(type);
            if (drawn[rank] != null) {
                throw new IllegalStateException("Water map ranks " + drawn[rank] + " and " + type + " equally");
            }
            drawn[rank] = type;
        }
        return List.of(drawn);
    }
}
