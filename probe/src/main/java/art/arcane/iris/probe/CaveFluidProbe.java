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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CaveFluidProbe {
    static final int PATCH_CHUNKS = 8;
    static final int PATCH_SPACING = 64;
    static final int LONG_EXTENT = 32;
    static final int[] BAND_FLOORS = {50, 0, -64, -128, -192};
    static final String[] BAND_NAMES = {"y>=50", "0..50", "-64..0", "-128..-64", "-192..-128", "y<-192"};
    private static final String LOG_PREFIX = "[cave-fluid-probe]";
    private static final byte NONE = 0;
    private static final byte WATER = 1;
    private static final byte LAVA = 2;
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private CaveFluidProbe() {
    }

    record Configuration(File pack, String dimension, long seed, int patchesPerSide, int threads, Path work) {
        static Configuration parse(String[] arguments) {
            if (arguments.length != 6) {
                throw new IllegalArgumentException("Expected: <pack> <dimension> <seed> <patchesPerSide> <threads> <workParent>");
            }
            return new Configuration(new File(arguments[0]), arguments[1], Long.parseLong(arguments[2]), Integer.parseInt(arguments[3]),
                    Integer.parseInt(arguments[4]), Path.of(arguments[5]));
        }
    }

    static final class Grid {
        final int span;
        final int height;
        final byte[] kinds;
        final byte[] pools;
        final int[] roofs;

        Grid(int span, int height) {
            this.span = span;
            this.height = height;
            this.kinds = new byte[span * span * height];
            this.pools = new byte[span * span * height];
            this.roofs = new int[span * span];
        }

        int index(int x, int y, int z) {
            return (y * span + z) * span + x;
        }
    }

    record Body(byte kind, boolean pool, int lowestY, int extent, int cells) {
    }

    static final class Tally {
        final int[] pools = new int[BAND_NAMES.length];
        final int[] lavaPools = new int[BAND_NAMES.length];
        final int[] lavaBodies = new int[BAND_NAMES.length];
        final int[] longLavaBodies = new int[BAND_NAMES.length];
        final int[] longWaterBodies = new int[BAND_NAMES.length];
        final int[] springs = new int[BAND_NAMES.length];
        int lavaSprings;
        int waterSprings;
        int unplacedSprings;
        int longestLava;
        int floorLavaBodies;
        int chunks;
    }

    static List<Body> bodies(Grid grid) {
        List<Body> bodies = new ArrayList<>();
        boolean[] seen = new boolean[grid.kinds.length];
        int[] stack = new int[1024];
        for (int start = 0; start < grid.kinds.length; start++) {
            byte kind = grid.kinds[start];
            if (kind == NONE || seen[start]) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int lowest = Integer.MAX_VALUE;
            int cells = 0;
            boolean pool = false;
            while (top > 0) {
                int cell = stack[--top];
                int column = cell % (grid.span * grid.span);
                int x = column % grid.span;
                int z = column / grid.span;
                int y = cell / (grid.span * grid.span);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                lowest = Math.min(lowest, y);
                cells++;
                pool |= grid.pools[cell] != 0;
                for (int direction = 0; direction < 6; direction++) {
                    int nextX = x + DX[direction];
                    int nextY = y + DY[direction];
                    int nextZ = z + DZ[direction];
                    if (nextX < 0 || nextZ < 0 || nextY < 0 || nextX >= grid.span || nextZ >= grid.span || nextY >= grid.height) {
                        continue;
                    }
                    int next = grid.index(nextX, nextY, nextZ);
                    if (!seen[next] && grid.kinds[next] == kind) {
                        seen[next] = true;
                        if (top == stack.length) {
                            stack = Arrays.copyOf(stack, stack.length * 2);
                        }
                        stack[top++] = next;
                    }
                }
            }
            bodies.add(new Body(kind, pool, lowest, Math.max(maxX - minX, maxZ - minZ) + 1, cells));
        }
        return bodies;
    }

    static int band(int worldY) {
        for (int band = 0; band < BAND_FLOORS.length; band++) {
            if (worldY >= BAND_FLOORS[band]) {
                return band;
            }
        }
        return BAND_FLOORS.length;
    }

    static void add(Tally tally, List<Body> bodies, int minimumY, int caveLavaHeight) {
        for (Body body : bodies) {
            int band = band(body.lowestY() + minimumY);
            if (body.pool()) {
                tally.pools[band]++;
                if (body.kind() == LAVA) {
                    tally.lavaPools[band]++;
                }
            }
            if (body.kind() == LAVA) {
                if (!body.pool() && body.lowestY() <= caveLavaHeight) {
                    tally.floorLavaBodies++;
                    continue;
                }
                tally.lavaBodies[band]++;
                tally.longestLava = Math.max(tally.longestLava, body.extent());
                if (body.extent() >= LONG_EXTENT) {
                    tally.longLavaBodies[band]++;
                }
            } else if (body.extent() >= LONG_EXTENT) {
                tally.longWaterBodies[band]++;
            }
        }
    }

    public static void main(String[] arguments) {
        int exitCode = 1;
        try {
            scan(Configuration.parse(arguments));
            exitCode = 0;
        } catch (Throwable failure) {
            System.out.println(LOG_PREFIX + " FAIL");
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    private static void scan(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                configuration.pack(), configuration.dimension(), LOG_PREFIX, null, configuration.work()));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), false, "survey")) {
            Engine engine = session.engine();
            Tally tally = new Tally();
            ExecutorService workers = Executors.newFixedThreadPool(configuration.threads());
            try {
                int side = configuration.patchesPerSide();
                for (int patchZ = 0; patchZ < side; patchZ++) {
                    for (int patchX = 0; patchX < side; patchX++) {
                        patch(engine, (patchX - side / 2) * PATCH_SPACING, (patchZ - side / 2) * PATCH_SPACING, workers, tally);
                    }
                    System.out.println(LOG_PREFIX + " row " + (patchZ + 1) + "/" + side);
                }
            } finally {
                workers.shutdown();
                workers.awaitTermination(60L, TimeUnit.SECONDS);
            }
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
            if (!reports.isEmpty()) {
                RealPackProbeSupport.printReports(LOG_PREFIX, "survey", reports);
                throw new IllegalStateException("Generation reported " + reports.size() + " error(s)");
            }
            report(configuration.seed(), tally);
        }
    }

    private static void patch(Engine engine, int minX, int minZ, ExecutorService workers, Tally tally) throws Exception {
        HydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        if (hydrology != null && hydrology.enabled()) {
            hydrology.prepareWindowsAround(minX, minZ, minX + PATCH_CHUNKS - 1, minZ + PATCH_CHUNKS - 1, 1).get();
        }
        int height = engine.getTarget().getHeight();
        int minimumY = engine.getWorld().minHeight();
        Grid grid = new Grid(PATCH_CHUNKS * 16, height);
        List<Future<?>> futures = new ArrayList<>();
        for (int offsetZ = 0; offsetZ < PATCH_CHUNKS; offsetZ++) {
            for (int offsetX = 0; offsetX < PATCH_CHUNKS; offsetX++) {
                int chunkX = minX + offsetX;
                int chunkZ = minZ + offsetZ;
                int originX = offsetX * 16;
                int originZ = offsetZ * 16;
                futures.add(workers.submit(() -> {
                    chunk(engine, chunkX, chunkZ, originX, originZ, grid, minimumY, tally);
                    return null;
                }));
            }
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException failure) {
                throw new IllegalStateException("Patch at chunk " + minX + "," + minZ + " failed", failure.getCause());
            }
        }
        tally.chunks += PATCH_CHUNKS * PATCH_CHUNKS;
        add(tally, bodies(grid), minimumY, engine.getDimension().getCaveLavaHeight());
    }

    private static void chunk(Engine engine, int chunkX, int chunkZ, int originX, int originZ, Grid grid, int minimumY, Tally tally) {
        int height = grid.height;
        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        try {
            engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to generate " + chunkX + "," + chunkZ, failure);
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int roof = -1;
                for (int y = height - 1; y >= 0; y--) {
                    NativeBlockState state = blocks.get(x, y, z);
                    byte kind = kind(state);
                    if (roof < 0 && kind == NONE && state != null && !state.isAir() && state.isSolid()) {
                        roof = y;
                    }
                    if (kind != NONE && y < roof) {
                        grid.kinds[grid.index(originX + x, y, originZ + z)] = kind;
                    }
                }
            }
        }
        MantleChunk<Matter> mantle = engine.getMantle().getMantle().useChunk(chunkX, chunkZ);
        try {
            mantle.iterate(HydrologyCaveCell.class, (x, y, z, cell) -> {
                if (cell == null) {
                    return;
                }
                if (cell.action() == HydrologyCaveAction.POOL_SOURCE) {
                    grid.pools[grid.index(originX + x, y, originZ + z)] = 1;
                } else if (cell.action() == HydrologyCaveAction.SPRING_SOURCE) {
                    int index = grid.index(originX + x, y, originZ + z);
                    byte kind = kind(blocks.get(x, y, z));
                    grid.kinds[index] = NONE;
                    synchronized (tally) {
                        tally.springs[band(y + minimumY)]++;
                        if (kind == LAVA) {
                            tally.lavaSprings++;
                        } else if (kind == WATER) {
                            tally.waterSprings++;
                        } else {
                            tally.unplacedSprings++;
                        }
                    }
                }
            });
        } finally {
            mantle.release();
        }
    }

    private static byte kind(NativeBlockState state) {
        if (state == null || state.isAir()) {
            return NONE;
        }
        String material = IrisProceduralBlocks.materialKey(state);
        if (material.endsWith("lava")) {
            return LAVA;
        }
        return material.endsWith("water") ? WATER : NONE;
    }

    private static void report(long seed, Tally tally) {
        double per = 1000D / tally.chunks;
        System.out.printf(Locale.ROOT, "CAVE_FLUID seed=%d chunks=%d lava_springs=%d water_springs=%d unplaced_springs=%d floor_lava_bodies=%d longest_lava=%d%n",
                seed, tally.chunks, tally.lavaSprings, tally.waterSprings, tally.unplacedSprings, tally.floorLavaBodies, tally.longestLava);
        for (int band = 0; band < BAND_NAMES.length; band++) {
            System.out.printf(Locale.ROOT, "CAVE_FLUID_BAND seed=%d band=%s pools=%.1f lava_pools=%.1f lava_bodies=%.1f lava_long=%.1f water_long=%.1f springs=%.1f%n",
                    seed, BAND_NAMES[band], tally.pools[band] * per, tally.lavaPools[band] * per, tally.lavaBodies[band] * per,
                    tally.longLavaBodies[band] * per, tally.longWaterBodies[band] * per, tally.springs[band] * per);
        }
    }
}
