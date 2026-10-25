package art.arcane.iris.probe;

import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CavePoolProbe {
    private static final String LOG_PREFIX = "[cave-pool-probe]";
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, -1, 1};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};
    private static final int UP = 5;

    private CavePoolProbe() {
    }

    record Configuration(File pack, String dimension, long seed, int minX, int maxX, int minZ, int maxZ, int threads, Path work) {
        static Configuration parse(String[] arguments) {
            if (arguments.length != 9) {
                throw new IllegalArgumentException("Expected: <pack> <dimension> <seed> <minChunkX> <maxChunkX> <minChunkZ> <maxChunkZ> "
                        + "<threads> <workParent>");
            }
            return new Configuration(new File(arguments[0]), arguments[1], Long.parseLong(arguments[2]), Integer.parseInt(arguments[3]),
                    Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]), Integer.parseInt(arguments[6]),
                    Integer.parseInt(arguments[7]), Path.of(arguments[8]));
        }

        Area area() {
            return new Area(minX, maxX, minZ, maxZ);
        }
    }

    record Area(int minX, int maxX, int minZ, int maxZ) {
        boolean inner(int chunkX, int chunkZ) {
            return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
        }

        List<long[]> chunks(int margin) {
            List<long[]> chunks = new ArrayList<>();
            for (int z = minZ - margin; z <= maxZ + margin; z++) {
                for (int x = minX - margin; x <= maxX + margin; x++) {
                    chunks.add(new long[]{x, z});
                }
            }
            return chunks;
        }
    }

    record Counts(int chunks, int pools, int checked, int guards, Map<String, Integer> fluids, List<Integer> areas, List<Integer> depths,
                  Map<Integer, Integer> chunkSpans, int airLeaks, int openLeaks, int skyLeaks, int foreignFluid, int waterloggableWalls,
                  int unsupportedGravity, int fluidAbove, int gravityCeilings, Map<String, Integer> replaced, List<String> examples) {
        long failures() {
            return (long) airLeaks + openLeaks + skyLeaks + foreignFluid + waterloggableWalls + unsupportedGravity + fluidAbove + gravityCeilings;
        }
    }

    static Counts check(Engine engine, Area area, ExecutorService workers) throws Exception {
        return count(survey(engine, area, workers), area);
    }

    public static void main(String[] arguments) {
        try {
            scan(Configuration.parse(arguments));
            System.exit(0);
        } catch (Throwable failure) {
            System.out.println(LOG_PREFIX + " FAIL");
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static RealPackProbeSupport.Workspace workspace(Configuration configuration) throws Exception {
        return RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(configuration.pack(), configuration.dimension(),
                LOG_PREFIX, null, configuration.work()));
    }

    private static void scan(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = workspace(configuration)) {
            Survey forward;
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "forward")) {
                Engine engine = session.engine();
                preplan(engine, configuration);
                forward = survey(engine, configuration.area(), null);
                requireQuiet(engine, "forward");
            }
            report(count(forward, configuration.area()));
            Map<Long, Long> shuffled;
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "shuffled")) {
                Engine engine = session.engine();
                preplan(engine, configuration);
                shuffled = hashes(engine, configuration);
                requireQuiet(engine, "shuffled");
            }
            compare(forward.hashes(), shuffled);
        }
    }

    private static void preplan(Engine engine, Configuration configuration) throws Exception {
        HydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        if (hydrology == null || !hydrology.enabled()) {
            return;
        }
        try {
            hydrology.prepareWindowsAround(configuration.minX() - 1, configuration.minZ() - 1, configuration.maxX() + 1,
                    configuration.maxZ() + 1, 1).get();
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Hydrology preplan failed", failure.getCause());
        }
    }

    private static void requireQuiet(Engine engine, String label) throws InterruptedException {
        List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
        if (!reports.isEmpty()) {
            RealPackProbeSupport.printReports(LOG_PREFIX, label, reports);
            IllegalStateException failure = new IllegalStateException(label + " generation reported " + reports.size() + " error(s)");
            reports.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    record Cell(long position, String fluid) {
    }

    record ChunkRecord(long[] open, long[] fluidOrAir, long[] gravity, long[] waterloggable, int[] roof) {
        boolean open(int localX, int y, int localZ) {
            return bit(open, localX, y, localZ);
        }

        boolean gravity(int localX, int y, int localZ) {
            return bit(gravity, localX, y, localZ);
        }

        boolean waterloggable(int localX, int y, int localZ) {
            return bit(waterloggable, localX, y, localZ);
        }

        boolean fluidOrAir(int localX, int y, int localZ) {
            return bit(fluidOrAir, localX, y, localZ);
        }

        private static boolean bit(long[] bits, int localX, int y, int localZ) {
            int index = (y << 8) | (localZ << 4) | localX;
            return (bits[index >>> 6] & (1L << (index & 63))) != 0L;
        }
    }

    record Survey(Map<Long, Long> hashes, Map<Long, ChunkRecord> chunks, List<Cell> pool, Map<String, Integer> replaced, int guards) {
    }

    private static Survey survey(Engine engine, Area area, ExecutorService workers) throws Exception {
        List<long[]> chunks = area.chunks(2);
        Map<Long, Long> hashes = new HashMap<>();
        Map<Long, ChunkRecord> records = new HashMap<>();
        List<Cell> pool = new ArrayList<>();
        Map<String, Integer> replaced = new TreeMap<>();
        int height = engine.getTarget().getHeight();
        List<Future<Hunk<NativeBlockState>>> generated = new ArrayList<>(chunks.size());
        if (workers != null) {
            for (long[] chunk : chunks) {
                generated.add(workers.submit(() -> generate(engine, (int) chunk[0], (int) chunk[1], height, hashes)));
            }
        }
        int guards = 0;
        for (int index = 0; index < chunks.size(); index++) {
            int chunkX = (int) chunks.get(index)[0];
            int chunkZ = (int) chunks.get(index)[1];
            Hunk<NativeBlockState> blocks;
            try {
                blocks = workers == null ? generate(engine, chunkX, chunkZ, height, hashes) : generated.get(index).get();
            } catch (ExecutionException failure) {
                throw new IllegalStateException("Failed to generate " + chunkX + "," + chunkZ, failure.getCause());
            }
            records.put(key(chunkX, chunkZ), record(blocks, height));
            guards += poolCells(engine, chunkX, chunkZ, blocks, pool, replaced);
            if (workers == null) {
                progress(index + 1, chunks.size());
            }
        }
        return new Survey(hashes, records, pool, replaced, guards);
    }

    private static Hunk<NativeBlockState> generate(Engine engine, int chunkX, int chunkZ, int height, Map<Long, Long> hashes) {
        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        try {
            engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to generate " + chunkX + "," + chunkZ, failure);
        }
        long hash = hash(blocks, biomes, height);
        synchronized (hashes) {
            hashes.put(key(chunkX, chunkZ), hash);
        }
        return blocks;
    }

    private static int poolCells(Engine engine, int chunkX, int chunkZ, Hunk<NativeBlockState> blocks, List<Cell> pool,
                                 Map<String, Integer> replaced) {
        MantleChunk<Matter> chunk = engine.getMantle().getMantle().useChunk(chunkX, chunkZ);
        int[] guards = new int[1];
        try {
            chunk.iterate(HydrologyCaveCell.class, (x, y, z, cell) -> {
                if (cell == null) {
                    return;
                }
                if (cell.action() == HydrologyCaveAction.SEAL_GUARD) {
                    guards[0]++;
                    return;
                }
                if (cell.action() != HydrologyCaveAction.POOL_SOURCE) {
                    return;
                }
                pool.add(new Cell(pack((chunkX << 4) + x, y, (chunkZ << 4) + z), cell.fluidProfileKey()));
                NativeBlockState state = blocks.get(x, y, z);
                if (!source(state, cell.fluidProfileKey())) {
                    replaced.merge(cell.fluidProfileKey() + "->" + (state == null ? "null" : state.key()), 1, Integer::sum);
                }
            });
        } finally {
            chunk.release();
        }
        return guards[0];
    }

    private static boolean source(NativeBlockState state, String fluid) {
        if (state == null || !state.isFluid()) {
            return false;
        }
        String level = IrisProceduralBlocks.propertyValue(state, "level");
        boolean still = level == null || "0".equals(level);
        boolean matches = "lava".equals(fluid) ? state.key().startsWith("minecraft:lava") : state.isWater();
        return still && matches;
    }

    private static ChunkRecord record(Hunk<NativeBlockState> blocks, int height) {
        long[] open = new long[height * 4];
        long[] fluidOrAir = new long[height * 4];
        long[] gravity = new long[height * 4];
        long[] waterloggable = new long[height * 4];
        int[] roof = new int[256];
        Arrays.fill(roof, -1);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    int index = (y << 8) | (z << 4) | x;
                    boolean air = state == null || state.isAir();
                    boolean fluid = !air && state.isFluid();
                    boolean solid = !air && !fluid && state.isSolid();
                    boolean logged = !air && !fluid && IrisProceduralBlocks.hasProperty(state, "waterlogged");
                    if (!solid && !fluid && !logged) {
                        open[index >>> 6] |= 1L << (index & 63);
                    }
                    if (logged) {
                        waterloggable[index >>> 6] |= 1L << (index & 63);
                    }
                    if (air || fluid) {
                        fluidOrAir[index >>> 6] |= 1L << (index & 63);
                    }
                    if (solid) {
                        roof[(z << 4) | x] = y;
                        if (IrisProceduralBlocks.isGravityAffected(state)) {
                            gravity[index >>> 6] |= 1L << (index & 63);
                        }
                    }
                }
            }
        }
        return new ChunkRecord(open, fluidOrAir, gravity, waterloggable, roof);
    }

    private static long hash(Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes, int height) {
        long hash = 0x9e3779b97f4a7c15L;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    NativeBiome biome = biomes.get(x, y, z);
                    hash = mix(hash ^ (state == null ? 0 : state.key().hashCode()));
                    hash = mix(hash ^ (biome == null ? 0 : biome.key().hashCode()));
                }
            }
        }
        return hash;
    }

    private static Map<Long, Long> hashes(Engine engine, Configuration configuration) throws Exception {
        List<long[]> chunks = new ArrayList<>(configuration.area().chunks(2));
        Collections.shuffle(chunks, new Random(configuration.seed() ^ 0x5DEECE66DL));
        ExecutorService executor = Executors.newFixedThreadPool(configuration.threads());
        try {
            List<Future<long[]>> futures = new ArrayList<>();
            for (long[] chunk : chunks) {
                futures.add(executor.submit(() -> {
                    int height = engine.getTarget().getHeight();
                    Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                    Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                    engine.generate((int) chunk[0] << 4, (int) chunk[1] << 4, blocks, biomes, true);
                    return new long[]{chunk[0], chunk[1], hash(blocks, biomes, height)};
                }));
            }
            Map<Long, Long> hashes = new HashMap<>();
            for (int index = 0; index < futures.size(); index++) {
                long[] result = futures.get(index).get();
                hashes.put(key((int) result[0], (int) result[1]), result[2]);
                progress(index + 1, futures.size());
            }
            return hashes;
        } finally {
            executor.shutdown();
            executor.awaitTermination(60L, TimeUnit.SECONDS);
        }
    }

    private static void compare(Map<Long, Long> forward, Map<Long, Long> shuffled) {
        int mismatches = 0;
        List<String> examples = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : forward.entrySet()) {
            if (!entry.getValue().equals(shuffled.get(entry.getKey()))) {
                mismatches++;
                if (examples.size() < 16) {
                    examples.add((int) (entry.getKey() >> 32) + "," + (int) (long) entry.getKey());
                }
            }
        }
        long signature = 0L;
        for (Map.Entry<Long, Long> entry : new TreeMap<>(forward).entrySet()) {
            signature = mix(signature ^ entry.getKey() ^ mix(entry.getValue()));
        }
        System.out.printf(Locale.ROOT, "CAVE_POOL_ORDER chunks=%d mismatches=%d signature=%016x examples=%s%n", forward.size(), mismatches,
                signature, examples);
    }

    static Counts count(Survey survey, Area area) {
        Map<Long, Integer> index = new HashMap<>();
        for (int cell = 0; cell < survey.pool().size(); cell++) {
            index.put(survey.pool().get(cell).position(), cell);
        }
        int[] parent = new int[survey.pool().size()];
        for (int cell = 0; cell < parent.length; cell++) {
            parent[cell] = cell;
        }
        for (int cell = 0; cell < parent.length; cell++) {
            long position = survey.pool().get(cell).position();
            for (int direction = 0; direction < 6; direction++) {
                Integer other = index.get(pack(x(position) + DX[direction], y(position) + DY[direction], z(position) + DZ[direction]));
                if (other != null) {
                    parent[find(parent, cell)] = find(parent, other);
                }
            }
        }
        Map<Integer, int[]> levels = new HashMap<>();
        for (int cell = 0; cell < parent.length; cell++) {
            int root = find(parent, cell);
            int y = y(survey.pool().get(cell).position());
            int[] range = levels.computeIfAbsent(root, ignored -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
            range[0] = Math.min(range[0], y);
            range[1] = Math.max(range[1], y);
        }
        int airLeaks = 0;
        int openLeaks = 0;
        int skyLeaks = 0;
        int foreignFluid = 0;
        int unsupportedGravity = 0;
        int waterloggableWalls = 0;
        int fluidAbove = 0;
        int gravityCeilings = 0;
        int checked = 0;
        Map<Integer, Set<Long>> columns = new HashMap<>();
        Map<Integer, Set<Long>> spans = new HashMap<>();
        Map<Integer, String> fluids = new HashMap<>();
        Set<Integer> innerPools = new HashSet<>();
        List<String> examples = new ArrayList<>();
        for (int cell = 0; cell < parent.length; cell++) {
            Cell pool = survey.pool().get(cell);
            long position = pool.position();
            int x = x(position);
            int y = y(position);
            int z = z(position);
            int root = find(parent, cell);
            columns.computeIfAbsent(root, ignored -> new HashSet<>()).add(key(x, z));
            spans.computeIfAbsent(root, ignored -> new HashSet<>()).add(key(x >> 4, z >> 4));
            fluids.put(root, pool.fluid());
            if (!area.inner(x >> 4, z >> 4)) {
                continue;
            }
            innerPools.add(root);
            checked++;
            int level = levels.get(root)[1];
            ChunkRecord own = survey.chunks().get(key(x >> 4, z >> 4));
            if (y >= own.roof()[((z & 15) << 4) | (x & 15)]) {
                skyLeaks++;
            }
            for (int direction = 0; direction < 6; direction++) {
                int neighbourY = y + DY[direction];
                int neighbourX = x + DX[direction];
                int neighbourZ = z + DZ[direction];
                if (index.containsKey(pack(neighbourX, neighbourY, neighbourZ))) {
                    continue;
                }
                ChunkRecord record = survey.chunks().get(key(neighbourX >> 4, neighbourZ >> 4));
                int localX = neighbourX & 15;
                int localZ = neighbourZ & 15;
                if (direction == UP && neighbourY > level) {
                    if (record.fluidOrAir(localX, neighbourY, localZ) && !record.open(localX, neighbourY, localZ)) {
                        fluidAbove++;
                        if (examples.size() < 12) {
                            examples.add("fluid-above " + neighbourX + "," + neighbourY + "," + neighbourZ);
                        }
                    } else if (record.gravity(localX, neighbourY, localZ)) {
                        gravityCeilings++;
                        if (examples.size() < 12) {
                            examples.add("gravity-ceiling " + neighbourX + "," + neighbourY + "," + neighbourZ);
                        }
                    }
                    continue;
                }
                if (record.open(localX, neighbourY, localZ)) {
                    if (record.fluidOrAir(localX, neighbourY, localZ)) {
                        airLeaks++;
                    } else {
                        openLeaks++;
                    }
                    if (examples.size() < 12) {
                        examples.add(x + "," + y + "," + z + "->" + neighbourX + "," + neighbourY + "," + neighbourZ);
                    }
                } else if (record.fluidOrAir(localX, neighbourY, localZ)) {
                    foreignFluid++;
                } else if (record.waterloggable(localX, neighbourY, localZ)) {
                    waterloggableWalls++;
                    if (examples.size() < 12) {
                        examples.add("waterloggable " + neighbourX + "," + neighbourY + "," + neighbourZ);
                    }
                } else if (record.gravity(localX, neighbourY, localZ) && neighbourY > 0
                        && (index.containsKey(pack(neighbourX, neighbourY - 1, neighbourZ))
                        || record.open(localX, neighbourY - 1, localZ) || record.fluidOrAir(localX, neighbourY - 1, localZ))) {
                    unsupportedGravity++;
                    if (examples.size() < 12) {
                        examples.add("gravity " + neighbourX + "," + neighbourY + "," + neighbourZ);
                    }
                }
            }
        }
        List<Integer> areas = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        Map<String, Integer> byFluid = new TreeMap<>();
        Map<Integer, Integer> chunkSpans = new TreeMap<>();
        for (int root : innerPools) {
            chunkSpans.merge(spans.get(root).size(), 1, Integer::sum);
            areas.add(columns.get(root).size());
            int[] range = levels.get(root);
            depths.add(range[1] - range[0] + 1);
            byFluid.merge(fluids.get(root), 1, Integer::sum);
        }
        Collections.sort(areas);
        Collections.sort(depths);
        return new Counts(survey.hashes().size(), innerPools.size(), checked, survey.guards(), byFluid, areas, depths, chunkSpans, airLeaks,
                openLeaks, skyLeaks, foreignFluid, waterloggableWalls, unsupportedGravity, fluidAbove, gravityCeilings, survey.replaced(),
                examples);
    }

    private static void report(Counts counts) {
        List<Integer> areas = counts.areas();
        Map<Integer, Integer> depthHistogram = new TreeMap<>();
        counts.depths().forEach(depth -> depthHistogram.merge(depth, 1, Integer::sum));
        long totalArea = areas.stream().mapToLong(Integer::longValue).sum();
        System.out.printf(Locale.ROOT, "CAVE_POOL_SURVEY chunks=%d pools=%d pool_cells_checked=%d guards=%d fluids=%s%n",
                counts.chunks(), counts.pools(), counts.checked(), counts.guards(), counts.fluids());
        System.out.printf(Locale.ROOT, "CAVE_POOL_AREA total=%d min=%d median=%d mean=%.1f max=%d%n", totalArea,
                areas.isEmpty() ? 0 : areas.get(0), areas.isEmpty() ? 0 : areas.get(areas.size() / 2),
                areas.isEmpty() ? 0D : totalArea / (double) areas.size(), areas.isEmpty() ? 0 : areas.get(areas.size() - 1));
        System.out.printf(Locale.ROOT, "CAVE_POOL_DEPTH histogram=%s%n", depthHistogram);
        System.out.printf(Locale.ROOT, "CAVE_POOL_CHUNKS_SPANNED histogram=%s%n", counts.chunkSpans());
        System.out.printf(Locale.ROOT, "CAVE_POOL_CONTAINMENT air_leaks=%d open_leaks=%d sky_leaks=%d foreign_fluid_contacts=%d "
                        + "waterloggable_walls=%d unsupported_gravity_walls=%d fluid_above_surface=%d gravity_ceilings=%d replaced_cells=%s "
                        + "examples=%s%n", counts.airLeaks(), counts.openLeaks(), counts.skyLeaks(), counts.foreignFluid(),
                counts.waterloggableWalls(), counts.unsupportedGravity(), counts.fluidAbove(), counts.gravityCeilings(), counts.replaced(),
                counts.examples());
    }

    private static int find(int[] parent, int cell) {
        while (parent[cell] != cell) {
            parent[cell] = parent[parent[cell]];
            cell = parent[cell];
        }
        return cell;
    }

    private static void progress(int done, int total) {
        if (done == total || done % 1024 == 0) {
            System.out.println(LOG_PREFIX + " " + done + "/" + total);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 38) | ((long) (z + (1 << 25)) << 12) | (y & 4095);
    }

    private static int x(long position) {
        return (int) (position >>> 38) - (1 << 25);
    }

    private static int z(long position) {
        return (int) ((position >>> 12) & ((1L << 26) - 1)) - (1 << 25);
    }

    private static int y(long position) {
        return (int) (position & 4095);
    }

    private static long mix(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
