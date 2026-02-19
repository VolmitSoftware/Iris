package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexagonNoise implements NoiseGenerator {
    private static final double SQRT_3_OVER_3 = Math.sqrt(3.0) / 3.0;
    private static final double TWO_OVER_THREE = 2.0 / 3.0;
    private static final long CONST_X = 0x9E3779B97F4A7C15L;
    private static final long CONST_Z = 0xC2B2AE3D27D4EB4FL;
    private final long seed;

    public HexagonNoise(long seed) {
        this.seed = new RNG(seed).lmax();
    }

    private long mix(long input) {
        input = (input ^ (input >>> 33)) * 0xff51afd7ed558ccdL;
        input = (input ^ (input >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return input ^ (input >>> 33);
    }

    private long hash(long q, long r) {
        long hash = seed;
        hash ^= mix(q + CONST_X);
        hash ^= mix(r + CONST_Z);
        return mix(hash);
    }

    private long[] roundAxial(double q, double r) {
        double cubeX = q;
        double cubeZ = r;
        double cubeY = -cubeX - cubeZ;

        long rx = Math.round(cubeX);
        long ry = Math.round(cubeY);
        long rz = Math.round(cubeZ);

        double xDiff = Math.abs(rx - cubeX);
        double yDiff = Math.abs(ry - cubeY);
        double zDiff = Math.abs(rz - cubeZ);

        if (xDiff > yDiff && xDiff > zDiff) {
            rx = -ry - rz;
        } else if (yDiff > zDiff) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }

        return new long[]{rx, rz};
    }

    private double sampleCell(double x, double z) {
        double q = (SQRT_3_OVER_3 * x) - (z / 3.0);
        double r = TWO_OVER_THREE * z;
        long[] axial = roundAxial(q, r);
        long hashed = hash(axial[0], axial[1]);
        long normalized = hashed & Long.MAX_VALUE;
        return normalized / (double) Long.MAX_VALUE;
    }

    @Override
    public double noise(double x) {
        return sampleCell(x, 0D);
    }

    @Override
    public double noise(double x, double z) {
        return sampleCell(x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        if (z == 0D) {
            return sampleCell(x, y);
        }

        double a = sampleCell(x, z);
        double b = sampleCell(y, x);
        double c = sampleCell(z, y);
        return (a + b + c) / 3D;
    }
}
