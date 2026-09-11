package art.arcane.iris.engine.hydrology;

import java.util.Arrays;
import java.util.Objects;

public record HydraulicChannelProfile(double[] widths, double[] depths) {
    public HydraulicChannelProfile {
        Objects.requireNonNull(widths, "widths");
        Objects.requireNonNull(depths, "depths");
        if (widths.length == 0 || widths.length != depths.length) {
            throw new IllegalArgumentException("Channel dimensions require equally sized, nonempty station arrays.");
        }
        widths = widths.clone();
        depths = depths.clone();
        for (int station = 0; station < widths.length; station++) {
            if (!Double.isFinite(widths[station]) || widths[station] <= 0D
                    || !Double.isFinite(depths[station]) || depths[station] <= 0D) {
                throw new IllegalArgumentException("Channel dimensions must be finite and positive.");
            }
        }
    }

    public static HydraulicChannelProfile uniform(double width, double depth) {
        return new HydraulicChannelProfile(new double[]{width}, new double[]{depth});
    }

    public static HydraulicChannelProfile range(double[] widths, double[] depths, int from, int to) {
        Objects.checkFromToIndex(from, to, widths.length);
        Objects.checkFromToIndex(from, to, depths.length);
        return new HydraulicChannelProfile(Arrays.copyOfRange(widths, from, to), Arrays.copyOfRange(depths, from, to));
    }

    @Override
    public double[] widths() {
        return widths.clone();
    }

    @Override
    public double[] depths() {
        return depths.clone();
    }

    public int size() {
        return widths.length;
    }

    public double widthAt(int station) {
        return widths[widths.length == 1 ? 0 : station];
    }

    public double depthAt(int station) {
        return depths[depths.length == 1 ? 0 : station];
    }

    public double maximumWidth() {
        double maximum = 0D;
        for (double width : widths) {
            maximum = Math.max(maximum, width);
        }
        return maximum;
    }

    public double maximumDepth() {
        double maximum = 0D;
        for (double depth : depths) {
            maximum = Math.max(maximum, depth);
        }
        return maximum;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HydraulicChannelProfile profile
                && Arrays.equals(widths, profile.widths)
                && Arrays.equals(depths, profile.depths);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(widths) + Arrays.hashCode(depths);
    }

    @Override
    public String toString() {
        return "HydraulicChannelProfile[widths=" + Arrays.toString(widths) + ", depths=" + Arrays.toString(depths) + "]";
    }
}
