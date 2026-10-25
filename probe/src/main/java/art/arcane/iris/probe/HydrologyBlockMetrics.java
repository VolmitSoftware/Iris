package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.raster.WaterRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

final class HydrologyBlockMetrics {
    private static final int BED_MARGIN = 2;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final HydrologyProbe.Inputs inputs;
    private final HydrologyRasterMetrics rasters;
    private final HydrologyWindows.WindowRange range;
    private final Consumer<String> examples;
    private final Consumer<String> failures;

    record Counts(HydrologyVoxels.WaterScan scan, long generated, long errors) {
    }

    interface Columns {
        boolean known(int x, int z);

        WaterRole role(int x, int z);

        int terrain(int x, int z);

        int natural(int x, int z);
    }

    HydrologyBlockMetrics(HydrologyProbe.Inputs inputs, HydrologyRasterMetrics rasters, HydrologyWindows.WindowRange range,
                          Consumer<String> examples, Consumer<String> failures) {
        this.inputs = inputs;
        this.rasters = rasters;
        this.range = range;
        this.examples = examples;
        this.failures = failures;
    }

    static boolean scanned(Columns columns, int x, int z) {
        return columns.role(x, z) != WaterRole.NONE || besideWater(columns, x, z);
    }

    static int scanFloor(Columns columns, int seaLevel, int x, int z) {
        int floor = columns.terrain(x, z) - BED_MARGIN;
        if (columns.role(x, z).wet() || columns.natural(x, z) >= seaLevel || besideWater(columns, x, z)) {
            return floor;
        }
        return Math.max(floor, seaLevel + 1);
    }

    private static boolean besideWater(Columns columns, int x, int z) {
        for (int[] offset : CARDINALS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (columns.known(nx, nz) && columns.role(nx, nz).wet()) {
                return true;
            }
        }
        return false;
    }

    Counts count(WindowKey key) throws InterruptedException {
        Columns columns = new Columns() {
            @Override
            public boolean known(int x, int z) {
                return range.containsBlock(x, z);
            }

            @Override
            public WaterRole role(int x, int z) {
                return rasters.role(x, z);
            }

            @Override
            public int terrain(int x, int z) {
                return rasters.terrain(x, z);
            }

            @Override
            public int natural(int x, int z) {
                return inputs.naturalHeight().applyAsInt(x, z);
            }
        };
        int minX = key.wx() * WindowKey.SIZE;
        int minZ = key.wz() * WindowKey.SIZE;
        List<int[]> scan = new ArrayList<>();
        Set<Long> chunks = new LinkedHashSet<>();
        for (int z = minZ; z < minZ + WindowKey.SIZE; z++) {
            for (int x = minX; x < minX + WindowKey.SIZE; x++) {
                if (!scanned(columns, x, z)) {
                    continue;
                }
                scan.add(new int[]{x, z, scanFloor(columns, inputs.settings().seaLevel(), x, z)});
                chunks.add(HydrologyVoxels.chunkKey(x >> 4, z >> 4));
                for (int[] offset : CARDINALS) {
                    chunks.add(HydrologyVoxels.chunkKey((x + offset[0]) >> 4, (z + offset[1]) >> 4));
                }
            }
        }
        List<Callable<HydrologyVoxels.ChunkBlocks>> tasks = new ArrayList<>(chunks.size());
        for (long chunk : chunks) {
            int chunkX = (int) (chunk >> 32);
            int chunkZ = (int) chunk;
            tasks.add(() -> inputs.blocks().generate(chunkX, chunkZ));
        }
        HydrologyVoxels.Volume volume = new HydrologyVoxels.Volume();
        long errors = 0L;
        for (Future<HydrologyVoxels.ChunkBlocks> future : inputs.workers().invokeAll(tasks)) {
            try {
                volume.put(future.get());
            } catch (ExecutionException failure) {
                errors++;
                failures.accept("chunk generation: " + HydrologyPlanHarness.cause(failure));
            }
        }
        HydrologyVoxels.WaterScan water = new HydrologyVoxels.WaterScan();
        for (int[] column : scan) {
            water.column(volume, column[0], column[1], column[2], inputs.worldHeight() - 1);
        }
        for (String example : water.examples) {
            examples.accept(example);
        }
        return new Counts(water, chunks.size(), errors);
    }
}
