package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.List;

final class CurveKnots {
    private CurveKnots() {
    }

    static Field monotone(Field input, double[] locations, double[] values) {
        double[] knots = increasing(locations);
        return curve(input, knots, values, monotoneDerivatives(knots, values));
    }

    static Field smooth(Field input, double[] locations, double[] values) {
        double[] knots = increasing(locations);
        double[] derivatives = new double[knots.length];
        for (int index = 1; index + 1 < knots.length; index++) {
            derivatives[index] = (values[index + 1] - values[index - 1]) / (knots[index + 1] - knots[index - 1]);
        }
        return curve(input, knots, values, derivatives);
    }

    private static Field curve(Field input, double[] knots, double[] values, double[] derivatives) {
        if (knots.length != values.length) {
            throw new IllegalArgumentException("Curve needs one value per knot, got " + values.length + " values for " + knots.length + " knots");
        }
        List<Field.Knot> points = new ArrayList<>(knots.length);
        for (int index = 0; index < knots.length; index++) {
            points.add(new Field.Knot(knots[index], new Field.Const(values[index]), derivatives[index]));
        }
        return new Field.Curve(input, points);
    }

    private static double[] increasing(double[] locations) {
        double[] knots = new double[locations.length];
        float previous = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < locations.length; index++) {
            float location = (float) locations[index];
            if (location <= previous) {
                location = Math.nextUp(previous);
            }
            knots[index] = location;
            previous = location;
        }
        return knots;
    }

    private static double[] monotoneDerivatives(double[] knots, double[] values) {
        double[] derivatives = new double[knots.length];
        for (int index = 1; index + 1 < knots.length; index++) {
            double left = (values[index] - values[index - 1]) / (knots[index] - knots[index - 1]);
            double right = (values[index + 1] - values[index]) / (knots[index + 1] - knots[index]);
            derivatives[index] = left * right <= 0.0D ? 0.0D : 2.0D * left * right / (left + right);
        }
        return derivatives;
    }
}
