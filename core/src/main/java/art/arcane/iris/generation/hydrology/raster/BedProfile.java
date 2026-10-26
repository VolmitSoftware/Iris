package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.grid.CellHash;

final class BedProfile {
    private static final long SALT_BROAD = 0x42454442524f4144L;
    private static final long SALT_DETAIL = 0x4245444445544149L;
    private static final long SALT_TEXTURE = 0x4245445445585455L;

    private BedProfile() {
    }

    static double variation(long seed, int x, int z) {
        return 0.65D * noise(seed, SALT_BROAD, x, z, 32) + 0.35D * noise(seed, SALT_DETAIL, x, z, 8);
    }

    static double detail(long seed, int x, int z) {
        return 0.35D * noise(seed, SALT_TEXTURE, x, z, 2);
    }

    static double position(double u, double variation) {
        double bounded = Math.clamp(u, 0D, 1D);
        return bounded + 0.8D * variation * bounded * (1D - bounded);
    }

    static int channelDepth(double depth, double ratio, double variation, double detail) {
        double across = position(ratio, -variation);
        double bowl = 1D - across * across;
        double cut = bowl * (depth + variation * Math.min(2D, depth * 0.3D) + detail);
        return (int) Math.clamp(Math.round(cut), 1L, Math.max(1L, Math.round(depth)));
    }

    static int pondDepth(int maximum, double u, double variation, double detail) {
        double across = position(u, variation);
        double bowl = across * across * (3D - 2D * across);
        double cut = 1D + bowl * (maximum - 1D + variation * Math.min(2D, (maximum - 1D) * 0.3D) + detail);
        return (int) Math.clamp(Math.round(cut), 1L, maximum);
    }

    private static double noise(long seed, long salt, int x, int z, int scale) {
        int ix = Math.floorDiv(x, scale);
        int iz = Math.floorDiv(z, scale);
        double u = fade(Math.floorMod(x, scale) / (double) scale);
        double v = fade(Math.floorMod(z, scale) / (double) scale);
        double a = CellHash.unit(CellHash.mix(seed, salt, ix, iz));
        double b = CellHash.unit(CellHash.mix(seed, salt, ix + 1, iz));
        double c = CellHash.unit(CellHash.mix(seed, salt, ix, iz + 1));
        double d = CellHash.unit(CellHash.mix(seed, salt, ix + 1, iz + 1));
        return 2D * ((a + (b - a) * u) * (1D - v) + (c + (d - c) * u) * v) - 1D;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6D - 15D) + 10D);
    }
}
