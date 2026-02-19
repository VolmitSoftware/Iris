package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexSimplexNoise implements NoiseGenerator, OctaveNoise {
    private static final double SQRT_3_OVER_3 = Math.sqrt(3.0) / 3.0;
    private static final double TWO_OVER_THREE = 2.0 / 3.0;
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double THREE_OVER_TWO = 1.5D;
    private static final double CELL_HEAT_SCALE = 2.7D;
    private final SimplexNoise cellHeatSimplex;
    private int octaves;

    public HexSimplexNoise(long seed) {
        RNG rng = new RNG(seed);
        this.cellHeatSimplex = new SimplexNoise(rng.nextParallelRNG(811L).lmax());
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

    private double axialX(long q, long r) {
        return SQRT_3 * (q + (r * 0.5D));
    }

    private double axialZ(long r) {
        return THREE_OVER_TWO * r;
    }

    private double cellHeat(long q, long r, double frequency) {
        double x = axialX(q, r);
        double z = axialZ(r);
        return cellHeatSimplex.noise((x * CELL_HEAT_SCALE) * frequency, (z * CELL_HEAT_SCALE) * frequency);
    }

    private double sampleCell(long q, long r) {
        if (octaves <= 1) {
            return cellHeat(q, r, 1D);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double total = 0D;
        double max = 0D;

        for (int i = 0; i < octaves; i++) {
            total += cellHeat(q, r, frequency) * amplitude;
            max += amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }

        if (max == 0D) {
            return 0.5D;
        }

        return clip(total / max);
    }

    private double sample(double x, double z) {
        double q = (SQRT_3_OVER_3 * x) - (z / 3.0);
        double r = TWO_OVER_THREE * z;
        long[] rounded = roundAxial(q, r);
        return sampleCell(rounded[0], rounded[1]);
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
