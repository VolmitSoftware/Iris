package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexRandomSizeNoise implements NoiseGenerator, OctaveNoise {
    private static final double SQRT_3_OVER_3 = Math.sqrt(3.0) / 3.0;
    private static final double TWO_OVER_THREE = 2.0 / 3.0;
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double THREE_OVER_TWO = 1.5D;
    private static final double ONE_THIRD = 1D / 3D;
    private static final double TWO_THIRDS = 2D / 3D;
    private static final int MAX_DEPTH = 8;
    private static final double HEAT_SCALE = 0.24D;
    private static final double STRUCTURE_SCALE = 0.36D;
    private static final double SUBDIVIDE_BASE_THRESHOLD = 0.22D;
    private static final double SUBDIVIDE_LEVEL_STEP = 0.11D;
    private static final long CONST_X = 0x9E3779B97F4A7C15L;
    private static final long CONST_Z = 0xC2B2AE3D27D4EB4FL;
    private static final long[][] CHILD_DIRECTIONS = new long[][]{
            {1L, 0L},
            {1L, -1L},
            {0L, -1L},
            {-1L, 0L},
            {-1L, 1L},
            {0L, 1L}
    };
    private final long seed;
    private final SimplexNoise heatSimplex;
    private final SimplexNoise structureSimplex;
    private int octaves;

    public HexRandomSizeNoise(long seed) {
        RNG rng = new RNG(seed);
        this.seed = rng.lmax();
        this.heatSimplex = new SimplexNoise(rng.nextParallelRNG(221L).lmax());
        this.structureSimplex = new SimplexNoise(rng.nextParallelRNG(442L).lmax());
        this.octaves = 1;
    }

    private double clip(double value) {
        if (value < 0D) {
            return 0D;
        }

        if (value > 1D) {
            return 1D;
        }

        return value;
    }

    private long mix(long input) {
        input = (input ^ (input >>> 33)) * 0xff51afd7ed558ccdL;
        input = (input ^ (input >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return input ^ (input >>> 33);
    }

    private double hashToUnit(long value) {
        long normalized = mix(value) & Long.MAX_VALUE;
        return normalized / (double) Long.MAX_VALUE;
    }

    private double random01(long nodeHash, int salt) {
        long mixed = nodeHash ^ (CONST_X * (salt + 1L)) ^ (CONST_Z * (salt + 31L));
        return hashToUnit(mixed);
    }

    private long nextNodeHash(long nodeHash, int childIndex, int level) {
        long mixed = nodeHash ^ (CONST_Z * (childIndex + 1L)) ^ (CONST_X * (level + 1L));
        return mix(mixed);
    }

    private long[] roundAxial(double q, double r) {
        double cubeX = q;
        double cubeZ = r;
        double cubeY = -cubeX - cubeZ;
        long roundedX = Math.round(cubeX);
        long roundedY = Math.round(cubeY);
        long roundedZ = Math.round(cubeZ);
        double xDiff = Math.abs(roundedX - cubeX);
        double yDiff = Math.abs(roundedY - cubeY);
        double zDiff = Math.abs(roundedZ - cubeZ);

        if (xDiff > yDiff && xDiff > zDiff) {
            roundedX = -roundedY - roundedZ;
        } else if (yDiff > zDiff) {
            roundedY = -roundedX - roundedZ;
        } else {
            roundedZ = -roundedX - roundedY;
        }

        return new long[]{roundedX, roundedZ};
    }

    private double hexDistance(double q, double r, double centerQ, double centerR) {
        double deltaQ = q - centerQ;
        double deltaR = r - centerR;
        double deltaY = -deltaQ - deltaR;
        return Math.max(Math.abs(deltaQ), Math.max(Math.abs(deltaR), Math.abs(deltaY)));
    }

    private double axialX(double q, double r) {
        return SQRT_3 * (q + (r * 0.5D));
    }

    private double axialZ(double r) {
        return THREE_OVER_TWO * r;
    }

    private double simplexField(SimplexNoise simplex, double x, double z, double scale) {
        if (octaves <= 1) {
            return simplex.noise(x * scale, z * scale);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double total = 0D;
        double max = 0D;

        for (int i = 0; i < octaves; i++) {
            total += simplex.noise((x * scale) * frequency, (z * scale) * frequency) * amplitude;
            max += amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }

        if (max == 0D) {
            return 0.5D;
        }

        return clip(total / max);
    }

    private double cellHeat(double centerQ, double centerR, long nodeHash, int level) {
        double x = axialX(centerQ, centerR);
        double z = axialZ(centerR);
        double scale = HEAT_SCALE * (1D + (level * 0.2D));
        double simplexValue = simplexField(heatSimplex, x, z, scale);
        double randomValue = random01(nodeHash, 11 + level);
        return clip((simplexValue * 0.74D) + (randomValue * 0.26D));
    }

    private boolean shouldSubdivide(double centerQ, double centerR, long nodeHash, int level) {
        if (level >= MAX_DEPTH - 1) {
            return false;
        }

        double x = axialX(centerQ, centerR);
        double z = axialZ(centerR);
        double scale = STRUCTURE_SCALE * (1D + (level * 0.31D));
        double simplexValue = simplexField(structureSimplex, x, z, scale);
        double randomValue = random01(nodeHash, 71 + level);
        double score = clip((simplexValue * 0.68D) + (randomValue * 0.32D));
        double threshold = SUBDIVIDE_BASE_THRESHOLD + (level * SUBDIVIDE_LEVEL_STEP);
        return score > threshold;
    }

    private int pickChildIndex(double localQ, double localR, double[] centersQ, double[] centersR) {
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;

        centersQ[0] = 0D;
        centersR[0] = 0D;
        double centerDistance = hexDistance(localQ, localR, 0D, 0D) / ONE_THIRD;

        if (centerDistance <= 1D && centerDistance < bestDistance) {
            bestDistance = centerDistance;
            bestIndex = 0;
        }

        for (int i = 0; i < 6; i++) {
            int index = i + 1;
            double centerQ = CHILD_DIRECTIONS[i][0] * TWO_THIRDS;
            double centerR = CHILD_DIRECTIONS[i][1] * TWO_THIRDS;
            centersQ[index] = centerQ;
            centersR[index] = centerR;
            double distance = hexDistance(localQ, localR, centerQ, centerR) / ONE_THIRD;

            if (distance <= 1D && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }

        return bestIndex;
    }

    private double sample(double x, double z) {
        double qWorld = (SQRT_3_OVER_3 * x) - (z / 3.0);
        double rWorld = TWO_OVER_THREE * z;
        long[] rounded = roundAxial(qWorld, rWorld);
        double centerQ = rounded[0];
        double centerR = rounded[1];
        double radius = 0.5D;
        double localQ = (qWorld - centerQ) / radius;
        double localR = (rWorld - centerR) / radius;
        long nodeHash = mix(seed ^ (rounded[0] * CONST_X) ^ (rounded[1] * CONST_Z));
        double heat = cellHeat(centerQ, centerR, nodeHash, 0);

        for (int level = 0; level < MAX_DEPTH; level++) {
            if (!shouldSubdivide(centerQ, centerR, nodeHash, level)) {
                return heat;
            }

            double[] centersQ = new double[7];
            double[] centersR = new double[7];
            int childIndex = pickChildIndex(localQ, localR, centersQ, centersR);

            if (childIndex < 0) {
                return heat;
            }

            double childCenterQ = centersQ[childIndex];
            double childCenterR = centersR[childIndex];
            centerQ += childCenterQ * radius;
            centerR += childCenterR * radius;
            radius *= ONE_THIRD;
            localQ = (localQ - childCenterQ) / ONE_THIRD;
            localR = (localR - childCenterR) / ONE_THIRD;
            nodeHash = nextNodeHash(nodeHash, childIndex, level);
            heat = cellHeat(centerQ, centerR, nodeHash, level + 1);
        }

        return heat;
    }

    @Override
    public double noise(double x) {
        return sample(x, 0D);
    }

    @Override
    public double noise(double x, double z) {
        return sample(x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        if (z == 0D) {
            return sample(x, y);
        }

        double a = sample(x, z);
        double b = sample(y, x);
        double c = sample(z, y);
        return (a + b + c) / 3D;
    }

    @Override
    public void setOctaves(int o) {
        this.octaves = Math.max(1, o);
    }
}
