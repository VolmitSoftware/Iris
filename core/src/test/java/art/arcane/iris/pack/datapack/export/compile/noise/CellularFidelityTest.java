package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CellularFidelityTest {
    private static final String NAMESPACE = "iris_export";
    private static final int SIZE = 2048;
    private static final int HEX_SIZE = 1024;
    private static final int REALIZATIONS = 8;
    private static final int DISTRIBUTION_STRIDE = 2;
    private static final double HEX_BLOCKS = 32.0D;
    private static final double CELL = 64.0D;
    private static final long WORLD_SEED = 9137L;
    private static final long IRIS_SEED = 9137L;
    private static final double TIE_TOLERANCE = 1.0e-4D;
    private static final double CENTRE_TOLERANCE = 1.0e-3D;
    private static RegionStats vanillaCells;
    private static RegionStats irisCells;

    @Test
    public void cellularFlatAreaWithinHalfPercent() {
        assertEquals("Flat area percent", irisCells().flatPercent(), vanillaCells().flatPercent(), 0.5D);
    }

    @Test
    public void cellCountWithin2Percent() {
        double iris = irisCells().regions();
        assertEquals("Cell count", iris, vanillaCells().regions(), 0.02D * iris);
    }

    @Test
    public void neighbourDegreeWithin0_05() {
        assertEquals("Mean neighbour degree", irisCells().meanDegree(), vanillaCells().meanDegree(), 0.05D);
    }

    @Test
    public void vascularDistributionW1Within2Percent() {
        double distance = styleDistance(NoiseStyle.VASCULAR);
        assertTrue("VASCULAR W1 is " + distance + " of the Iris deviation", distance <= 0.02D);
    }

    @Test
    public void globDistributionW1Within2Percent() {
        double distance = styleDistance(NoiseStyle.GLOB);
        assertTrue("GLOB W1 is " + distance + " of the Iris deviation", distance <= 0.02D);
    }

    @Test
    public void hexCentresMatchExceptTies() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        HexLattice.Centers centers = HexLattice.centers("fidelity/hex", HEX_BLOCKS, null, null);
        Map<String, NoiseDef> noises = FieldWalk.noiseDefinitions(registry);
        double rootThree = Math.sqrt(3.0D);
        int mismatches = 0;
        for (int z = 0; z < HEX_SIZE; z++) {
            for (int x = 0; x < HEX_SIZE; x++) {
                double hexX = x / HEX_BLOCKS;
                double hexZ = z / HEX_BLOCKS;
                double[] iris = irisCentre(hexX, hexZ, rootThree);
                double vanillaX = sample(centers.x(), noises, x, z);
                double vanillaZ = sample(centers.z(), noises, x, z);
                boolean same = Math.abs(vanillaX - iris[0]) <= CENTRE_TOLERANCE && Math.abs(vanillaZ - iris[1]) <= CENTRE_TOLERANCE;
                double vanillaDistance = squared(hexX - vanillaX) + squared(hexZ - vanillaZ);
                double irisDistance = squared(hexX - iris[0]) + squared(hexZ - iris[1]);
                if (!same && Math.abs(vanillaDistance - irisDistance) > TIE_TOLERANCE) {
                    mismatches++;
                }
            }
        }
        assertTrue("Hex centres differ on " + mismatches + " pixels", mismatches <= 0.0005D * HEX_SIZE * HEX_SIZE);
    }

    private static synchronized RegionStats vanillaCells() {
        if (vanillaCells == null) {
            NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
            Field value = CellularLibrary.build("fidelity/cellular", CELL, null, null, CellularMode.GRID_3X3, registry).cellValue();
            Map<String, NoiseDef> noises = FieldWalk.noiseDefinitions(registry);
            double[][] grid = new double[SIZE][SIZE];
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    grid[z][x] = sample(value, noises, x, z);
                }
            }
            vanillaCells = RegionStats.of(grid);
        }
        return vanillaCells;
    }

    private static synchronized RegionStats irisCells() {
        if (irisCells == null) {
            CNG iris = NoiseStyle.CELLULAR.create(new RNG(IRIS_SEED));
            double[][] grid = new double[SIZE][SIZE];
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    grid[z][x] = iris.noise(x, z);
                }
            }
            irisCells = RegionStats.of(grid);
        }
        return irisCells;
    }

    private static double styleDistance(NoiseStyle style) {
        int samplesPerAxis = SIZE / DISTRIBUTION_STRIDE;
        int samples = samplesPerAxis * samplesPerAxis;
        double[] vanillaValues = new double[samples * REALIZATIONS];
        double[] irisValues = new double[samples * REALIZATIONS];
        for (int realization = 0; realization < REALIZATIONS; realization++) {
            NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
            DefaultCellularStyleLowering lowering = new DefaultCellularStyleLowering(new ArrayList<>(), (nested, salt, nestedRegistry, capabilities) -> {
                throw new AssertionError("Unexpected nested style " + nested.getStyle());
            }, CellularMode.GRID_3X3);
            Field field = lowering.lower(new IrisGeneratorStyle(style), realization + 1L, registry, ExportCapabilities.forTarget(DataVersion.V26_3));
            Map<String, NoiseDef> noises = FieldWalk.noiseDefinitions(registry);
            CNG iris = style.create(new RNG(IRIS_SEED + realization));
            int offset = realization * samples;
            for (int row = 0; row < samplesPerAxis; row++) {
                for (int column = 0; column < samplesPerAxis; column++) {
                    int x = column * DISTRIBUTION_STRIDE;
                    int z = row * DISTRIBUTION_STRIDE;
                    vanillaValues[offset + row * samplesPerAxis + column] = sample(field, noises, x, z);
                    irisValues[offset + row * samplesPerAxis + column] = iris.noise(x, z);
                }
            }
        }
        return wasserstein(irisValues, vanillaValues) / deviation(irisValues);
    }

    private static double[] irisCentre(double hexX, double hexZ, double rootThree) {
        double q = rootThree / 3.0D * hexX - hexZ / 3.0D;
        double r = 2.0D / 3.0D * hexZ;
        double cube = -q - r;
        long roundedQ = Math.round(q);
        long roundedR = Math.round(r);
        long roundedCube = Math.round(cube);
        double qDifference = Math.abs(roundedQ - q);
        double rDifference = Math.abs(roundedR - r);
        double cubeDifference = Math.abs(roundedCube - cube);
        boolean fixQ = qDifference > cubeDifference && qDifference > rDifference;
        long centerQ = fixQ ? -roundedCube - roundedR : roundedQ;
        long centerR = !fixQ && cubeDifference <= rDifference ? -roundedQ - roundedCube : roundedR;
        return new double[]{rootThree * (centerQ + centerR * 0.5D), 1.5D * centerR};
    }

    private static double sample(Field field, Map<String, NoiseDef> noises, int x, int z) {
        return FieldEvaluator.sample(field, noises, WORLD_SEED, DataVersion.V26_3, x, 0, z);
    }

    private static double wasserstein(double[] first, double[] second) {
        double[] left = first.clone();
        double[] right = second.clone();
        Arrays.sort(left);
        Arrays.sort(right);
        double sum = 0.0D;
        for (int index = 0; index < left.length; index++) {
            sum += Math.abs(left[index] - right[index]);
        }
        return sum / left.length;
    }

    private static double deviation(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0D);
        double squares = 0.0D;
        for (double value : values) {
            squares += squared(value - mean);
        }
        return Math.sqrt(squares / values.length);
    }

    private static double squared(double value) {
        return value * value;
    }

    private record RegionStats(int regions, double meanDegree, double flatPercent) {
        private static RegionStats of(double[][] grid) {
            int size = grid.length;
            int[][] labels = new int[size][size];
            int count = 0;
            int[] queue = new int[size * size];
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (labels[z][x] == 0) {
                        count++;
                        flood(grid, labels, queue, x, z, count);
                    }
                }
            }
            List<Set<Integer>> neighbours = new ArrayList<>(count + 1);
            for (int index = 0; index <= count; index++) {
                neighbours.add(new HashSet<>());
            }
            boolean[] border = new boolean[count + 1];
            long flat = 0L;
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int label = labels[z][x];
                    if (x == 0 || z == 0 || x == size - 1 || z == size - 1) {
                        border[label] = true;
                    }
                    if (x + 1 < size && labels[z][x + 1] != label) {
                        neighbours.get(label).add(labels[z][x + 1]);
                        neighbours.get(labels[z][x + 1]).add(label);
                    }
                    if (z + 1 < size && labels[z + 1][x] != label) {
                        neighbours.get(label).add(labels[z + 1][x]);
                        neighbours.get(labels[z + 1][x]).add(label);
                    }
                    if (x + 1 < size && z + 1 < size && grid[z][x] == grid[z][x + 1] && grid[z][x] == grid[z + 1][x]) {
                        flat++;
                    }
                }
            }
            long degree = 0L;
            int interior = 0;
            for (int label = 1; label <= count; label++) {
                if (!border[label]) {
                    interior++;
                    degree += neighbours.get(label).size();
                }
            }
            double flatPercent = 100.0D * flat / ((double) (size - 1) * (size - 1));
            return new RegionStats(count, (double) degree / interior, flatPercent);
        }

        private static void flood(double[][] grid, int[][] labels, int[] queue, int startX, int startZ, int label) {
            int size = grid.length;
            int head = 0;
            int tail = 0;
            labels[startZ][startX] = label;
            queue[tail++] = startZ * size + startX;
            while (head < tail) {
                int cell = queue[head++];
                int x = cell % size;
                int z = cell / size;
                double value = grid[z][x];
                int[] neighbourX = {x + 1, x - 1, x, x};
                int[] neighbourZ = {z, z, z + 1, z - 1};
                for (int index = 0; index < 4; index++) {
                    int nextX = neighbourX[index];
                    int nextZ = neighbourZ[index];
                    if (nextX >= 0 && nextZ >= 0 && nextX < size && nextZ < size && labels[nextZ][nextX] == 0 && grid[nextZ][nextX] == value) {
                        labels[nextZ][nextX] = label;
                        queue[tail++] = nextZ * size + nextX;
                    }
                }
            }
        }
    }
}
