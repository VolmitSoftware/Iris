package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class IrisDepositEllipsoidCellsTest {
    @Test
    public void cellsAndDrawsMatchThePerCellEvaluation() {
        int[] sizes = {1, 2, 3, 4, 5, 7, 8, 9, 12, 16, 17, 24, 32, 33, 48, 64, 96, 200};
        for (int size : sizes) {
            long seeds = size <= 64 ? 300L : 8L;
            for (long seed = 0L; seed < seeds; seed++) {
                long mixedSeed = seed * 0x9E3779B97F4A7C15L + size;
                RNG expectedRng = new RNG(mixedSeed);
                RNG actualRng = new RNG(mixedSeed);
                List<Long> expected = new ArrayList<>();
                List<Long> actual = new ArrayList<>();
                referenceCells(expectedRng, size, (x, y, z) -> expected.add(pack(x, y, z)));
                IrisDepositGenerator.vanillaEllipsoidCells(actualRng, size, (x, y, z) -> actual.add(pack(x, y, z)));
                assertEquals("size " + size + " seed " + seed, expected, actual);
                assertEquals("size " + size + " seed " + seed, expectedRng.nextLong(), actualRng.nextLong());
            }
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static void referenceCells(RNG rng, int size, IrisDepositGenerator.CellSink sink) {
        float angle = rng.nextFloat() * (float) Math.PI;
        float reach = size / 8F;
        double startX = Math.sin(angle) * reach;
        double endX = -Math.sin(angle) * reach;
        double startZ = Math.cos(angle) * reach;
        double endZ = -Math.cos(angle) * reach;
        double startY = rng.nextInt(3) - 2;
        double endY = rng.nextInt(3) - 2;
        double[] nodes = new double[size * 4];

        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            double radiusNoise = rng.nextDouble() * size / 16D;
            nodes[i * 4] = startX + (endX - startX) * progress;
            nodes[i * 4 + 1] = startY + (endY - startY) * progress;
            nodes[i * 4 + 2] = startZ + (endZ - startZ) * progress;
            nodes[i * 4 + 3] = ((Math.sin(Math.PI * progress) + 1D) * radiusNoise + 1D) / 2D;
        }

        for (int i = 0; i < size - 1; i++) {
            if (nodes[i * 4 + 3] <= 0D) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                if (nodes[j * 4 + 3] <= 0D) {
                    continue;
                }
                double dx = nodes[i * 4] - nodes[j * 4];
                double dy = nodes[i * 4 + 1] - nodes[j * 4 + 1];
                double dz = nodes[i * 4 + 2] - nodes[j * 4 + 2];
                double dr = nodes[i * 4 + 3] - nodes[j * 4 + 3];
                if (dr * dr <= dx * dx + dy * dy + dz * dz) {
                    continue;
                }
                if (dr > 0D) {
                    nodes[j * 4 + 3] = -1D;
                } else {
                    nodes[i * 4 + 3] = -1D;
                }
            }
        }

        for (int i = 0; i < size; i++) {
            double radius = nodes[i * 4 + 3];
            if (radius < 0D) {
                continue;
            }
            double centerX = nodes[i * 4];
            double centerY = nodes[i * 4 + 1];
            double centerZ = nodes[i * 4 + 2];
            int minX = (int) Math.floor(centerX - radius);
            int maxX = Math.max((int) Math.floor(centerX + radius), minX);
            int minY = (int) Math.floor(centerY - radius);
            int maxY = Math.max((int) Math.floor(centerY + radius), minY);
            int minZ = (int) Math.floor(centerZ - radius);
            int maxZ = Math.max((int) Math.floor(centerZ + radius), minZ);

            for (int x = minX; x <= maxX; x++) {
                double nx = (x + 0.5D - centerX) / radius;
                if (nx * nx >= 1D) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    double ny = (y + 0.5D - centerY) / radius;
                    if (nx * nx + ny * ny >= 1D) {
                        continue;
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        double nz = (z + 0.5D - centerZ) / radius;
                        if (nx * nx + ny * ny + nz * nz < 1D) {
                            sink.add(x, y, z);
                        }
                    }
                }
            }
        }
    }
}
