package art.arcane.iris.pack.datapack.export.ir;

public record ValueRange(double min, double max) {
    public static final ValueRange UNBOUNDED = new ValueRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    public ValueRange {
        if (Double.isNaN(min) || Double.isNaN(max) || min > max
                || min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException("Invalid value range [" + min + ", " + max + "]");
        }
        min = min == 0.0 ? 0.0 : min;
        max = max == 0.0 ? 0.0 : max;
    }

    public static ValueRange point(double value) {
        return new ValueRange(value, value);
    }

    public static ValueRange hull(double first, double second) {
        if (Double.isNaN(first) || Double.isNaN(second)) {
            return UNBOUNDED;
        }
        return new ValueRange(Math.min(first, second), Math.max(first, second));
    }

    public boolean isBounded() {
        return Double.isFinite(min) && Double.isFinite(max);
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    public ValueRange union(ValueRange other) {
        return new ValueRange(Math.min(min, other.min), Math.max(max, other.max));
    }

    public ValueRange widen(double margin) {
        if (Double.isNaN(margin) || margin < 0.0) {
            throw new IllegalArgumentException("Invalid range margin " + margin);
        }
        return new ValueRange(min - margin, max + margin);
    }

    public ValueRange negate() {
        return new ValueRange(-max, -min);
    }

    public ValueRange abs() {
        if (min >= 0.0) {
            return this;
        }
        if (max <= 0.0) {
            return negate();
        }
        return new ValueRange(0.0, Math.max(-min, max));
    }

    public ValueRange add(ValueRange other) {
        return new ValueRange(min + other.min, max + other.max);
    }

    public ValueRange sub(ValueRange other) {
        return new ValueRange(min - other.max, max - other.min);
    }

    public ValueRange mul(ValueRange other) {
        double first = product(min, other.min);
        double second = product(min, other.max);
        double third = product(max, other.min);
        double fourth = product(max, other.max);
        return new ValueRange(Math.min(Math.min(first, second), Math.min(third, fourth)), Math.max(Math.max(first, second), Math.max(third, fourth)));
    }

    public ValueRange div(ValueRange other) {
        if (other.contains(0.0)) {
            return UNBOUNDED;
        }
        return mul(hull(1.0 / other.min, 1.0 / other.max));
    }

    public ValueRange pointwiseMin(ValueRange other) {
        return new ValueRange(Math.min(min, other.min), Math.min(max, other.max));
    }

    public ValueRange pointwiseMax(ValueRange other) {
        return new ValueRange(Math.max(min, other.min), Math.max(max, other.max));
    }

    public ValueRange pow(ValueRange exponent) {
        if (min < 0.0) {
            return UNBOUNDED;
        }
        double first = Math.pow(min, exponent.min);
        double second = Math.pow(min, exponent.max);
        double third = Math.pow(max, exponent.min);
        double fourth = Math.pow(max, exponent.max);
        if (Double.isNaN(first) || Double.isNaN(second) || Double.isNaN(third) || Double.isNaN(fourth)) {
            return UNBOUNDED;
        }
        return new ValueRange(Math.min(Math.min(first, second), Math.min(third, fourth)), Math.max(Math.max(first, second), Math.max(third, fourth)));
    }

    public ValueRange clamp(double lower, double upper) {
        return new ValueRange(Math.min(Math.max(min, lower), upper), Math.max(Math.min(max, upper), lower));
    }

    private static double product(double first, double second) {
        if (first == 0.0 || second == 0.0) {
            return 0.0;
        }
        return first * second;
    }
}
