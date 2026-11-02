package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
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

    static Reach blend(Reach downstream, List<Reach> feeders, int junction) {
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
        float widthShift = width - halfWidth[junction];
        float depthShift = depth - depths[junction];
        float[] along = new float[xs.length];
        for (int point = 1; point < xs.length; point++) {
            along[point] = along[point - 1] + (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        for (int point = 0; point < xs.length; point++) {
            float fromJunction = along[point] - along[junction];
            halfWidth[point] += blend(widthShift, 0f, fromJunction);
            depths[point] += blend(depthShift, 0f, fromJunction);
        }
        return regraded(downstream, clamped(downstream.grade(), lowestEnd(feeders)), halfWidth, depths);
    }

    static Reach clamp(Reach downstream, List<Reach> feeders) {
        return clamp(downstream, lowestEnd(feeders));
    }

    static Reach clamp(Reach downstream, int end) {
        int[] grade = clamped(downstream.grade(), end);
        return grade == downstream.grade() ? downstream : regraded(downstream, grade, downstream.halfWidth(), downstream.depth());
    }

    static Reach settleInto(Reach feeder, int level, List<ConfluenceGrader.Footprint> receiving) {
        int[] grade = feeder.grade();
        boolean[] fixed = fixed(feeder);
        int waterfallMinimumDrop = feeder.tier().waterfallMinimumDrop();
        int[] settled = ConfluenceGrader.settle(footprint(feeder), grade, fixed, receiving, level, waterfallMinimumDrop);
        if (settled == grade) {
            return feeder;
        }
        if (!feeder.tier().cascades()) {
            ConfluenceGrader.spreadJunction(settled, grade, fixed, waterfallMinimumDrop, Integer.MIN_VALUE);
        }
        return regraded(feeder, settled, feeder.halfWidth(), feeder.depth());
    }

    static ConfluenceGrader.Footprint footprint(Reach reach) {
        return new ConfluenceGrader.Footprint(reach.xs(), reach.zs(), reach.halfWidth());
    }

    private static boolean[] fixed(Reach feeder) {
        ReachStyle[] style = feeder.style();
        boolean[] fixed = new boolean[style.length];
        fixed[0] = true;
        for (int point = 1; point < style.length; point++) {
            fixed[point] = style[point] == ReachStyle.POND_OUTLET;
        }
        return fixed;
    }

    private static Reach regraded(Reach reach, int[] grade, float[] halfWidth, float[] depth) {
        ReachStyle[] style = grade == reach.grade() ? reach.style() : restyled(reach.style(), grade, reach.tier().waterfallMinimumDrop());
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), grade, halfWidth, depth, style,
                reach.catchment());
    }

    private static ReachStyle[] restyled(ReachStyle[] style, int[] grade, int waterfallMinimumDrop) {
        boolean[] mouth = new boolean[style.length];
        boolean[] outlet = new boolean[style.length];
        for (int point = 0; point < style.length; point++) {
            mouth[point] = style[point] == ReachStyle.MOUTH;
            outlet[point] = style[point] == ReachStyle.POND_OUTLET;
        }
        return ReachShaper.styles(grade, mouth, outlet, waterfallMinimumDrop);
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
}
