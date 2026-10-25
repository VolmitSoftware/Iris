package art.arcane.iris.generation.hydrology.underground;

import java.util.Arrays;

public record SeaInlet(long id, float[] xs, float[] zs, float[] halfWidth, int waterLevel, int[] floor, int[] ceiling, int chamberX,
                       int chamberZ, int chamberRadius) {
    public static final int OPEN = Integer.MAX_VALUE;

    public SeaInlet {
        int stations = xs.length;
        if (stations < 2 || zs.length != stations || halfWidth.length != stations || floor.length != stations || ceiling.length != stations) {
            throw new IllegalArgumentException("Sea inlet " + id + " needs at least two stations and matching arrays: xs=" + xs.length
                    + " zs=" + zs.length + " halfWidth=" + halfWidth.length + " floor=" + floor.length + " ceiling=" + ceiling.length);
        }
        if (chamberRadius <= 0) {
            throw new IllegalArgumentException("Sea inlet " + id + " needs a positive chamber radius, got " + chamberRadius);
        }
        for (int station = 0; station < stations; station++) {
            if (!(halfWidth[station] > 0f) || floor[station] >= waterLevel || ceiling[station] < waterLevel) {
                throw new IllegalArgumentException("Sea inlet " + id + " station " + station + " has half-width " + halfWidth[station]
                        + ", floor " + floor[station] + " and ceiling " + ceiling[station] + " around water level " + waterLevel);
            }
        }
    }

    public int stations() {
        return xs.length;
    }

    public boolean open(int station) {
        return ceiling[station] == OPEN;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SeaInlet inlet && id == inlet.id && waterLevel == inlet.waterLevel && chamberX == inlet.chamberX
                && chamberZ == inlet.chamberZ && chamberRadius == inlet.chamberRadius && Arrays.equals(xs, inlet.xs)
                && Arrays.equals(zs, inlet.zs) && Arrays.equals(halfWidth, inlet.halfWidth) && Arrays.equals(floor, inlet.floor)
                && Arrays.equals(ceiling, inlet.ceiling);
    }

    @Override
    public int hashCode() {
        int hash = 31 * Long.hashCode(id) + waterLevel;
        hash = 31 * hash + chamberX;
        hash = 31 * hash + chamberZ;
        hash = 31 * hash + chamberRadius;
        hash = 31 * hash + Arrays.hashCode(xs);
        hash = 31 * hash + Arrays.hashCode(zs);
        hash = 31 * hash + Arrays.hashCode(halfWidth);
        hash = 31 * hash + Arrays.hashCode(floor);
        return 31 * hash + Arrays.hashCode(ceiling);
    }

    @Override
    public String toString() {
        int last = xs.length - 1;
        return "SeaInlet[id=" + id + ", stations=" + xs.length + ", mouth=" + xs[0] + "," + zs[0] + ", end=" + xs[last] + "," + zs[last]
                + ", waterLevel=" + waterLevel + ", chamber=" + chamberX + "," + chamberZ + " r" + chamberRadius + "]";
    }
}
