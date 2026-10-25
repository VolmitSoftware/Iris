package art.arcane.iris.probe;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveStorage;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class SeaInletProbe {
    private static final String LOG = "[sea-inlet-probe]";
    private static final int CHUNKS_PER_WINDOW = WindowKey.SIZE >> 4;
    private static final int BAND_BELOW = 12;
    private static final int BAND_ABOVE = 110;
    private static final byte AIR = 0;
    private static final byte SOURCE = 1;
    private static final byte FLOWING = 2;
    private static final byte LOGGED = 3;
    private static final byte SOLID = 4;
    private static final byte OTHER = 5;
    private static final byte FOREIGN = 6;
    private static final byte LOOSE = 7;
    private static final byte SEA_LAYER = 1;
    private static final byte DECORATION = 2;
    private static final Map<String, Set<String>> LAYER_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> DECORATION_KEYS = new ConcurrentHashMap<>();
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private SeaInletProbe() {
    }

    public static void main(String[] arguments) {
        try {
            Configuration configuration = Configuration.parse(arguments);
            if (configuration.mode().equals("inventory")) {
                inventory(configuration);
            } else if (configuration.mode().equals("dump")) {
                dump(configuration);
            } else {
                gate(configuration);
            }
            System.exit(0);
        } catch (Throwable failure) {
            System.out.println(LOG + " FAIL");
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void dump(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = workspace(configuration, configuration.packOn());
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "dump")) {
            Engine engine = session.engine();
            int x = configuration.wx0();
            int z = configuration.wz0();
            int height = engine.getTarget().getHeight();
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
            Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
            engine.generate(x >> 4 << 4, z >> 4 << 4, blocks, biomes, false);
            int sea = engine.getDimension().getFluidHeight();
            ChunkWaterRaster raster = engine.getComplex().getHydrologyRuntime().raster(x >> 4, z >> 4);
            System.out.println("DUMP column " + x + "," + z + " role=" + raster.role(x & 15, z & 15) + " head=" + raster.waterHead(x & 15, z & 15)
                    + " terrain=" + (raster.empty() ? "natural" : raster.terrainHeight(x & 15, z & 15)) + " natural="
                    + engine.getComplex().naturalTrueHeight(x, z) + " rock=" + engine.getComplex().getRockStream().get(x, z).key());
            for (int y = sea + configuration.wz1(); y >= sea - 12; y--) {
                HydrologyCaveCell cell = HydrologyCaveStorage.getIfPresent(engine.getMantle().getMantle(), x, y, z);
                NativeBlockState state = blocks.get(x & 15, y, z & 15);
                System.out.println("DUMP y=" + y + " block=" + (state == null ? "null" : state.key()) + " cell=" + (cell == null ? "-" : cell.action()));
            }
        }
    }

    private static void inventory(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = workspace(configuration, configuration.packOn());
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "inventory")) {
            HydrologyRuntime runtime = session.engine().getComplex().getHydrologyRuntime();
            for (int wz = configuration.wz0(); wz <= configuration.wz1(); wz++) {
                for (int wx = configuration.wx0(); wx <= configuration.wx1(); wx++) {
                    int[] coast = coast(session.engine(), wx, wz);
                    Map<Long, SeaInlet> owned;
                    try {
                        owned = coast[1] == 0 ? Map.of() : owned(runtime, wx, wx, wz, wz);
                    } catch (RuntimeException failure) {
                        System.out.println("SEA_INLET_WINDOW_FAILED wx=" + wx + " wz=" + wz + " " + rootMessage(failure));
                        continue;
                    }
                    double length = 0D;
                    for (SeaInlet inlet : owned.values()) {
                        length += length(inlet);
                    }
                    System.out.printf(Locale.ROOT, "SEA_INLET_WINDOW wx=%d wz=%d belowSea=%d cliffCoast=%d inlets=%d meanLength=%.1f%n", wx, wz,
                            coast[0], coast[1], owned.size(), owned.isEmpty() ? 0D : length / owned.size());
                }
            }
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static int[] coast(Engine engine, int wx, int wz) {
        int sea = engine.getDimension().getFluidHeight();
        int cells = WindowKey.SIZE / 8;
        int[] heights = new int[(cells + 2) * (cells + 2)];
        for (int j = 0; j < cells + 2; j++) {
            for (int i = 0; i < cells + 2; i++) {
                heights[j * (cells + 2) + i] = engine.getComplex().naturalTrueHeight(wx * WindowKey.SIZE + (i - 1) * 8, wz * WindowKey.SIZE + (j - 1) * 8);
            }
        }
        int below = 0;
        int cliff = 0;
        for (int j = 1; j <= cells; j++) {
            for (int i = 1; i <= cells; i++) {
                int height = heights[j * (cells + 2) + i];
                if (height < sea) {
                    below++;
                    continue;
                }
                boolean coastal = false;
                for (int[] side : SIDES) {
                    coastal |= heights[(j + side[1]) * (cells + 2) + i + side[0]] < sea;
                }
                if (coastal && height >= sea + 8) {
                    cliff++;
                }
            }
        }
        return new int[]{below, cliff};
    }

    private static void gate(Configuration configuration) throws Exception {
        List<long[]> tiles = tileChunks(configuration);
        Run forward = run(configuration, configuration.packOn(), tiles, null, false, true, 1, "on-forward");
        List<long[]> all = new ArrayList<>(forward.order);
        List<long[]> scanned = new ArrayList<>();
        for (long scan : forward.scanChunks) {
            scanned.add(new long[]{chunkX(scan), chunkZ(scan)});
        }
        System.out.println(LOG + " scan chunks " + forward.scanChunks.size() + ", generated chunks " + all.size());
        Run reverse = run(configuration, configuration.packOn(), all, null, true, false, configuration.threads(), "on-reverse");
        Run baseline = run(configuration, configuration.packOff(), scanned, forward.scanChunks, false, false, configuration.threads(), "off");
        report(configuration, forward.inlets, forward, reverse, baseline);
    }

    private static Run run(Configuration configuration, File pack, List<long[]> chunks, Set<Long> scanChunks, boolean reverse, boolean inventory,
                           int threads, String label) throws Exception {
        List<long[]> ordered = new ArrayList<>(chunks);
        if (reverse) {
            Collections.reverse(ordered);
        }
        Run run = new Run(label);
        try (RealPackProbeSupport.Workspace workspace = workspace(configuration, pack);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, label)) {
            Engine engine = session.engine();
            HydrologyRuntime runtime = engine.getComplex().getHydrologyRuntime();
            if (inventory) {
                run.inlets.putAll(touching(runtime, configuration));
                run.scanChunks.addAll(scanChunks(run.inlets, configuration));
                Set<Long> listed = new TreeSet<>();
                for (long[] chunk : ordered) {
                    listed.add(key((int) chunk[0], (int) chunk[1]));
                }
                for (long scan : run.scanChunks) {
                    if (!listed.contains(scan)) {
                        ordered.add(new long[]{chunkX(scan), chunkZ(scan)});
                    }
                }
                System.out.printf(Locale.ROOT, "%s %s: %d inlets touch the tiles%n", LOG, label, run.inlets.size());
            }
            run.order.addAll(ordered);
            Set<Long> capture = scanChunks != null ? scanChunks : run.scanChunks;
            int height = engine.getTarget().getHeight();
            int sea = engine.getDimension().getFluidHeight();
            AtomicInteger done = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<?>> futures = new ArrayList<>(ordered.size());
                for (long[] chunk : ordered) {
                    futures.add(pool.submit(() -> {
                        int chunkX = (int) chunk[0];
                        int chunkZ = (int) chunk[1];
                        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                        engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                        long key = key(chunkX, chunkZ);
                        run.hashes.put(key, hash(blocks, biomes, height));
                        if (capture.contains(key)) {
                            run.bands.put(key, Band.capture(engine, blocks, chunkX, chunkZ, sea, height));
                        }
                        int count = done.incrementAndGet();
                        if (count % 1024 == 0 || count == ordered.size()) {
                            System.out.println(LOG + " " + label + " generated " + count + "/" + ordered.size());
                        }
                        return null;
                    }));
                    if (threads == 1) {
                        futures.get(futures.size() - 1).get();
                    }
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                pool.shutdown();
            }
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
            if (!reports.isEmpty()) {
                RealPackProbeSupport.printReports(LOG, label, reports);
                throw new IllegalStateException(label + " reported " + reports.size() + " generation errors");
            }
            run.sea = sea;
            run.roofClearance = engine.getDimension().getHydrology() == null ? 0
                    : engine.getDimension().getHydrology().getUnderground().getSeaInlets().getRoofClearance();
        }
        return run;
    }

    record Checks(int inlets, long leaks, long missingWater, long waterAbove, long roofViolations, long roofBreaches, long openRoofViolations,
                  long disconnected, long shellOpen, long shellLoose, long looseBeds, List<String> examples) {
        static final Checks NONE = new Checks(0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, List.of());

        long failures() {
            return leaks + missingWater + waterAbove + roofViolations + openRoofViolations + disconnected + shellOpen + shellLoose;
        }
    }

    static Checks check(Engine engine, List<SeaInlet> inlets, ExecutorService workers) throws Exception {
        if (inlets.isEmpty()) {
            return Checks.NONE;
        }
        Run run = new Run("hydrology");
        run.sea = engine.getDimension().getFluidHeight();
        run.roofClearance = engine.getDimension().getHydrology() == null ? 0
                : engine.getDimension().getHydrology().getUnderground().getSeaInlets().getRoofClearance();
        int height = engine.getTarget().getHeight();
        Set<Long> chunks = new TreeSet<>();
        for (SeaInlet inlet : inlets) {
            int[] bounds = SeaInletRasterizer.bounds(inlet);
            for (int chunkZ = (bounds[1] >> 4) - 1; chunkZ <= (bounds[3] >> 4) + 1; chunkZ++) {
                for (int chunkX = (bounds[0] >> 4) - 1; chunkX <= (bounds[2] >> 4) + 1; chunkX++) {
                    chunks.add(key(chunkX, chunkZ));
                }
            }
        }
        List<Future<?>> futures = new ArrayList<>(chunks.size());
        for (long chunk : chunks) {
            futures.add(workers.submit(() -> {
                int chunkX = chunkX(chunk);
                int chunkZ = chunkZ(chunk);
                Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                run.bands.put(chunk, Band.capture(engine, blocks, chunkX, chunkZ, run.sea, height));
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        Scan scan = new Scan(run, run.sea, run.roofClearance, inlets);
        scan.run();
        Scan.InletStats total = scan.total;
        return new Checks(inlets.size(), total.leaks, total.missingWater, total.waterAbove, total.roofViolations, total.roofBreaches,
                total.openRoofViolations, total.disconnected, scan.shellOpen, scan.shellLoose, total.looseBeds, List.copyOf(scan.examples));
    }

    private static Map<Long, SeaInlet> touching(HydrologyRuntime runtime, Configuration configuration) {
        Map<Long, SeaInlet> inlets = new TreeMap<>();
        for (int chunkZ = configuration.wz0() * CHUNKS_PER_WINDOW; chunkZ < (configuration.wz1() + 1) * CHUNKS_PER_WINDOW; chunkZ++) {
            for (int chunkX = configuration.wx0() * CHUNKS_PER_WINDOW; chunkX < (configuration.wx1() + 1) * CHUNKS_PER_WINDOW; chunkX++) {
                for (SeaInlet inlet : runtime.inlets(chunkX, chunkZ)) {
                    inlets.putIfAbsent(inlet.id(), inlet);
                }
            }
        }
        return inlets;
    }

    private static Map<Long, SeaInlet> owned(HydrologyRuntime runtime, int wx0, int wx1, int wz0, int wz1) {
        Map<Long, SeaInlet> owned = new TreeMap<>();
        for (int chunkZ = wz0 * CHUNKS_PER_WINDOW; chunkZ < (wz1 + 1) * CHUNKS_PER_WINDOW; chunkZ++) {
            for (int chunkX = wx0 * CHUNKS_PER_WINDOW; chunkX < (wx1 + 1) * CHUNKS_PER_WINDOW; chunkX++) {
                for (SeaInlet inlet : runtime.inlets(chunkX, chunkZ)) {
                    WindowKey owner = owner(inlet);
                    if (owner.wx() >= wx0 && owner.wx() <= wx1 && owner.wz() >= wz0 && owner.wz() <= wz1) {
                        owned.putIfAbsent(inlet.id(), inlet);
                    }
                }
            }
        }
        return owned;
    }

    private static WindowKey owner(SeaInlet inlet) {
        return WindowKey.containing((int) Math.floor(inlet.xs()[1]), (int) Math.floor(inlet.zs()[1]));
    }

    private static Set<Long> scanChunks(Map<Long, SeaInlet> inlets, Configuration configuration) {
        Set<Long> chunks = new TreeSet<>();
        for (SeaInlet inlet : inlets.values()) {
            if (!ownedByTiles(inlet, configuration)) {
                continue;
            }
            int[] bounds = SeaInletRasterizer.bounds(inlet);
            for (int chunkZ = (bounds[1] >> 4) - 1; chunkZ <= (bounds[3] >> 4) + 1; chunkZ++) {
                for (int chunkX = (bounds[0] >> 4) - 1; chunkX <= (bounds[2] >> 4) + 1; chunkX++) {
                    chunks.add(key(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    private static boolean ownedByTiles(SeaInlet inlet, Configuration configuration) {
        WindowKey owner = owner(inlet);
        return owner.wx() >= configuration.wx0() && owner.wx() <= configuration.wx1() && owner.wz() >= configuration.wz0()
                && owner.wz() <= configuration.wz1();
    }

    private static List<long[]> tileChunks(Configuration configuration) {
        List<long[]> chunks = new ArrayList<>();
        for (int chunkZ = configuration.wz0() * CHUNKS_PER_WINDOW; chunkZ < (configuration.wz1() + 1) * CHUNKS_PER_WINDOW; chunkZ++) {
            for (int chunkX = configuration.wx0() * CHUNKS_PER_WINDOW; chunkX < (configuration.wx1() + 1) * CHUNKS_PER_WINDOW; chunkX++) {
                chunks.add(new long[]{chunkX, chunkZ});
            }
        }
        return chunks;
    }

    private static void report(Configuration configuration, Map<Long, SeaInlet> touching, Run on, Run reverse, Run off) {
        int sea = on.sea;
        int clearance = on.roofClearance;
        List<SeaInlet> owned = new ArrayList<>();
        for (SeaInlet inlet : touching.values()) {
            if (ownedByTiles(inlet, configuration)) {
                owned.add(inlet);
            }
        }
        Scan scan = new Scan(on, sea, clearance, owned);
        scan.run();
        int onLeaks = scan.countAllLeaks();
        int offLeaks = new Scan(off, sea, clearance, owned).countAllLeaks();
        int mismatches = mismatches(on.hashes, reverse.hashes);

        System.out.println("SEA_INLET_INVENTORY owned=" + owned.size() + " touching=" + touching.size());
        for (SeaInlet inlet : owned) {
            Scan.InletStats stats = scan.stats.get(inlet.id());
            WindowKey window = owner(inlet);
            System.out.printf(Locale.ROOT,
                    "SEA_INLET id=%d window=%d,%d mouth=%.0f,%.0f stations=%d open=%d length=%.1f chamber=%d columns=%d wet=%d dry=%d"
                            + " chunks=%d crossesWindows=%s leaks=%d missingWater=%d seaPlants=%d nonSource=%d waterAboveSea=%d roofMin=%d"
                            + " roofViolations=%d roofBreaches=%d openRoofViolations=%d disconnectedWet=%d%n",
                    inlet.id(), window.wx(), window.wz(), inlet.xs()[0], inlet.zs()[0], inlet.stations(), openStations(inlet), length(inlet),
                    inlet.chamberRadius(), stats.columns, stats.wet, stats.dry, stats.chunks.size(), stats.windows.size() > 1, stats.leaks,
                    stats.missingWater, stats.seaPlants, stats.nonSource, stats.waterAbove, stats.roofMin, stats.roofViolations, stats.roofBreaches,
                    stats.openRoofViolations, stats.disconnected);
        }
        for (String example : scan.examples) {
            System.out.println("SEA_INLET_EXAMPLE " + example);
        }
        int offLevel = 0;
        for (SeaInlet inlet : owned) {
            offLevel += inlet.waterLevel() == sea ? 0 : 1;
        }
        System.out.printf(Locale.ROOT, "SEA_INLET_CONTAINMENT leaks=%d missingWater=%d seaPlantCells=%d slotSeaLayerCells=%d"
                        + " slotDecorationCells=%d nonSource=%d waterAboveSea=%d wetCells=%d dryCells=%d%n", scan.total.leaks,
                scan.total.missingWater, scan.total.seaPlants, scan.total.seaLayers, scan.total.decorations, scan.total.nonSource,
                scan.total.waterAbove, scan.total.wet, scan.total.dry);
        System.out.printf(Locale.ROOT, "SEA_INLET_LEVEL sea=%d inletsWithOtherLevel=%d%n", sea, offLevel);
        System.out.printf(Locale.ROOT, "SEA_INLET_ALL_WATER_LEAKS scanChunks=%d inletsOn=%d inletsOff=%d%n", on.bands.size(), onLeaks, offLeaks);
        System.out.printf(Locale.ROOT, "SEA_INLET_ROOF min=%d violations=%d breaches=%d openRoofViolations=%d coveredColumns=%d openColumns=%d%n",
                scan.total.roofMin, scan.total.roofViolations, scan.total.roofBreaches, scan.total.openRoofViolations, scan.total.covered,
                scan.total.open);
        System.out.printf(Locale.ROOT, "SEA_INLET_OCEAN connected=%d partial=%d disconnected=%d disconnectedWetCells=%d%n", scan.connected,
                scan.partial, scan.unconnected, scan.total.disconnected);
        System.out.printf(Locale.ROOT, "SEA_INLET_SEAL cells=%d open=%d loose=%d maxOpenLandStations=%d slotLooseBeds=%d%n", scan.shellCells,
                scan.shellOpen, scan.shellLoose, maxOpenLandStations(owned), scan.total.looseBeds);
        System.out.printf(Locale.ROOT, "SEA_INLET_DETERMINISM chunks=%d reverseChunks=%d mismatches=%d crossChunkInlets=%d crossWindowInlets=%d%n",
                on.hashes.size(), reverse.hashes.size(), mismatches, scan.crossChunk(), scan.crossWindow());
    }

    private static int openStations(SeaInlet inlet) {
        int open = 0;
        for (int station = 1; station < inlet.stations(); station++) {
            open += inlet.open(station) ? 1 : 0;
        }
        return open;
    }

    private static int maxOpenLandStations(List<SeaInlet> inlets) {
        int most = 0;
        for (SeaInlet inlet : inlets) {
            most = Math.max(most, openStations(inlet));
        }
        return most;
    }

    private static double length(SeaInlet inlet) {
        double length = 0D;
        for (int station = 1; station < inlet.stations(); station++) {
            length += Math.hypot(inlet.xs()[station] - inlet.xs()[station - 1], inlet.zs()[station] - inlet.zs()[station - 1]);
        }
        return length;
    }

    private static int mismatches(Map<Long, Long> expected, Map<Long, Long> observed) {
        int mismatches = 0;
        for (Map.Entry<Long, Long> entry : expected.entrySet()) {
            Long other = observed.get(entry.getKey());
            if (other == null || !other.equals(entry.getValue())) {
                mismatches++;
                if (mismatches <= 8) {
                    System.out.println("SEA_INLET_MISMATCH chunk=" + chunkX(entry.getKey()) + "," + chunkZ(entry.getKey()));
                }
            }
        }
        return mismatches + Math.max(0, observed.size() - expected.size());
    }

    private static long hash(Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes, int height) {
        long hash = 0xcbf29ce484222325L;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    NativeBiome biome = biomes.get(x, y, z);
                    hash = (hash ^ (state == null ? 0 : state.key().hashCode())) * 0x100000001b3L;
                    hash = (hash ^ (biome == null ? 0 : biome.key().hashCode())) * 0x100000001b3L;
                }
            }
        }
        return hash;
    }

    private static RealPackProbeSupport.Workspace workspace(Configuration configuration, File pack) throws Exception {
        return RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(pack, configuration.dimension(), LOG, null,
                configuration.work()));
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    static int chunkZ(long key) {
        return (int) key;
    }

    static byte classify(NativeBlockState state, String seaFluid) {
        if (state == null || state.isAir()) {
            return AIR;
        }
        String key = state.key();
        String material = IrisProceduralBlocks.materialKey(state);
        if (material.equals(seaFluid)) {
            String level = IrisProceduralBlocks.propertyValue(state, "level");
            return level == null || "0".equals(level) ? SOURCE : FLOWING;
        }
        if (material.equals("minecraft:water") || material.equals("minecraft:lava")) {
            return FOREIGN;
        }
        boolean holdsWater = key.contains("seagrass") || key.contains("kelp") || key.contains("bubble_column")
                || "true".equals(IrisProceduralBlocks.propertyValue(state, "waterlogged"));
        if (holdsWater) {
            return seaFluid.equals("minecraft:water") ? LOGGED : FOREIGN;
        }
        if (!state.isSolid()) {
            return OTHER;
        }
        return IrisProceduralBlocks.isGravityAffected(state) ? LOOSE : SOLID;
    }

    private record Configuration(File packOn, File packOff, String dimension, long seed, int wx0, int wx1, int wz0, int wz1, String mode,
                                 Path work, int threads) {
        static Configuration parse(String[] arguments) {
            if (arguments.length != 11) {
                throw new IllegalArgumentException("Expected: <packOn> <packOff> <dimension> <seed> <wx0> <wx1> <wz0> <wz1> <inventory|gate|dump>"
                        + " <workDir> <threads>");
            }
            return new Configuration(new File(arguments[0]), new File(arguments[1]), arguments[2], Long.parseLong(arguments[3]),
                    Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]), Integer.parseInt(arguments[6]), Integer.parseInt(arguments[7]),
                    arguments[8], Path.of(arguments[9]), Integer.parseInt(arguments[10]));
        }
    }

    static final class Band {
        final int chunkX;
        final int chunkZ;
        final int bottom;
        final int levels;
        final byte[] cells;
        final int[] terrain;
        final int[] ground;
        final int[] skyTop;
        final byte[] placed;

        private Band(int chunkX, int chunkZ, int bottom, int levels) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.bottom = bottom;
            this.levels = levels;
            this.cells = new byte[256 * levels];
            this.placed = new byte[256 * levels];
            this.terrain = new int[256];
            this.ground = new int[256];
            this.skyTop = new int[256];
        }

        static Band capture(Engine engine, Hunk<NativeBlockState> blocks, int chunkX, int chunkZ, int sea, int height) {
            Band band = new Band(chunkX, chunkZ, sea - BAND_BELOW, BAND_BELOW + BAND_ABOVE + 1);
            String seaFluid = IrisProceduralBlocks.materialKey(engine.getComplex().getFluidStream().get((chunkX << 4) + 8, (chunkZ << 4) + 8));
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int column = lz << 4 | lx;
                    IrisBiome biome = engine.getComplex().getTrueBiomeStream().get((chunkX << 4) + lx, (chunkZ << 4) + lz);
                    Set<String> layers = layerKeys(engine, biome);
                    Set<String> decorations = decorationKeys(engine, biome);
                    for (int level = 0; level < band.levels; level++) {
                        NativeBlockState state = blocks.get(lx, band.bottom + level, lz);
                        band.cells[column * band.levels + level] = classify(state, seaFluid);
                        if (band.bottom + level <= sea && state != null) {
                            band.placed[column * band.levels + level] = layers.contains(state.key()) ? SEA_LAYER
                                    : decorations.contains(state.key()) ? DECORATION : 0;
                        }
                    }
                    band.terrain[column] = engine.getMantle().trueHeight((chunkX << 4) + lx, (chunkZ << 4) + lz);
                    band.ground[column] = -1;
                    band.skyTop[column] = -1;
                    int airTop = engine.getComplex().naturalTrueHeight((chunkX << 4) + lx, (chunkZ << 4) + lz);
                    for (int y = height - 1; y >= 0; y--) {
                        NativeBlockState state = blocks.get(lx, y, lz);
                        if (state == null || state.isAir()) {
                            continue;
                        }
                        if (band.skyTop[column] < 0) {
                            band.skyTop[column] = y;
                        }
                        String key = state.key();
                        if (y <= airTop && state.isSolid() && !key.contains("leaves") && !key.contains("_log") && !key.contains("_wood")
                                && !key.contains("mushroom_block") && !key.contains("stem") && !key.contains("vine") && !key.contains("lichen")
                                && !key.contains("lily_pad") && !key.equals("minecraft:snow") && !key.startsWith("minecraft:snow[")) {
                            band.ground[column] = y;
                            break;
                        }
                    }
                }
            }
            return band;
        }

        byte at(int lx, int y, int lz) {
            int level = y - bottom;
            if (level < 0 || level >= levels) {
                return -1;
            }
            return cells[(lz << 4 | lx) * levels + level];
        }

        byte placed(int lx, int y, int lz) {
            int level = y - bottom;
            return level >= 0 && level < levels ? placed[(lz << 4 | lx) * levels + level] : 0;
        }

        int layerBottom(int lx, int lz, int floor, int water) {
            int bottom = water + 1;
            while (bottom - 1 > floor && placed(lx, bottom - 1, lz) == SEA_LAYER) {
                bottom--;
            }
            return bottom;
        }

        private static Set<String> layerKeys(Engine engine, IrisBiome biome) {
            if (biome == null) {
                return Set.of();
            }
            return LAYER_KEYS.computeIfAbsent(biome.getLoadKey(), ignored -> {
                Set<String> keys = new HashSet<>();
                for (IrisBiomePaletteLayer layer : biome.getSeaLayers()) {
                    for (NativeBlockState state : layer.getBlockData(engine.getData())) {
                        keys.add(state.key());
                    }
                }
                return keys;
            });
        }

        private static Set<String> decorationKeys(Engine engine, IrisBiome biome) {
            if (biome == null) {
                return Set.of();
            }
            return DECORATION_KEYS.computeIfAbsent(biome.getLoadKey(), ignored -> {
                Set<String> keys = new HashSet<>();
                for (IrisDecorator decorator : biome.getDecorators()) {
                    for (NativeBlockState state : decorator.getBlockData(engine.getData())) {
                        keys.add(state.key());
                    }
                    for (NativeBlockState state : decorator.getBlockDataTops(engine.getData())) {
                        keys.add(state.key());
                    }
                }
                for (IrisProceduralPlacement placement : biome.getProceduralObjects().getAllPlacements()) {
                    for (IrisObject object : placement.getVariantObjects(engine.getData())) {
                        for (NativeBlockState state : object.getBlocks().values()) {
                            keys.add(state.key());
                        }
                    }
                }
                return keys;
            });
        }
    }

    private static final class Run {
        private final String label;
        private final Map<Long, Long> hashes = new ConcurrentHashMap<>();
        private final Map<Long, Band> bands = new ConcurrentHashMap<>();
        private final List<long[]> order = new ArrayList<>();
        private final Map<Long, SeaInlet> inlets = new TreeMap<>();
        private final Set<Long> scanChunks = new TreeSet<>();
        private int sea;
        private int roofClearance;

        private Run(String label) {
            this.label = label;
        }
    }

    private static final class Scan {
        private final Run run;
        private final int sea;
        private final int clearance;
        private final List<SeaInlet> inlets;
        private final Map<Long, InletStats> stats = new LinkedHashMap<>();
        private final InletStats total = new InletStats();
        private final List<String> examples = new ArrayList<>();
        private final Map<Long, int[]> geometry = new HashMap<>();
        private int connected;
        private int partial;
        private int unconnected;
        private int shellCells;
        private int shellOpen;
        private int shellLoose;

        private Scan(Run run, int sea, int clearance, List<SeaInlet> inlets) {
            this.run = run;
            this.sea = sea;
            this.clearance = clearance;
            this.inlets = inlets;
        }

        private void run() {
            for (SeaInlet inlet : inlets) {
                int[] bounds = SeaInletRasterizer.bounds(inlet);
                int[] column = new int[2];
                for (int z = bounds[1]; z <= bounds[3]; z++) {
                    for (int x = bounds[0]; x <= bounds[2]; x++) {
                        if (SeaInletRasterizer.column(inlet, x, z, column)) {
                            long key = columnKey(x, z);
                            int[] known = geometry.get(key);
                            geometry.put(key, known == null ? new int[]{column[0], column[1]}
                                    : new int[]{Math.min(known[0], column[0]), Math.max(known[1], column[1])});
                        }
                    }
                }
            }
            for (SeaInlet inlet : inlets) {
                InletStats inletStats = new InletStats();
                stats.put(inlet.id(), inletStats);
                scanInlet(inlet, inletStats);
                total.add(inletStats);
            }
            shell();
            connectivity();
        }

        private void scanInlet(SeaInlet inlet, InletStats stats) {
            int[] bounds = SeaInletRasterizer.bounds(inlet);
            int[] column = new int[2];
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                for (int x = bounds[0]; x <= bounds[2]; x++) {
                    if (!SeaInletRasterizer.column(inlet, x, z, column)) {
                        continue;
                    }
                    Band band = band(x, z);
                    if (band == null) {
                        throw new IllegalStateException("Inlet " + inlet.id() + " column " + x + "," + z + " was not generated");
                    }
                    stats.columns++;
                    stats.chunks.add(key(x >> 4, z >> 4));
                    stats.windows.add(columnKey(Math.floorDiv(x, WindowKey.SIZE), Math.floorDiv(z, WindowKey.SIZE)));
                    int floor = column[0];
                    int ceiling = column[1];
                    boolean open = ceiling == SeaInlet.OPEN;
                    int layers = open ? band.layerBottom(x & 15, z & 15, floor, inlet.waterLevel()) : inlet.waterLevel() + 1;
                    if (open && at(x, floor, z) == LOOSE) {
                        stats.looseBeds++;
                    }
                    for (int y = floor + 1; y <= inlet.waterLevel(); y++) {
                        stats.wet++;
                        byte cell = at(x, y, z);
                        if (y >= layers) {
                            stats.seaLayers++;
                            continue;
                        }
                        if (open && cell != LOGGED && band.placed(x & 15, y, z & 15) == DECORATION) {
                            stats.decorations++;
                            continue;
                        }
                        if (cell == LOGGED) {
                            stats.seaPlants++;
                        } else if (cell != SOURCE) {
                            stats.missingWater++;
                            if (cell == FLOWING) {
                                stats.nonSource++;
                            }
                            example("missing water " + cell, x, y, z);
                        }
                        for (int[] side : SIDES) {
                            leak(stats, x, y, z, x + side[0], y, z + side[1]);
                        }
                        leak(stats, x, y, z, x, y - 1, z);
                    }
                    int lx = x & 15;
                    int lz = z & 15;
                    int index = lz << 4 | lx;
                    int top = open ? Math.min(band.bottom + band.levels - 2, Math.max(inlet.waterLevel(), band.skyTop[index])) : ceiling;
                    for (int y = inlet.waterLevel() + 1; y <= top; y++) {
                        stats.dry++;
                        byte cell = at(x, y, z);
                        if (cell == SOURCE || cell == FLOWING || cell == LOGGED) {
                            stats.waterAbove++;
                            example("water above sea level", x, y, z);
                        }
                        if (cell == AIR || cell == OTHER) {
                            for (int[] side : SIDES) {
                                airLeak(stats, x, y, z, x + side[0], y, z + side[1]);
                            }
                            airLeak(stats, x, y, z, x, y + 1, z);
                        }
                    }
                    int terrain = band.terrain[index];
                    if (!open) {
                        stats.covered++;
                        int roof = terrain - ceiling;
                        stats.roofMin = Math.min(stats.roofMin, roof);
                        if (roof < clearance) {
                            stats.roofViolations++;
                            example("roof " + roof, x, ceiling, z);
                        }
                        for (int y = ceiling + 1; y <= ceiling + clearance && y <= terrain; y++) {
                            byte cell = at(x, y, z);
                            if (cell != SOLID && cell != LOOSE) {
                                stats.roofBreaches++;
                                break;
                            }
                        }
                    } else {
                        stats.open++;
                        if (band.ground[index] > inlet.waterLevel()) {
                            stats.openRoofViolations++;
                            example("ground " + band.ground[index] + " left over the open slot", x, inlet.waterLevel(), z);
                        }
                    }
                }
            }
        }

        private void shell() {
            Map<Long, Integer> low = new HashMap<>();
            for (Map.Entry<Long, int[]> entry : geometry.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                for (int dz = -SeaInletRasterizer.SHELL; dz <= SeaInletRasterizer.SHELL; dz++) {
                    for (int dx = -SeaInletRasterizer.SHELL; dx <= SeaInletRasterizer.SHELL; dx++) {
                        low.merge(columnKey(x + dx, z + dz), entry.getValue()[0], Math::min);
                    }
                }
            }
            for (Map.Entry<Long, Integer> entry : low.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                int[] column = geometry.get(entry.getKey());
                int top = column == null ? sea : column[1] == SeaInlet.OPEN ? column[0] - 1 : Math.min(sea, column[0]);
                for (int y = entry.getValue() - SeaInletRasterizer.SHELL; y <= top; y++) {
                    byte cell = at(x, y, z);
                    shellCells++;
                    if (cell == AIR || cell == OTHER || cell == FOREIGN) {
                        shellOpen++;
                        example("open cell in the seal", x, y, z);
                    } else if (cell == LOOSE) {
                        shellLoose++;
                        example("loose block in the seal", x, y, z);
                    }
                }
            }
        }

        private void leak(InletStats stats, int x, int y, int z, int nx, int ny, int nz) {
            byte cell = at(nx, ny, nz);
            if (cell == AIR || cell == OTHER || cell == FOREIGN) {
                stats.leaks++;
                example("water beside " + (cell == AIR ? "air" : cell == FOREIGN ? "another fluid" : "open block"), x, y, z);
            }
        }

        private void airLeak(InletStats stats, int x, int y, int z, int nx, int ny, int nz) {
            byte cell = at(nx, ny, nz);
            if (cell == SOURCE || cell == FLOWING || cell == LOGGED) {
                stats.leaks++;
                example("inlet air beside water", x, y, z);
            }
        }

        private void connectivity() {
            Map<Long, boolean[]> reached = new HashMap<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (Band band : run.bands.values()) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = (band.chunkX << 4) + lx;
                        int z = (band.chunkZ << 4) + lz;
                        int columnIndex = lz << 4 | lx;
                        if (geometry.containsKey(columnKey(x, z)) || band.terrain[columnIndex] >= sea - 2 || band.skyTop[columnIndex] > sea) {
                            continue;
                        }
                        if (band.at(lx, sea - 1, lz) == SOURCE) {
                            visit(reached, queue, x, sea - 1, z);
                        } else if (band.placed(lx, sea, lz) == SEA_LAYER) {
                            visit(reached, queue, x, sea, z);
                        }
                    }
                }
            }
            while (!queue.isEmpty()) {
                int[] cell = queue.poll();
                int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
                for (int[] step : around) {
                    int x = cell[0] + step[0];
                    int y = cell[1] + step[1];
                    int z = cell[2] + step[2];
                    byte kind = at(x, y, z);
                    if (kind == SOURCE || kind == FLOWING || kind == LOGGED || frozenSea(x, y, z)) {
                        visit(reached, queue, x, y, z);
                    }
                }
            }
            for (SeaInlet inlet : inlets) {
                InletStats inletStats = stats.get(inlet.id());
                int[] bounds = SeaInletRasterizer.bounds(inlet);
                int[] column = new int[2];
                int wet = 0;
                int missing = 0;
                for (int z = bounds[1]; z <= bounds[3]; z++) {
                    for (int x = bounds[0]; x <= bounds[2]; x++) {
                        if (!SeaInletRasterizer.column(inlet, x, z, column)) {
                            continue;
                        }
                        for (int y = column[0] + 1; y <= inlet.waterLevel(); y++) {
                            byte kind = at(x, y, z);
                            if (kind != SOURCE && kind != FLOWING && kind != LOGGED) {
                                continue;
                            }
                            wet++;
                            if (!reachedAt(reached, x, y, z)) {
                                missing++;
                            }
                        }
                    }
                }
                inletStats.disconnected = missing;
                total.disconnected += missing;
                if (missing > 0) {
                    for (int station = 0; station < inlet.stations(); station++) {
                        int x = Math.round(inlet.xs()[station]);
                        int z = Math.round(inlet.zs()[station]);
                        int y = inlet.waterLevel();
                        example("inlet " + inlet.id() + " station " + station + " kind " + at(x, y, z) + " reached " + reachedAt(reached, x, y, z),
                                x, y, z);
                    }
                }
                if (missing == 0) {
                    connected++;
                } else if (missing < wet) {
                    partial++;
                } else {
                    unconnected++;
                }
            }
        }

        private boolean frozenSea(int x, int y, int z) {
            Band band = band(x, z);
            if (band == null || geometry.containsKey(columnKey(x, z))) {
                return false;
            }
            int columnIndex = (z & 15) << 4 | (x & 15);
            return band.terrain[columnIndex] < sea - 2 && band.skyTop[columnIndex] <= sea && band.placed(x & 15, y, z & 15) == SEA_LAYER;
        }

        private void visit(Map<Long, boolean[]> reached, ArrayDeque<int[]> queue, int x, int y, int z) {
            Band band = band(x, z);
            if (band == null) {
                return;
            }
            int level = y - band.bottom;
            if (level < 0 || level >= band.levels) {
                return;
            }
            boolean[] seen = reached.computeIfAbsent(key(x >> 4, z >> 4), ignored -> new boolean[256 * band.levels]);
            int index = ((z & 15) << 4 | (x & 15)) * band.levels + level;
            if (!seen[index]) {
                seen[index] = true;
                queue.add(new int[]{x, y, z});
            }
        }

        private boolean reachedAt(Map<Long, boolean[]> reached, int x, int y, int z) {
            Band band = band(x, z);
            boolean[] seen = reached.get(key(x >> 4, z >> 4));
            if (band == null || seen == null) {
                return false;
            }
            int level = y - band.bottom;
            return level >= 0 && level < band.levels && seen[((z & 15) << 4 | (x & 15)) * band.levels + level];
        }

        private int countAllLeaks() {
            int leaks = 0;
            for (Band band : run.bands.values()) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = (band.chunkX << 4) + lx;
                        int z = (band.chunkZ << 4) + lz;
                        for (int level = 1; level < band.levels; level++) {
                            int y = band.bottom + level;
                            byte cell = band.at(lx, y, lz);
                            if (cell != SOURCE) {
                                continue;
                            }
                            boolean open = false;
                            for (int[] side : SIDES) {
                                byte neighbour = at(x + side[0], y, z + side[1]);
                                open |= neighbour == AIR || neighbour == OTHER;
                            }
                            byte below = band.at(lx, y - 1, lz);
                            open |= below == AIR || below == OTHER;
                            if (open) {
                                leaks++;
                            }
                        }
                    }
                }
            }
            return leaks;
        }

        private Band band(int x, int z) {
            return run.bands.get(key(x >> 4, z >> 4));
        }

        private int crossChunk() {
            int crossing = 0;
            for (InletStats inletStats : stats.values()) {
                crossing += inletStats.chunks.size() > 1 ? 1 : 0;
            }
            return crossing;
        }

        private int crossWindow() {
            int crossing = 0;
            for (InletStats inletStats : stats.values()) {
                crossing += inletStats.windows.size() > 1 ? 1 : 0;
            }
            return crossing;
        }

        private byte at(int x, int y, int z) {
            Band band = band(x, z);
            return band == null ? -1 : band.at(x & 15, y, z & 15);
        }

        private void example(String what, int x, int y, int z) {
            if (examples.size() < 40) {
                examples.add(what + " at " + x + "," + y + "," + z);
            }
        }

        private static long columnKey(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }

        private static final class InletStats {
            private int columns;
            private int wet;
            private int dry;
            private int leaks;
            private int missingWater;
            private int seaPlants;
            private int seaLayers;
            private int looseBeds;
            private int decorations;
            private int nonSource;
            private int waterAbove;
            private int roofMin = Integer.MAX_VALUE;
            private int roofViolations;
            private int roofBreaches;
            private int openRoofViolations;
            private int covered;
            private int open;
            private int disconnected;
            private final Set<Long> chunks = new TreeSet<>();
            private final Set<Long> windows = new TreeSet<>();

            private void add(InletStats other) {
                columns += other.columns;
                wet += other.wet;
                dry += other.dry;
                leaks += other.leaks;
                missingWater += other.missingWater;
                seaPlants += other.seaPlants;
                seaLayers += other.seaLayers;
                looseBeds += other.looseBeds;
                decorations += other.decorations;
                nonSource += other.nonSource;
                waterAbove += other.waterAbove;
                roofMin = Math.min(roofMin, other.roofMin);
                roofViolations += other.roofViolations;
                roofBreaches += other.roofBreaches;
                openRoofViolations += other.openRoofViolations;
                covered += other.covered;
                open += other.open;
                disconnected += other.disconnected;
                chunks.addAll(other.chunks);
            }
        }
    }
}
