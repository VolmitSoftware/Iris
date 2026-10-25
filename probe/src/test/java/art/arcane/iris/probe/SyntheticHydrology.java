package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanners;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.UnaryOperator;

final class SyntheticHydrology implements AutoCloseable {
    static final long SEED = 1337L;
    static final int SEA_LEVEL = 40;
    static final int HEIGHT = 320;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;
    private static final long SALT_NOISE = 0x4e4f495345L;
    private static final byte FALLING_WATER = (byte) (HydrologyVoxels.WATER + 8);

    private final HydrologySettings settings;
    private final Terrain terrain;
    private final ForkJoinPool planningPool;
    private final ExecutorService workers;
    private final HydrologyRuntime runtime;

    SyntheticHydrology() {
        this.settings = HydrologySettings.compile(new IrisHydrology().setEnabled(true), SEED, SEA_LEVEL, 0, HEIGHT);
        this.terrain = new Terrain();
        this.planningPool = new ForkJoinPool(4);
        this.workers = Executors.newFixedThreadPool(4);
        HydrologyPlanners planners = HydrologyPlanners.create(settings, terrain);
        this.runtime = new HydrologyRuntime(settings, HydrologyPlanCache.create(settings, planners.regions(), planners.windows(), planningPool,
                () -> false), terrain::naturalHeight, () -> false, System::nanoTime);
    }

    HydrologyProbe.Inputs inputs() {
        return inputs(UnaryOperator.identity());
    }

    HydrologyProbe.Inputs inputs(UnaryOperator<HydrologyVoxels.ChunkBlocks> edit) {
        return inputs(edit, (key, plan) -> new HydrologyUnderground.Counts(plan.inlets().size(), 0L, 0, 0L, List.of()));
    }

    HydrologyProbe.Inputs inputs(UnaryOperator<HydrologyVoxels.ChunkBlocks> edit, HydrologyUnderground.Source underground) {
        return new HydrologyProbe.Inputs(settings, terrain, terrain::naturalHeight, planningPool, workers,
                (chunkX, chunkZ) -> edit.apply(blocks(chunkX, chunkZ)), underground, HEIGHT);
    }

    ChunkWaterRaster raster(int chunkX, int chunkZ) {
        return runtime.raster(chunkX, chunkZ);
    }

    int naturalHeight(int x, int z) {
        return terrain.naturalHeight(x, z);
    }

    int terrainHeight(int x, int z) {
        ChunkWaterRaster raster = raster(x >> 4, z >> 4);
        return raster.empty() ? terrain.naturalHeight(x, z) : raster.terrainHeight(x & 15, z & 15);
    }

    private HydrologyVoxels.ChunkBlocks blocks(int chunkX, int chunkZ) {
        HydrologyVoxels.ChunkBlocks blocks = HydrologyVoxels.ChunkBlocks.empty(chunkX, chunkZ, HEIGHT);
        ChunkWaterRaster own = raster(chunkX, chunkZ);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                int ground = terrainHeight(x, z);
                int head = own.waterHead(lx, lz);
                blocks.heads()[(lz << 4) | lx] = head;
                for (int y = 0; y <= ground; y++) {
                    blocks.set(lx, y, lz, HydrologyVoxels.SOLID);
                }
                int surface = head != Integer.MIN_VALUE ? head : ground < SEA_LEVEL ? SEA_LEVEL : ground;
                for (int y = ground + 1; y <= surface; y++) {
                    blocks.set(lx, y, lz, HydrologyVoxels.WATER);
                }
                if (head != Integer.MIN_VALUE) {
                    for (int y = head + 1; y <= highestNeighbour(x, z); y++) {
                        blocks.set(lx, y, lz, FALLING_WATER);
                    }
                }
            }
        }
        return blocks;
    }

    private int highestNeighbour(int x, int z) {
        int highest = Integer.MIN_VALUE;
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            highest = Math.max(highest, raster(nx >> 4, nz >> 4).waterHead(nx & 15, nz & 15));
        }
        return highest;
    }

    @Override
    public void close() {
        runtime.close();
        workers.shutdownNow();
        planningPool.shutdownNow();
    }

    static final class Terrain implements HydrologyTerrainSource {
        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                float value = (float) height(xs[index], zs[index]);
                out.height()[index] = value;
                out.ocean()[index] = value <= SEA_LEVEL;
                out.rainfall()[index] = 1f;
                out.pondDensity()[index] = 1f;
                out.permission()[index] = ALLOW_ALL;
                out.fluidProfile()[index] = null;
            }
        }

        int naturalHeight(int x, int z) {
            return Math.round((float) height(x, z));
        }

        @Override
        public int seaLevel() {
            return SEA_LEVEL;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return HEIGHT;
        }

        private static double height(double x, double z) {
            double height = 70D;
            double amplitude = 60D;
            double wavelength = 4096D;
            for (int octave = 0; octave < 6; octave++) {
                height += amplitude * noise(SEED + octave, x / wavelength, z / wavelength);
                amplitude *= 0.5D;
                wavelength *= 0.5D;
            }
            return height;
        }

        private static double noise(long seed, double x, double z) {
            int x0 = (int) Math.floor(x);
            int z0 = (int) Math.floor(z);
            double fx = smooth(x - x0);
            double fz = smooth(z - z0);
            double top = lerp(corner(seed, x0, z0), corner(seed, x0 + 1, z0), fx);
            double bottom = lerp(corner(seed, x0, z0 + 1), corner(seed, x0 + 1, z0 + 1), fx);
            return lerp(top, bottom, fz);
        }

        private static double corner(long seed, int x, int z) {
            return 2D * CellHash.unit(CellHash.mix(seed, SALT_NOISE, x, z)) - 1D;
        }

        private static double smooth(double t) {
            return t * t * (3D - 2D * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
