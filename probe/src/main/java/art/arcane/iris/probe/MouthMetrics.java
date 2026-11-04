package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

final class MouthMetrics {
    static final int APPROACH = 96;

    private MouthMetrics() {
    }

    static boolean seaMouth(Reach reach) {
        int last = reach.style().length - 1;
        return reach.downstreamId() == 0L && reach.style()[last] == ReachStyle.MOUTH;
    }

    static double[] along(Reach reach) {
        double[] along = new double[reach.xs().length];
        for (int point = 1; point < along.length; point++) {
            along[point] = along[point - 1] + Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
        }
        return along;
    }

    static int snapped(Reach reach, int point, int sea) {
        return WaterHeads.snap(reach.grade()[point], sea, reach.tier().stepHeight());
    }

    static int lastAboveSea(Reach reach, int sea) {
        for (int point = reach.grade().length - 1; point >= 0; point--) {
            if (snapped(reach, point, sea) > sea) {
                return point;
            }
        }
        return -1;
    }

    static int finalDrop(Reach reach, int sea) {
        int high = lastAboveSea(reach, sea);
        return high < 0 ? 0 : snapped(reach, high, sea) - sea;
    }

    static double seaRun(Reach reach, int sea) {
        double[] along = along(reach);
        int high = lastAboveSea(reach, sea);
        return along[along.length - 1] - (high < 0 ? 0D : along[high]);
    }

    static int largestStep(Reach reach, int sea, double within) {
        double[] along = along(reach);
        double end = along[along.length - 1];
        int largest = 0;
        for (int point = 1; point < along.length; point++) {
            if (end - along[point] <= within) {
                largest = Math.max(largest, snapped(reach, point - 1, sea) - snapped(reach, point, sea));
            }
        }
        return largest;
    }
}
