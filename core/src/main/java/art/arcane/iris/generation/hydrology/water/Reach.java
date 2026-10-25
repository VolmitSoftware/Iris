package art.arcane.iris.generation.hydrology.water;

import java.util.Arrays;
import java.util.Objects;

public record Reach(
        long id,
        HydrologyTier tier,
        long downstreamId,
        float[] xs,
        float[] zs,
        int[] grade,
        float[] halfWidth,
        float[] depth,
        ReachStyle[] style,
        float catchment
) {
    public Reach {
        Objects.requireNonNull(tier, "tier");
        int points = xs.length;
        if (points < 2 || zs.length != points || grade.length != points || halfWidth.length != points
                || depth.length != points || style.length != points) {
            throw new IllegalArgumentException("Reach " + id + " needs at least two points and matching arrays: xs=" + xs.length
                    + " zs=" + zs.length + " grade=" + grade.length + " halfWidth=" + halfWidth.length + " depth=" + depth.length
                    + " style=" + style.length);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Reach reach && id == reach.id && downstreamId == reach.downstreamId
                && Float.floatToIntBits(catchment) == Float.floatToIntBits(reach.catchment) && tier.equals(reach.tier)
                && Arrays.equals(xs, reach.xs) && Arrays.equals(zs, reach.zs) && Arrays.equals(grade, reach.grade)
                && Arrays.equals(halfWidth, reach.halfWidth) && Arrays.equals(depth, reach.depth) && Arrays.equals(style, reach.style);
    }

    @Override
    public int hashCode() {
        int hash = 31 * Long.hashCode(id) + Long.hashCode(downstreamId);
        hash = 31 * hash + Float.floatToIntBits(catchment);
        hash = 31 * hash + tier.hashCode();
        hash = 31 * hash + Arrays.hashCode(xs);
        hash = 31 * hash + Arrays.hashCode(zs);
        hash = 31 * hash + Arrays.hashCode(grade);
        hash = 31 * hash + Arrays.hashCode(halfWidth);
        hash = 31 * hash + Arrays.hashCode(depth);
        return 31 * hash + Arrays.hashCode(style);
    }

    @Override
    public String toString() {
        int last = xs.length - 1;
        return "Reach[id=" + id + ", tier=" + tier.name() + ", downstreamId=" + downstreamId + ", points=" + xs.length
                + ", from=" + xs[0] + "," + zs[0] + "@" + grade[0] + ", to=" + xs[last] + "," + zs[last] + "@" + grade[last]
                + ", catchment=" + catchment + "]";
    }
}
