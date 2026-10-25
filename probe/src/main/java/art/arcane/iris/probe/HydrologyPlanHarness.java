package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanners;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCodec;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;
import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.ReachIndex;
import art.arcane.iris.generation.hydrology.water.Pond;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;

final class HydrologyPlanHarness {
    private static final double TILE_AREA = 1024D * 1024D;
    private static final Comparator<RegionKey> REGION_ORDER = Comparator.comparingInt(RegionKey::rz).thenComparingInt(RegionKey::rx);

    private HydrologyPlanHarness() {
    }

    static final class Plans {
        private final HydrologySettings settings;
        private final IntBinaryOperator naturalHeight;
        private final HydrologyWindows.WindowRange planned;
        private final CountingTerrain counting;
        private final HydrologyPlanners planners;
        private final ChunkRasterizer rasterizer;
        private final Map<RegionKey, RegionPlan> regions = new HashMap<>();
        private final Map<RegionKey, Double> regionCpu = new HashMap<>();
        private final Map<WindowKey, IndexedWindow> windows = new HashMap<>();
        private final Map<WindowKey, Double> windowCpu = new HashMap<>();
        private final List<String> failures = new ArrayList<>();

        Plans(HydrologySettings settings, HydrologyTerrainSource terrain, IntBinaryOperator naturalHeight, HydrologyWindows.WindowRange range) {
            this.settings = settings;
            this.naturalHeight = naturalHeight;
            this.planned = range.grown(1);
            this.counting = new CountingTerrain(terrain);
            this.planners = HydrologyPlanners.create(settings, counting);
            this.rasterizer = new ChunkRasterizer(settings);
            Set<RegionKey> regionKeys = new TreeSet<>(REGION_ORDER);
            for (WindowKey key : planned.keys()) {
                regionKeys.addAll(WindowPlanner.lakeRegions(key));
            }
            for (RegionKey key : regionKeys) {
                double started = cpuMillis();
                try {
                    regions.put(key, planners.regions().plan(key));
                } catch (RuntimeException failure) {
                    failures.add("region " + key.rx() + "," + key.rz() + " plan: " + cause(failure));
                }
                regionCpu.put(key, cpuMillis() - started);
            }
            for (WindowKey key : planned.keys()) {
                List<RegionPlan> needed = new ArrayList<>();
                for (RegionKey region : WindowPlanner.lakeRegions(key)) {
                    needed.add(regions.get(region));
                }
                if (needed.contains(null)) {
                    failures.add("window " + key.wx() + "," + key.wz() + " plan: a region plan it needs failed");
                    continue;
                }
                double started = cpuMillis();
                try {
                    WindowPlan plan = planners.windows().plan(key, needed);
                    windows.put(key, new IndexedWindow(plan, ReachIndex.build(plan.reaches(), plan.ponds())));
                } catch (RuntimeException failure) {
                    failures.add("window " + key.wx() + "," + key.wz() + " plan: " + cause(failure));
                }
                windowCpu.put(key, cpuMillis() - started);
            }
        }

        List<String> failures() {
            return List.copyOf(failures);
        }

        String digest(WindowKey key) {
            IndexedWindow window = windows.get(key);
            return window == null ? "failed" : digest(List.of(HydrologyPlanCodec.encode(settings, window.plan())));
        }

        String digest() {
            List<byte[]> encoded = new ArrayList<>();
            for (WindowKey key : planned.keys()) {
                IndexedWindow window = windows.get(key);
                encoded.add(window == null ? new byte[0] : HydrologyPlanCodec.encode(settings, window.plan()));
            }
            for (RegionKey key : regionKeys()) {
                encoded.add(HydrologyPlanCodec.encode(settings, regions.get(key)));
            }
            return digest(encoded);
        }

