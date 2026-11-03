package art.arcane.iris.probe;

import java.util.Arrays;

final class TidalReachMetrics {
    private static final double STEP = 4D;
    private static final double STRAIGHT_DEGREES = 10D;

    private TidalReachMetrics() {
    }

    static double length(float[] xs, float[] zs) {
        double length = 0D;
        for (int point = 1; point < xs.length; point++) {
            length += Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        return length;
    }

    static double sinuosity(float[] xs, float[] zs) {
        int last = xs.length - 1;
        double chord = Math.hypot(xs[last] - xs[0], zs[last] - zs[0]);
        return chord <= 0D ? 1D : length(xs, zs) / chord;
    }

    static double[][] resample(float[] xs, float[] zs) {
        double total = length(xs, zs);
        int steps = Math.max(1, (int) Math.floor(total / STEP));
        double[] rx = new double[steps + 1];
        double[] rz = new double[steps + 1];
        int segment = 0;
        double along = 0D;
        for (int index = 0; index <= steps; index++) {
            double wanted = index * STEP;
            while (segment < xs.length - 2 && along + Math.hypot(xs[segment + 1] - xs[segment], zs[segment + 1] - zs[segment]) < wanted) {
                along += Math.hypot(xs[segment + 1] - xs[segment], zs[segment + 1] - zs[segment]);
                segment++;
            }
            double span = Math.hypot(xs[segment + 1] - xs[segment], zs[segment + 1] - zs[segment]);
            double t = span <= 0D ? 0D : Math.clamp((wanted - along) / span, 0D, 1D);
            rx[index] = xs[segment] + t * (xs[segment + 1] - xs[segment]);
            rz[index] = zs[segment] + t * (zs[segment + 1] - zs[segment]);
        }
        return new double[][]{rx, rz};
    }

    static double straightRun(float[] xs, float[] zs) {
        double[][] line = resample(xs, zs);
        int segments = line[0].length - 1;
        double[] heading = headings(line);
        double longest = 0D;
        int start = 0;
        for (int segment = 0; segment < segments; segment++) {
            while (start < segment && turn(heading[start], heading[segment]) > STRAIGHT_DEGREES) {
                start++;
            }
            longest = Math.max(longest, (segment - start + 1) * STEP);
        }
        return longest;
    }

    static double sharpestTurn(float[] xs, float[] zs) {
        double[][] line = resample(xs, zs);
        double[] heading = headings(line);
        double sharpest = 0D;
        for (int segment = 1; segment < heading.length; segment++) {
            sharpest = Math.max(sharpest, turn(heading[segment - 1], heading[segment]));
        }
        return sharpest;
    }

    private static double[] headings(double[][] line) {
        double[] heading = new double[line[0].length - 1];
        for (int segment = 0; segment < heading.length; segment++) {
            heading[segment] = Math.toDegrees(Math.atan2(line[1][segment + 1] - line[1][segment], line[0][segment + 1] - line[0][segment]));
        }
        return heading;
    }

    private static double turn(double from, double to) {
        double difference = Math.abs(to - from) % 360D;
        return difference > 180D ? 360D - difference : difference;
    }

    static double longestSlotRun(double[] left, double[] right, double rise, double step) {
        double longest = 0D;
        double run = 0D;
        for (int station = 0; station < left.length; station++) {
            run = left[station] >= rise && right[station] >= rise ? run + step : 0D;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    static double steepestRise(int[] heights, int span) {
        double steepest = 0D;
        for (int from = 0; from + span < heights.length; from++) {
            steepest = Math.max(steepest, heights[from + span] - heights[from]);
        }
        return steepest;
    }

    static double percentile(double[] values, double share) {
        if (values.length == 0) {
            return 0D;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(share * sorted.length))];
    }
}
