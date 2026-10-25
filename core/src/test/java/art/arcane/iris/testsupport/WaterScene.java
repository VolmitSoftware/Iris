package art.arcane.iris.testsupport;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.ReachIndex;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

public final class WaterScene {
    public static final int SEA_LEVEL = 40;
    private static final long SEED = 1337L;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private final IntBinaryOperator natural;
    private final ChunkRasterizer rasterizer;
    private final List<Reach> reaches = new ArrayList<>();
    private final List<Pond> ponds = new ArrayList<>();
    private final Map<WindowKey, IndexedWindow> windows = new HashMap<>();
    private final Map<Long, ChunkWaterRaster> rasters = new HashMap<>();

    public WaterScene(IrisHydrology config, IntBinaryOperator natural) {
        this.natural = natural;
        this.rasterizer = new ChunkRasterizer(HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y));
    }

    public WaterScene reach(Reach reach) {
        reaches.add(reach);
        return this;
    }

    public WaterScene pond(Pond pond) {
        ponds.add(pond);
        return this;
    }

    public ChunkWaterRaster raster(int chunkX, int chunkZ) {
        return rasters.computeIfAbsent(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL),
                key -> rasterizer.rasterize(chunkX, chunkZ, natural, windows(chunkX, chunkZ), regions(chunkX, chunkZ)));
    }

    public int terrain(int x, int z) {
        ChunkWaterRaster raster = at(x, z);
        return raster.empty() ? natural.applyAsInt(x, z) : raster.terrainHeight(x & 15, z & 15);
    }

    public int head(int x, int z) {
        return at(x, z).waterHead(x & 15, z & 15);
    }

    public boolean water(int x, int z) {
        return head(x, z) != Integer.MIN_VALUE;
    }

    public int caveCeiling(int x, int z) {
        return at(x, z).caveCeiling(x & 15, z & 15);
    }

    public static HydrologyTier stream() {
        return tier("stream", 1.5f, 0.5f);
    }

    public static HydrologyTier tier(String name, float halfWidth, float depthRatio) {
        return new HydrologyTier(name, 0f, halfWidth, halfWidth, 0f, depthRatio, 1, true, 6, 0, IrisBankStyle.SMOOTH, false,
                new IrisHydrologyMaterials(), "water", List.of());
    }

    public static Reach line(long id, HydrologyTier tier, int startX, int startZ, int endX, int endZ, IntBinaryOperator grade) {
        int points = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ)) + 1;
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grades = new int[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            float t = points == 1 ? 0f : point / (float) (points - 1);
            xs[point] = startX + (endX - startX) * t;
            zs[point] = startZ + (endZ - startZ) * t;
            grades[point] = grade.applyAsInt(Math.round(xs[point]), Math.round(zs[point]));
            halfWidth[point] = tier.minHalfWidth();
            depth[point] = tier.depth(tier.minHalfWidth());
            int drop = point == 0 ? 0 : grades[point - 1] - grades[point];
            style[point] = drop >= tier.waterfallMinimumDrop() ? ReachStyle.WATERFALL
                    : drop >= 2 ? ReachStyle.CASCADE : drop == 1 ? ReachStyle.RIFFLE : ReachStyle.RUN;
        }
        return new Reach(id, tier, 0L, xs, zs, grades, halfWidth, depth, style, 1000f);
    }

    public static Pond pond(long id, int level, int centreX, int centreZ, int radius) {
        int step = 4;
        int originX = Math.floorDiv(centreX - radius, step) * step - step;
        int originZ = Math.floorDiv(centreZ - radius, step) * step - step;
        int width = (centreX + radius - originX) / step + 2;
        int depth = (centreZ + radius - originZ) / step + 2;
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                if (Math.hypot(originX + i * step - centreX, originZ + j * step - centreZ) <= radius) {
                    int index = j * width + i;
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        PondMask mask = new PondMask(originX, originZ, step, width, depth, bits);
        return new Pond(id, PondKind.NATURAL, level, level - 8, centreX, centreZ, level + 1.5f, mask, 0L, 0, "water", centreX, centreZ);
    }

    private ChunkWaterRaster at(int x, int z) {
        return raster(x >> 4, z >> 4);
    }

    private List<IndexedWindow> windows(int chunkX, int chunkZ) {
        WindowKey own = WindowKey.containing(chunkX * 16, chunkZ * 16);
        List<IndexedWindow> around = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                around.add(window(new WindowKey(own.wx() + dx, own.wz() + dz)));
            }
        }
        return around;
    }

    private IndexedWindow window(WindowKey key) {
        return windows.computeIfAbsent(key, ignored -> {
            List<Reach> owned = new ArrayList<>();
            for (Reach reach : reaches) {
                int last = reach.xs().length - 1;
                if (key.equals(WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])))) {
                    owned.add(reach);
                }
            }
            List<Pond> ownedPonds = new ArrayList<>();
            for (Pond pond : ponds) {
                if (key.equals(WindowKey.containing(pond.floorX(), pond.floorZ()))) {
                    ownedPonds.add(pond);
                }
            }
            return new IndexedWindow(new WindowPlan(key, owned, ownedPonds, List.of()), ReachIndex.build(owned, ownedPonds));
        });
    }

    private static List<RegionPlan> regions(int chunkX, int chunkZ) {
        List<RegionPlan> plans = new ArrayList<>();
        for (RegionKey key : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
            plans.add(new RegionPlan(key, List.of(), List.of()));
        }
        return plans;
    }
}
