package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ValueDistributionFit {
    private static final double[] LEVELS = {
            0.0, 0.0005, 0.002, 0.005, 0.01, 0.02, 0.035, 0.05, 0.075, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45,
            0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.925, 0.95, 0.965, 0.98, 0.99, 0.995, 0.998, 0.9995, 1.0
    };
    private static final int MINIMUM_SAMPLES = 64;

    private ValueDistributionFit() {
    }

    public static List<Field.Knot> fit(double[] source, double[] target) {
        double[] sortedSource = sorted(Objects.requireNonNull(source, "source"), "source");
        double[] sortedTarget = sorted(Objects.requireNonNull(target, "target"), "target");
        List<Double> locations = new ArrayList<>(LEVELS.length);
        List<Double> values = new ArrayList<>(LEVELS.length);
        for (double level : LEVELS) {
            double location = (float) quantile(sortedSource, level);
            double value = (float) quantile(sortedTarget, level);
            if (!locations.isEmpty() && location <= locations.getLast()) {
                continue;
            }
            locations.add(location);
            values.add(value);
        }
        if (locations.size() < 2) {
            throw new IllegalArgumentException("Value distribution fit needs a source with spread, got a constant source of " + sortedSource[0]);
        }
        double[] derivatives = monotoneDerivatives(locations, values);
        List<Field.Knot> knots = new ArrayList<>(locations.size());
        for (int index = 0; index < locations.size(); index++) {
            knots.add(new Field.Knot(locations.get(index), new Field.Const(values.get(index)), derivatives[index]));
        }
        return List.copyOf(knots);
    }

    public static double quantile(double[] sorted, double level) {
        if (level <= 0.0) {
            return sorted[0];
        }
        if (level >= 1.0) {
            return sorted[sorted.length - 1];
        }
        double position = level * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        double fraction = position - lower;
        return lower + 1 >= sorted.length ? sorted[lower] : sorted[lower] + fraction * (sorted[lower + 1] - sorted[lower]);
    }

    private static double[] sorted(double[] samples, String role) {
        if (samples.length < MINIMUM_SAMPLES) {
            throw new IllegalArgumentException("Value distribution fit needs at least " + MINIMUM_SAMPLES + " " + role + " samples, got " + samples.length);
        }
        double[] copy = samples.clone();
        for (double sample : copy) {
            if (!Double.isFinite(sample)) {
                throw new IllegalArgumentException("Value distribution fit " + role + " samples must be finite, got " + sample);
            }
        }
        Arrays.sort(copy);
        return copy;
    }

    private static double[] monotoneDerivatives(List<Double> locations, List<Double> values) {
        int count = locations.size();
        double[] secants = new double[count - 1];
        for (int index = 0; index < secants.length; index++) {
            secants[index] = (values.get(index + 1) - values.get(index)) / (locations.get(index + 1) - locations.get(index));
        }
        double[] derivatives = new double[count];
        for (int index = 1; index < count - 1; index++) {
            double before = secants[index - 1];
            double after = secants[index];
            derivatives[index] = before <= 0.0 || after <= 0.0 ? 0.0 : (float) (2.0 / (1.0 / before + 1.0 / after));
        }
        return derivatives;
    }
}
