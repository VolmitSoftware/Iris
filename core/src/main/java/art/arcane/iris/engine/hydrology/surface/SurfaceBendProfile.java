package art.arcane.iris.engine.hydrology.surface;

final class SurfaceBendProfile {
    private static final double MAXIMUM_OFFSET = 0.35D;
    private static final double CURVATURE_GAIN = 2.5D;
    private static final int MINIMUM_WINDOW = 8;
    private static final int MAXIMUM_WINDOW = 32;
    private static final int SMOOTHING_RADIUS = 4;

    private SurfaceBendProfile() {
    }

    static double[] offsets(SurfaceCenterline centerline, ChannelProfile channel) {
        int count = centerline.size();
        double[] offsets = new double[count];
        double[] distance = new double[count];
        double[] turns = new double[count];
        for (int station = 1; station < count; station++) {
            distance[station] = distance[station - 1] + StrictMath.hypot(
                    centerline.x()[station] - centerline.x()[station - 1],
                    centerline.z()[station] - centerline.z()[station - 1]);
            double cross = centerline.tangentX()[station - 1] * centerline.tangentZ()[station]
                    - centerline.tangentZ()[station - 1] * centerline.tangentX()[station];
            double dot = centerline.tangentX()[station - 1] * centerline.tangentX()[station]
                    + centerline.tangentZ()[station - 1] * centerline.tangentZ()[station];
            turns[station] = turns[station - 1] + StrictMath.atan2(cross, dot);
        }
        for (int station = 1; station + 1 < count; station++) {
            double halfWidth = channel.halfWidth()[station];
            int window = Math.max(MINIMUM_WINDOW, Math.min(MAXIMUM_WINDOW, (int) StrictMath.ceil(halfWidth * 3D)));
            int from = Math.max(0, station - window);
            int to = Math.min(count - 1, station + window);
            double curvature = (turns[to] - turns[from]) / Math.max(1D, distance[to] - distance[from]);
            double fade = SurfaceNoise.smoothStep(Math.min(1D, Math.min(station, count - 1D - station) / window));
            offsets[station] = Math.max(-MAXIMUM_OFFSET, Math.min(MAXIMUM_OFFSET,
                    -curvature * halfWidth * CURVATURE_GAIN)) * fade;
        }
        return smooth(offsets);
    }

    static double normalizedDistance(double distance, double side, double offset) {
        if (offset == 0D) {
            return distance;
        }
        double signed = StrictMath.copySign(distance, side);
        double shifted = signed >= offset ? (signed - offset) / (1D - offset)
                : (offset - signed) / (1D + offset);
        return Math.max(0D, Math.min(1D, shifted));
    }

    private static double[] smooth(double[] offsets) {
        if (offsets.length < 2) {
            return offsets;
        }
        double[] smoothed = new double[offsets.length];
        double total = 0D;
        for (int station = 0; station <= Math.min(SMOOTHING_RADIUS, offsets.length - 1); station++) {
            total += offsets[station];
        }
        for (int station = 0; station < offsets.length; station++) {
            if (station > 0) {
                if (station + SMOOTHING_RADIUS < offsets.length) {
                    total += offsets[station + SMOOTHING_RADIUS];
                }
                if (station > SMOOTHING_RADIUS) {
                    total -= offsets[station - SMOOTHING_RADIUS - 1];
                }
            }
            int from = Math.max(0, station - SMOOTHING_RADIUS);
            int to = Math.min(offsets.length - 1, station + SMOOTHING_RADIUS);
            smoothed[station] = Math.max(-MAXIMUM_OFFSET, Math.min(MAXIMUM_OFFSET, total / (to - from + 1)));
        }
        smoothed[0] = 0D;
        smoothed[smoothed.length - 1] = 0D;
        return smoothed;
    }
}
