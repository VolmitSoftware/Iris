package art.arcane.iris.testsupport;

import art.arcane.iris.generation.hydrology.underground.SealedPassage;

public record TestPassage(long id, float[] xs, float[] zs, float[] halfWidth, int waterLevel, int[] floor, int[] ceiling, int chamberX,
                          int chamberZ, int chamberRadius) implements SealedPassage {
    public static final int ROOF_CLEARANCE = 6;

    public int stations() {
        return xs.length;
    }

    @Override
    public int roofClearance() {
        return ROOF_CLEARANCE;
    }

    @Override
    public int[] bounds() {
        float minX = chamberX - chamberRadius;
        float minZ = chamberZ - chamberRadius;
        float maxX = chamberX + chamberRadius + 1;
        float maxZ = chamberZ + chamberRadius + 1;
        for (int station = 0; station < stations(); station++) {
            float reach = halfWidth[station] + 1f;
            minX = Math.min(minX, xs[station] - reach);
            minZ = Math.min(minZ, zs[station] - reach);
            maxX = Math.max(maxX, xs[station] + reach);
            maxZ = Math.max(maxZ, zs[station] + reach);
        }
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minZ), (int) Math.ceil(maxX), (int) Math.ceil(maxZ)};
    }

    @Override
    public boolean column(int x, int z, int[] out) {
        double px = x + 0.5D;
        double pz = z + 0.5D;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int station = 0; station + 1 < xs.length; station++) {
            double ax = xs[station];
            double az = zs[station];
            double dx = xs[station + 1] - ax;
            double dz = zs[station + 1] - az;
            double span = dx * dx + dz * dz;
            double t = span <= 0D ? 0D : Math.clamp(((px - ax) * dx + (pz - az) * dz) / span, 0D, 1D);
            double distance = Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
            double half = halfWidth[station] + t * (halfWidth[station + 1] - halfWidth[station]);
            if (distance > half) {
                continue;
            }
            lowest = Math.min(lowest, (int) Math.round(floor[station] + t * (floor[station + 1] - floor[station])));
            highest = Math.max(highest, (int) Math.round(ceiling[station] + t * (ceiling[station + 1] - ceiling[station])));
        }
        double radius = chamberRadius;
        double fromCentre = Math.hypot(px - chamberX, pz - chamberZ);
        if (fromCentre <= radius) {
            int last = xs.length - 1;
            double dome = Math.sqrt(1D - (fromCentre / radius) * (fromCentre / radius));
            lowest = Math.min(lowest, waterLevel - (int) Math.max(1L, Math.round((waterLevel - floor[last]) * dome)));
            highest = Math.max(highest, waterLevel + (int) Math.round((ceiling[last] - waterLevel) * dome));
        }
        if (highest == Integer.MIN_VALUE) {
            return false;
        }
        out[0] = lowest;
        out[1] = highest;
        return true;
    }
}
