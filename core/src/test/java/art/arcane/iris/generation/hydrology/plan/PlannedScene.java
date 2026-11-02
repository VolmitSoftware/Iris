package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.raster.ChannelFluids;
import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.ReachIndex;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class PlannedScene {
    private static final int CHUNK = 16;

    private final SyntheticTerrain terrain;
    private final HydrologySettings settings;
    private final int windowsX;
    private final int windowsZ;
    private final Map<WindowKey, WindowPlan> plans;
    private final Function<RegionKey, RegionPlan> regions;
    private final List<Reach> reaches;
    private final List<Pond> ponds;
    private final List<SeaInlet> inlets;
    private final ChunkRasterizer rasterizer;
    private final Map<WindowKey, IndexedWindow> windows = new HashMap<>();
    private final Map<Long, ChunkWaterRaster> rasters = new HashMap<>();

    private PlannedScene(SyntheticTerrain terrain, HydrologySettings settings, int windowsX, int windowsZ, Map<WindowKey, WindowPlan> plans,
                         Function<RegionKey, RegionPlan> regions) {
        this.terrain = terrain;
        this.settings = settings;
        this.windowsX = windowsX;
        this.windowsZ = windowsZ;
        this.plans = plans;
        this.regions = regions;
        this.rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
        List<Reach> ownedReaches = new ArrayList<>();
        List<Pond> ownedPonds = new ArrayList<>();
        List<SeaInlet> ownedInlets = new ArrayList<>();
        for (WindowPlan plan : plans.values()) {
            ownedReaches.addAll(plan.reaches());
            ownedPonds.addAll(plan.ponds());
            ownedInlets.addAll(plan.inlets());
        }
        this.reaches = List.copyOf(ownedReaches);
        this.ponds = List.copyOf(ownedPonds);
        this.inlets = List.copyOf(ownedInlets);
    }

    static PlannedScene plan(SyntheticTerrain terrain, HydrologySettings settings, int windowsX, int windowsZ) {
        RegionPlanner regionPlanner = new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20), new PondPlanner(settings), new ReachShaper(settings));
        WindowPlanner windowPlanner = new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21),
                new PondPlanner(settings), new ReachShaper(settings));
        Map<RegionKey, RegionPlan> regionPlans = new HashMap<>();
        Function<RegionKey, RegionPlan> regions = key -> regionPlans.computeIfAbsent(key, regionPlanner::plan);
        Map<WindowKey, WindowPlan> plans = new LinkedHashMap<>();
        for (int wz = 0; wz < windowsZ; wz++) {
            for (int wx = 0; wx < windowsX; wx++) {
                WindowKey key = new WindowKey(wx, wz);
                plans.put(key, windowPlanner.plan(key, regionPlans(WindowPlanner.lakeRegions(key), regions)));
            }
        }
        return new PlannedScene(terrain, settings, windowsX, windowsZ, plans, regions);
    }

    static PlannedScene of(SyntheticTerrain terrain, HydrologySettings settings, List<Reach> reaches, List<Pond> ponds) {
        WindowKey origin = new WindowKey(0, 0);
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            requireOrigin(origin, WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])), reach);
        }
        for (Pond pond : ponds) {
            requireOrigin(origin, WindowKey.containing(pond.floorX(), pond.floorZ()), pond);
        }
        Map<WindowKey, WindowPlan> plans = new LinkedHashMap<>();
        plans.put(origin, new WindowPlan(origin, reaches, ponds, List.of()));
        return new PlannedScene(terrain, settings, 1, 1, plans, key -> new RegionPlan(key, List.of(), List.of()));
    }

    static HydrologySettings packSettings(long seed) {
        IrisHydrology config = new IrisHydrology();
        config.setTiers(new KList<>(
                new IrisHydrologyTier().setName("stream").setMinCatchment(900D).setHalfWidth(new KList<>(0.5D, 2.5D)).setWidthFactor(1.2D)
                        .setDepthRatio(0.4D).setInset(1).setBankBand(8),
                new IrisHydrologyTier().setName("river").setMinCatchment(5000D).setHalfWidth(new KList<>(2.5D, 9D)).setWidthFactor(1.2D)
                        .setDepthRatio(0.45D).setInset(2).setBankBand(32),
                new IrisHydrologyTier().setName("major").setMinCatchment(200000D).setHalfWidth(new KList<>(9D, 18D)).setWidthFactor(1.2D)
                        .setDepthRatio(0.5D).setInset(3).setBankBand(48).setWaterfallMinimumDrop(8).setDeltas(true)));
        config.getPonds().setFreeboard(new KList<>(1, 2));
        config.getPonds().setClimateDrawdown(3);
        config.getPonds().getSeeded().setDensity(10D).setRadius(new KList<>(6, 22)).setMaxSlope(0.7D).setChannelSpacing(new KList<>(80, 192));
        config.getPonds().getShore().setRoundness(0.5D).setShelf(6).setTaper(14).setMaxDepth(8);
        return HydrologySettings.compile(config, seed, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    HydrologySettings settings() {
        return settings;
    }

    List<Reach> reaches() {
        return reaches;
    }

    List<Pond> ponds() {
        return ponds;
    }

    List<SeaInlet> inlets() {
        return inlets;
    }

    int minX() {
        return 0;
    }

    int minZ() {
        return 0;
    }

    int maxX() {
        return windowsX * WindowKey.SIZE - 1;
    }

    int maxZ() {
        return windowsZ * WindowKey.SIZE - 1;
    }

    int head(int x, int z) {
        return at(x, z).waterHead(Math.floorMod(x, CHUNK), Math.floorMod(z, CHUNK));
    }

    int terrain(int x, int z) {
        ChunkWaterRaster raster = at(x, z);
        return raster.empty() ? (int) Math.round(terrain.natural(x, z)) : raster.terrainHeight(Math.floorMod(x, CHUNK), Math.floorMod(z, CHUNK));
    }

    WaterRole role(int x, int z) {
        return at(x, z).role(Math.floorMod(x, CHUNK), Math.floorMod(z, CHUNK));
    }

    long feature(int x, int z) {
        return at(x, z).featureId(Math.floorMod(x, CHUNK), Math.floorMod(z, CHUNK));
    }

    private ChunkWaterRaster at(int x, int z) {
        int chunkX = Math.floorDiv(x, CHUNK);
        int chunkZ = Math.floorDiv(z, CHUNK);
        return rasters.computeIfAbsent(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL), ignored -> rasterizer.rasterize(chunkX, chunkZ,
                terrain::natural, windows(chunkX, chunkZ), regionPlans(ChunkRasterizer.lakeRegions(chunkX, chunkZ), regions)));
    }

    private List<IndexedWindow> windows(int chunkX, int chunkZ) {
        WindowKey own = WindowKey.containing(chunkX * CHUNK, chunkZ * CHUNK);
        List<IndexedWindow> around = new ArrayList<>(9);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                around.add(window(new WindowKey(own.wx() + dx, own.wz() + dz)));
            }
        }
        return around;
    }

    private IndexedWindow window(WindowKey key) {
        return windows.computeIfAbsent(key, ignored -> {
            WindowPlan plan = plans.getOrDefault(key, new WindowPlan(key, List.of(), List.of(), List.of()));
            return new IndexedWindow(plan, ReachIndex.build(plan.reaches(), plan.ponds()));
        });
    }

    private static List<RegionPlan> regionPlans(List<RegionKey> keys, Function<RegionKey, RegionPlan> regions) {
        List<RegionPlan> plans = new ArrayList<>(keys.size());
        for (RegionKey key : keys) {
            plans.add(regions.apply(key));
        }
        return plans;
    }

    private static void requireOrigin(WindowKey origin, WindowKey owner, Object feature) {
        if (!origin.equals(owner)) {
            throw new IllegalArgumentException(feature + " belongs to window " + owner + ", outside the hand-built scene window " + origin);
        }
    }
}
