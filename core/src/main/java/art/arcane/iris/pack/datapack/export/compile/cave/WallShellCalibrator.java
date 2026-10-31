package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;

final class WallShellCalibrator {
    private static final long SEED = 0x5EED_3A11L;
    private static final int PATCHES = 256;
    private static final int PATCH_CELLS = 3;
    private static final int SPREAD_CELLS = 1024;

    private final Map<String, NoiseDef> noises;
    private final DataVersion target;
    private final int cellXZ;
    private final int cellY;
    private double[] solid = new double[4096];
    private int solidCount;
    private long walls;

    private WallShellCalibrator(Map<String, NoiseDef> noises, DataVersion target, int cellXZ, int cellY) {
        this.noises = noises;
        this.target = target;
        this.cellXZ = cellXZ;
        this.cellY = cellY;
    }

    static double band(CaveInterior interior, Map<String, NoiseDef> noises, DataVersion target) {
        WallShellCalibrator calibrator = new WallShellCalibrator(noises, target, CaveFieldCompiler.CELL_XZ, CaveFieldCompiler.CELL_Y);
        SplittableRandom random = new SplittableRandom(SEED);
        for (int patch = 0; patch < PATCHES; patch++) {
            calibrator.sample(interior, random);
        }
        return calibrator.band();
    }

    private void sample(CaveInterior interior, SplittableRandom random) {
        int x0 = cellXZ * (random.nextInt(2 * SPREAD_CELLS) - SPREAD_CELLS);
        int z0 = cellXZ * (random.nextInt(2 * SPREAD_CELLS) - SPREAD_CELLS);
        int lowCell = Math.floorDiv(interior.minY(), cellY);
        int highCell = Math.max(lowCell, Math.floorDiv(interior.maxY(), cellY) - PATCH_CELLS);
        int y0 = cellY * (lowCell + random.nextInt(highCell - lowCell + 1));
        int corners = PATCH_CELLS + 1;
        double[][][] lattice = new double[corners][corners][corners];
        for (int i = 0; i < corners; i++) {
            for (int j = 0; j < corners; j++) {
                for (int k = 0; k < corners; k++) {
                    lattice[i][j][k] = FieldEvaluator.sample(interior.field(), noises, SEED, target, x0 + i * cellXZ, y0 + j * cellY, z0 + k * cellXZ);
                }
            }
        }
        int width = PATCH_CELLS * cellXZ;
        int height = PATCH_CELLS * cellY;
        double[][][] voxels = new double[width][height][width];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < width; z++) {
                    voxels[x][y][z] = interpolate(lattice, x, y, z);
                }
            }
        }
        for (int x = 1; x < width - 1; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 1; z < width - 1; z++) {
                    double value = voxels[x][y][z];
                    if (value < 0.0) {
                        continue;
                    }
                    add(value);
                    if (voxels[x - 1][y][z] < 0.0 || voxels[x + 1][y][z] < 0.0 || voxels[x][y][z - 1] < 0.0 || voxels[x][y][z + 1] < 0.0) {
                        walls++;
                    }
                }
            }
        }
    }

    private double interpolate(double[][][] lattice, int x, int y, int z) {
        int i = x / cellXZ;
        int j = y / cellY;
        int k = z / cellXZ;
        double fx = (x - i * cellXZ) / (double) cellXZ;
        double fy = (y - j * cellY) / (double) cellY;
        double fz = (z - k * cellXZ) / (double) cellXZ;
        double bottom = lerp(fz, lerp(fx, lattice[i][j][k], lattice[i + 1][j][k]), lerp(fx, lattice[i][j][k + 1], lattice[i + 1][j][k + 1]));
        double top = lerp(fz, lerp(fx, lattice[i][j + 1][k], lattice[i + 1][j + 1][k]), lerp(fx, lattice[i][j + 1][k + 1], lattice[i + 1][j + 1][k + 1]));
        return lerp(fy, bottom, top);
    }

    private void add(double value) {
        if (solidCount == solid.length) {
            solid = Arrays.copyOf(solid, solid.length * 2);
        }
        solid[solidCount++] = value;
    }

    private double band() {
        if (walls == 0L) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(solid, solidCount);
        Arrays.sort(sorted);
        int rank = (int) Math.min(solidCount - 1L, walls);
        return sorted[rank];
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
