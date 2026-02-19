package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;

public class SierpinskiTriangleNoise implements NoiseGenerator, OctaveNoise {
    private static final double TRI_SCALE = 0.18D;
    private static final double TRI_PROJECTION = 0.8660254037844386D;
    private static final double HEAT_SCALE = 0.33D;
    private final SimplexNoise heatSimplex;
    private int octaves;

    public SierpinskiTriangleNoise(long seed) {
        RNG rng = new RNG(seed);
        this.heatSimplex = new SimplexNoise(rng.nextParallelRNG(177L).lmax());
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

    private double heat(double x, double z) {
        if (octaves <= 1) {
            return heatSimplex.noise(x * HEAT_SCALE, z * HEAT_SCALE);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double total = 0D;
        double max = 0D;

        for (int i = 0; i < octaves; i++) {
            total += heatSimplex.noise((x * HEAT_SCALE) * frequency, (z * HEAT_SCALE) * frequency) * amplitude;
            max += amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }

        if (max == 0D) {
            return 0.5D;
        }

        return clip(total / max);
    }

    private double mask(double x, double z) {
        double sx = (x * TRI_SCALE) + ((z * TRI_SCALE) * 0.5D);
        double sz = (z * TRI_SCALE) * TRI_PROJECTION;
        long ix = (long) Math.floor(Math.abs(sx));
        long iz = (long) Math.floor(Math.abs(sz));
        return ((ix & iz) == 0L) ? 1D : 0D;
    }

    private double sample(double x, double z) {
        double m = mask(x, z);
        double h = heat(x, z);
        if (m >= 0.5D) {
            return clip((h * 0.65D) + 0.35D);
        }

        return clip(h * 0.12D);
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
