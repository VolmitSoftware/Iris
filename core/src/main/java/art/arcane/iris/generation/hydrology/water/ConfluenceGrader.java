package art.arcane.iris.generation.hydrology.water;

import java.util.ArrayList;
import java.util.List;

public final class ConfluenceGrader {
    public static final float POOL_MARGIN = 2f;
    public static final float POOL_CAP = 64f;
    public static final float APPROACH_SLOPE = 0.25f;

    private ConfluenceGrader() {
    }

    public record Footprint(float[] xs, float[] zs, float[] halfWidth) {
    }

    public static int[] settle(Footprint feeder, int[] grade, boolean[] fixed, List<Footprint> receiving, int level, int waterfallMinimumDrop) {
        int last = grade.length - 1;
        double[] arc = arcFromEnd(feeder);
        int[] settled = lower(grade, grade, last, level);
        int edge = last;
        for (int point = last - 1; point >= 0 && pooled(feeder, fixed, receiving, arc, point); point--) {
            settled = lower(settled, grade, point, level);
            edge = point;
        }
        int meet = approach(grade, fixed, arc, edge, level, waterfallMinimumDrop);
        for (int point = edge - 1; point > meet; point--) {
            settled = lower(settled, grade, point, envelope(arc, edge, point, level));
        }
        return settled;
    }

    static boolean[] level(List<ReachDraft> drafts, int[] upstreamFirst) {
        boolean[] touched = new boolean[drafts.size()];
        for (int position = upstreamFirst.length - 1; position >= 0; position--) {
            ReachDraft downstream = drafts.get(upstreamFirst[position]);
            for (int feeder : downstream.incoming) {
                ReachDraft draft = drafts.get(feeder);
                int[] settled = settle(footprint(draft), draft.grade, fixed(draft), receiving(drafts, downstream, feeder), downstream.grade[0],
                        draft.tier.waterfallMinimumDrop());
                if (settled != draft.grade) {
                    draft.grade = settled;
                    touched[feeder] = true;
                }
            }
        }
        return touched;
    }

    static double edgeDistance(Footprint channel, double x, double z) {
        float[] xs = channel.xs();
        float[] zs = channel.zs();
        float[] halfWidth = channel.halfWidth();
        double nearest = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment + 1 < xs.length; segment++) {
            double ax = xs[segment];
            double az = zs[segment];
            double dx = xs[segment + 1] - ax;
            double dz = zs[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
            double offsetX = x - (ax + t * dx);
            double offsetZ = z - (az + t * dz);
            double width = halfWidth[segment] + t * (halfWidth[segment + 1] - halfWidth[segment]);
            nearest = Math.min(nearest, Math.sqrt(offsetX * offsetX + offsetZ * offsetZ) - width);
        }
        return nearest;
    }

    private static boolean pooled(Footprint feeder, boolean[] fixed, List<Footprint> receiving, double[] arc, int point) {
        if (arc[point] > POOL_CAP || fixed[point]) {
            return false;
        }
        double reach = feeder.halfWidth()[point] + POOL_MARGIN;
        for (Footprint channel : receiving) {
            if (edgeDistance(channel, feeder.xs()[point], feeder.zs()[point]) <= reach) {
                return true;
            }
        }
        return false;
    }

    private static int approach(int[] grade, boolean[] fixed, double[] arc, int edge, int level, int waterfallMinimumDrop) {
        double window = (waterfallMinimumDrop - 1) / (double) APPROACH_SLOPE;
        for (int point = edge - 1; point >= 0 && arc[point] - arc[edge] <= window; point--) {
            if (grade[point] <= envelope(arc, edge, point, level)) {
                return point;
            }
            if (fixed[point]) {
                return edge;
            }
        }
        return edge;
    }

    private static int envelope(double[] arc, int edge, int point, int level) {
        return level + (int) Math.floor(APPROACH_SLOPE * (arc[point] - arc[edge]));
    }

    private static int[] lower(int[] settled, int[] grade, int point, int ceiling) {
        if (settled[point] <= ceiling) {
            return settled;
        }
        int[] lowered = settled == grade ? grade.clone() : settled;
        lowered[point] = ceiling;
        return lowered;
    }

    private static double[] arcFromEnd(Footprint feeder) {
        float[] xs = feeder.xs();
        float[] zs = feeder.zs();
        double[] arc = new double[xs.length];
        for (int point = xs.length - 2; point >= 0; point--) {
            arc[point] = arc[point + 1] + Math.hypot(xs[point + 1] - xs[point], zs[point + 1] - zs[point]);
        }
        return arc;
    }

    private static Footprint footprint(ReachDraft draft) {
        return new Footprint(draft.xs, draft.zs, draft.halfWidth);
    }

    private static boolean[] fixed(ReachDraft draft) {
        boolean[] fixed = draft.outletZone.clone();
        if (draft.held != null) {
            for (int point = 0; point < fixed.length; point++) {
                fixed[point] |= draft.held[point];
            }
        }
        return fixed;
    }

    private static List<Footprint> receiving(List<ReachDraft> drafts, ReachDraft downstream, int feeder) {
        List<Footprint> receiving = new ArrayList<>(downstream.incoming.length);
        receiving.add(footprint(downstream));
        for (int sibling : downstream.incoming) {
            if (sibling != feeder) {
                receiving.add(footprint(drafts.get(sibling)));
            }
        }
        return receiving;
    }
}
