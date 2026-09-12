package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyHash;

public final class SurfaceNoise {
    private SurfaceNoise() {
    }

    public static double value(long seed, int x, int z, int wavelength) {
        int span = Math.max(1, wavelength);
        int cellX = Math.floorDiv(x, span);
        int cellZ = Math.floorDiv(z, span);
        double localX = smoothStep(Math.floorMod(x, span) / (double) span);
        double localZ = smoothStep(Math.floorMod(z, span) / (double) span);
        double top = lerp(corner(seed, cellX, cellZ), corner(seed, cellX + 1, cellZ), localX);
        double bottom = lerp(corner(seed, cellX, cellZ + 1), corner(seed, cellX + 1, cellZ + 1), localX);
        return lerp(top, bottom, localZ);
    }

    public static double signed(long seed, int x, int z, int wavelength) {
        return value(seed, x, z, wavelength) * 2D - 1D;
    }

    public static double smoothStep(double progress) {
        double clamped = Math.max(0D, Math.min(1D, progress));
        return clamped * clamped * (3D - 2D * clamped);
    }

    private static double corner(long seed, int cellX, int cellZ) {
        return HydrologyHash.unit(HydrologyHash.mix(seed, cellX, cellZ));
    }

    private static double lerp(double first, double second, double progress) {
        return first + (second - first) * progress;
    }
}
