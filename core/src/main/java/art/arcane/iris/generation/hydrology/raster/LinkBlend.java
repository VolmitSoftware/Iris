package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.List;

public final class LinkBlend {
    private static final float LENGTH = 16f;

    private LinkBlend() {
    }

    public static float blend(float from, float to, float along) {
        if (along >= LENGTH) {
            return to;
        }
        float share = Math.max(0f, along) / LENGTH;
        return from + (to - from) * share;
    }

    static Reach blend(Reach downstream, List<Reach> feeders, int seaLevel) {
        float width = 0f;
        float depth = 0f;
        for (Reach feeder : feeders) {
            int last = feeder.xs().length - 1;
            width = Math.max(width, feeder.halfWidth()[last]);
            depth = Math.max(depth, feeder.depth()[last]);
        }
        float[] xs = downstream.xs();
        float[] zs = downstream.zs();
        float[] halfWidth = downstream.halfWidth().clone();
        float[] depths = downstream.depth().clone();
        float widthShift = width - halfWidth[0];
        float depthShift = depth - depths[0];
        float along = 0f;
        for (int point = 0; point < xs.length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
            }
            halfWidth[point] += blend(widthShift, 0f, along);
            depths[point] += blend(depthShift, 0f, along);
        }
        return regraded(downstream, clamped(downstream.grade(), lowestEnd(feeders)), halfWidth, depths, seaLevel);
    }

    static Reach clamp(Reach downstream, List<Reach> feeders, int seaLevel) {
        return clamp(downstream, lowestEnd(feeders), seaLevel);
    }

    static Reach clamp(Reach downstream, int end, int seaLevel) {
        int[] grade = clamped(downstream.grade(), end);
        return grade == downstream.grade() ? downstream : regraded(downstream, grade, downstream.halfWidth(), downstream.depth(), seaLevel);
    }

    static Reach settleInto(Reach feeder, Junction junction, List<ConfluenceGrader.Footprint> receiving, List<PondMask> lips, int seaLevel) {
        int[] grade = feeder.grade();
        int level = junction.level();
        boolean[] fixed = fixed(feeder, lips, junction.fed() && grade[0] - level > ConfluenceGrader.MAX_START_LOWERING);
        int waterfallMinimumDrop = feeder.tier().waterfallMinimumDrop();
        int[] settled = ConfluenceGrader.settle(footprint(feeder), grade, fixed, receiving, level, waterfallMinimumDrop);
        if (settled == grade) {
            return feeder;
        }
        if (!feeder.tier().cascades()) {
            ConfluenceGrader.spreadJunction(settled, grade, fixed, waterfallMinimumDrop, Integer.MIN_VALUE);
        }
        return regraded(feeder, settled, feeder.halfWidth(), feeder.depth(), seaLevel);
    }

    static ConfluenceGrader.Footprint footprint(Reach reach) {
        return new ConfluenceGrader.Footprint(reach.xs(), reach.zs(), reach.halfWidth());
    }

    private static boolean[] fixed(Reach feeder, List<PondMask> lips, boolean heldStart) {
        ReachStyle[] style = feeder.style();
        boolean[] fixed = new boolean[style.length];
        boolean held = !lips.isEmpty();
        fixed[0] = held || heldStart || style[0] == ReachStyle.POND_OUTLET;
        for (int point = 1; point < style.length; point++) {
            held = held && nearLip(lips, feeder.xs()[point], feeder.zs()[point], feeder.halfWidth()[point]);
            fixed[point] = held || style[point] == ReachStyle.POND_OUTLET || zoneStep(feeder, fixed, point);
        }
        return fixed;
    }

    private static boolean zoneStep(Reach feeder, boolean[] fixed, int point) {
        return fixed[point - 1] && feeder.style()[point] == ReachStyle.CASCADE && feeder.grade()[point - 1] - feeder.grade()[point] == 1;
    }

    private static boolean nearLip(List<PondMask> lips, float x, float z, float halfWidth) {
        for (PondMask lip : lips) {
            if (ReachShaper.nearLip(lip, x, z, halfWidth)) {
                return true;
            }
        }
        return false;
    }

    private static Reach regraded(Reach reach, int[] grade, float[] halfWidth, float[] depth, int seaLevel) {
        ReachStyle[] style = grade == reach.grade() ? reach.style() : restyled(reach.style(), grade, reach.tier().waterfallMinimumDrop(), seaLevel);
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), grade, halfWidth, depth, style,
                reach.catchment());
    }

    private static ReachStyle[] restyled(ReachStyle[] style, int[] grade, int waterfallMinimumDrop, int seaLevel) {
        boolean[] mouth = new boolean[style.length];
        boolean[] outlet = new boolean[style.length];
        for (int point = 0; point < style.length; point++) {
            mouth[point] = style[point] == ReachStyle.MOUTH;
            outlet[point] = style[point] == ReachStyle.POND_OUTLET;
        }
        return ReachShaper.styles(grade, mouth, outlet, waterfallMinimumDrop, seaLevel);
    }

    private static int lowestEnd(List<Reach> feeders) {
        int end = Integer.MAX_VALUE;
        for (Reach feeder : feeders) {
            end = Math.min(end, feeder.grade()[feeder.grade().length - 1]);
        }
        return end;
    }

    private static int[] clamped(int[] grade, int end) {
        int[] result = grade;
        for (int point = 0; point < grade.length; point++) {
            if (grade[point] > end) {
                if (result == grade) {
                    result = grade.clone();
                }
                result[point] = end;
            }
        }
        return result;
    }

    record Junction(int level, boolean fed) {
    }
}