        private static String digest(List<byte[]> encoded) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                for (byte[] bytes : encoded) {
                    sha.update(bytes);
                }
                return HexFormat.of().formatHex(sha.digest(), 0, 8);
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 is unavailable.", failure);
            }
        }

        private List<RegionKey> regionKeys() {
            List<RegionKey> keys = new ArrayList<>(regions.keySet());
            keys.sort(REGION_ORDER);
            return keys;
        }

        IndexedWindow window(WindowKey key) {
            return Objects.requireNonNull(windows.get(key), "window plan " + key);
        }

        RegionPlan region(RegionKey key) {
            return Objects.requireNonNull(regions.get(key), "region plan " + key);
        }

        Map<WindowKey, WindowPlan> windowPlans() {
            Map<WindowKey, WindowPlan> plans = new LinkedHashMap<>();
            for (WindowKey key : planned.keys()) {
                IndexedWindow window = windows.get(key);
                if (window != null) {
                    plans.put(key, window.plan());
                }
            }
            return plans;
        }

        List<Pond> lakes() {
            List<Pond> lakes = new ArrayList<>();
            for (RegionKey key : regionKeys()) {
                lakes.addAll(regions.get(key).lakes());
            }
            return lakes;
        }

        double windowCpu(WindowKey key) {
            return windowCpu.getOrDefault(key, 0D);
        }

        double regionCpu(RegionKey key) {
            return regionCpu.getOrDefault(key, 0D);
        }

        double meanRegionCpu() {
            double total = 0D;
            for (double cpu : regionCpu.values()) {
                total += cpu;
            }
            return regionCpu.isEmpty() ? 0D : total / regionCpu.size();
        }

        int regionCount() {
            return regions.size();
        }

        int windowCount() {
            return windows.size();
        }

        long naturalSamples() {
            return counting.natural();
        }

        long baseSamples() {
            return counting.base();
        }

        double landTiles(WindowKey key) {
            int cells = WindowKey.SIZE / WindowPlanner.CELL;
            HydrologySampleBuffer samples = new HydrologySampleBuffer(cells * cells);
            planners.windowLattice().fill(key.wx() * cells, key.wz() * cells, cells, cells, samples);
            int land = 0;
            for (int cell = 0; cell < cells * cells; cell++) {
                if (!samples.ocean()[cell]) {
                    land++;
                }
            }
            return land * (double) (WindowPlanner.CELL * WindowPlanner.CELL) / TILE_AREA;
        }

        ChunkWaterRaster rasterize(int chunkX, int chunkZ) {
            WindowKey own = WindowKey.containing(chunkX << 4, chunkZ << 4);
            List<IndexedWindow> needed = new ArrayList<>(9);
            for (WindowKey key : HydrologyWindows.neighbourhood(own)) {
                IndexedWindow window = windows.get(key);
                if (window == null) {
                    throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " needs window " + key + ", which the probe did not plan.");
                }
                needed.add(window);
            }
            List<RegionPlan> lakes = new ArrayList<>();
            for (RegionKey region : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
                lakes.add(Objects.requireNonNull(regions.get(region), "region plan " + region));
            }
            return rasterizer.rasterize(chunkX, chunkZ, naturalHeight, needed, lakes);
        }

        HydrologySettings settings() {
            return settings;
        }
    }

    static final class CountingTerrain implements HydrologyTerrainSource {
        private final HydrologyTerrainSource delegate;
        private final AtomicLong natural = new AtomicLong();
        private final AtomicLong base = new AtomicLong();

        CountingTerrain(HydrologyTerrainSource delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            (baseOnly ? base : natural).addAndGet(xs.length);
            delegate.sample(xs, zs, out, baseOnly);
        }

        @Override
        public int seaLevel() {
            return delegate.seaLevel();
        }

        @Override
        public int minY() {
            return delegate.minY();
        }

        @Override
        public int maxY() {
            return delegate.maxY();
        }

        long natural() {
            return natural.get();
        }

        long base() {
            return base.get();
        }
    }

    static final class RasterCache implements HydrologyRasterMetrics.Rasters {
        private final int capacity;
        private final BiFunction<Integer, Integer, ChunkWaterRaster> compute;
        private final LinkedHashMap<Long, ChunkWaterRaster> entries;

        RasterCache(int capacity, BiFunction<Integer, Integer, ChunkWaterRaster> compute) {
            this.capacity = capacity;
            this.compute = compute;
            this.entries = new LinkedHashMap<>(capacity * 2, 0.75f, true);
        }

        @Override
        public ChunkWaterRaster get(int chunkX, int chunkZ) {
            long key = HydrologyVoxels.chunkKey(chunkX, chunkZ);
            synchronized (entries) {
                ChunkWaterRaster known = entries.get(key);
                if (known != null) {
                    return known;
                }
            }
            ChunkWaterRaster raster = compute.apply(chunkX, chunkZ);
            synchronized (entries) {
                entries.put(key, raster);
                if (entries.size() > capacity) {
                    Long eldest = entries.keySet().iterator().next();
                    entries.remove(eldest);
                }
            }
            return raster;
        }
    }

    static String cause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    static double cpuMillis() {
        return ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime() / 1_000_000D;
    }
}
