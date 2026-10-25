package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;

final class LinkBlend {
    private static final float LENGTH = 16f;

    private LinkBlend() {
    }

    static Reach blend(Reach downstream, List<Reach> feeders) {
        int end = lowestEnd(feeders);
        float width = 0f;
        float depth = 0f;
        for (Reach feeder : feeders) {
            int last = feeder.xs().length - 1;
            width = Math.max(width, feeder.halfWidth()[last]);
            depth = Math.max(depth, feeder.depth()[last]);
        }
        float[] xs = downstream.xs();
        float[] zs = downstream.zs();
        int[] grade = clamped(downstream.grade(), end);
        float[] halfWidth = downstream.halfWidth().clone();
        float[] depths = downstream.depth().clone();
        float along = 0f;
        for (int point = 0; point < xs.length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
            }
            if (along <= LENGTH) {
                float share = along / LENGTH;
                halfWidth[point] = width + (halfWidth[point] - width) * share;
                depths[point] = depth + (depths[point] - depth) * share;
            }
        }
        return new Reach(downstream.id(), downstream.tier(), downstream.downstreamId(), xs, zs, grade, halfWidth, depths,
                downstream.style(), downstream.catchment());
    }

    static Reach clamp(Reach downstream, List<Reach> feeders) {
        return clamp(downstream, lowestEnd(feeders));
    }

    static Reach clamp(Reach downstream, int end) {
        boolean above = false;
        for (int grade : downstream.grade()) {
            above |= grade > end;
        }
        if (!above) {
            return downstream;
        }
        return new Reach(downstream.id(), downstream.tier(), downstream.downstreamId(), downstream.xs(), downstream.zs(),
                clamped(downstream.grade(), end), downstream.halfWidth(), downstream.depth(), downstream.style(), downstream.catchment());
    }

    private static int lowestEnd(List<Reach> feeders) {
        int end = Integer.MAX_VALUE;
        for (Reach feeder : feeders) {
            end = Math.min(end, feeder.grade()[feeder.grade().length - 1]);
        }
        return end;
    }

    private static int[] clamped(int[] grade, int end) {
        int[] result = grade.clone();
        for (int point = 0; point < result.length; point++) {
            result[point] = Math.min(result[point], end);
        }
        return result;
    }
}
