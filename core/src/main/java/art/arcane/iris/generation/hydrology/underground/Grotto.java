package art.arcane.iris.generation.hydrology.underground;

import java.util.Arrays;

public record Grotto(long id, int seaLevel, float roughness, int coast, float[] xs, float[] zs, float[] halfWidth, float[] roof, float[] floor,
                     float[] lift, float[] holeX, float[] holeZ, float[] holeRadius, int[] holeTop, long[] plugs) {
    public Grotto {
        int points = xs.length;
        if (points < 2 || zs.length != points || halfWidth.length != points || roof.length != points || floor.length != points || lift.length != points) {
            throw new IllegalArgumentException("Grotto " + id + " needs at least 2 stations with equal profile lengths, got " + points);
        }
        if (coast < 0 || coast >= points) {
            throw new IllegalArgumentException("Grotto " + id + " has coast station " + coast + " outside its " + points + " stations");
        }
        int holes = holeX.length;
        if (holeZ.length != holes || holeRadius.length != holes || holeTop.length != holes) {
            throw new IllegalArgumentException("Grotto " + id + " has uneven skylight data");
        }
        for (int point = 0; point < points; point++) {
            if (halfWidth[point] <= 0f || roof[point] <= 0f || floor[point] <= 0f) {
                throw new IllegalArgumentException("Grotto " + id + " has an empty profile at station " + point);
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Grotto other && id == other.id && seaLevel == other.seaLevel && Float.compare(roughness, other.roughness) == 0 && coast == other.coast
                && Arrays.equals(xs, other.xs) && Arrays.equals(zs, other.zs) && Arrays.equals(halfWidth, other.halfWidth) && Arrays.equals(roof, other.roof)
                && Arrays.equals(floor, other.floor) && Arrays.equals(lift, other.lift) && Arrays.equals(holeX, other.holeX) && Arrays.equals(holeZ, other.holeZ)
                && Arrays.equals(holeRadius, other.holeRadius) && Arrays.equals(holeTop, other.holeTop) && Arrays.equals(plugs, other.plugs);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(id) + Arrays.hashCode(xs);
    }

    public static long plug(int x, int y, int z) {
        return (x + 0x4000000L) << 38 | (z + 0x4000000L) << 12 | y & 0xFFFL;
    }

    public boolean plugged(int x, int y, int z) {
        return plugs.length > 0 && Arrays.binarySearch(plugs, plug(x, y, z)) >= 0;
    }

    public int mouthX() {
        return (int) Math.floor(xs[coast]);
    }

    public int mouthZ() {
        return (int) Math.floor(zs[coast]);
    }

    public int[] bounds() {
        float reach = GrottoShape.horizontalReach(this);
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int point = 0; point < xs.length; point++) {
            minX = Math.min(minX, xs[point]);
            maxX = Math.max(maxX, xs[point]);
            minZ = Math.min(minZ, zs[point]);
            maxZ = Math.max(maxZ, zs[point]);
        }
        return new int[]{(int) Math.floor(minX - reach), (int) Math.floor(minZ - reach), (int) Math.ceil(maxX + reach), (int) Math.ceil(maxZ + reach)};
    }

    public boolean touches(int minX, int minZ, int maxX, int maxZ) {
        int[] bounds = bounds();
        return bounds[0] <= maxX && bounds[2] >= minX && bounds[1] <= maxZ && bounds[3] >= minZ;
    }
}
