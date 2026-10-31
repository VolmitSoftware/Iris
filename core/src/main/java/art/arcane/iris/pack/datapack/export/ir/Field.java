package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public sealed interface Field {
    default EnumSet<Axis> axes() {
        return FieldTraits.axes(this);
    }

    default ValueRange range() {
        return FieldTraits.range(this);
    }

    default CostClass cost() {
        return FieldTraits.cost(this);
    }

    private static void requireFinite(double value, String role) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(role + " must be finite, got " + value);
        }
    }

    private static void requireOrdered(double min, double max, String role) {
        requireFinite(min, role + " minimum");
        requireFinite(max, role + " maximum");
        if (min > max) {
            throw new IllegalArgumentException(role + " minimum " + min + " exceeds maximum " + max);
        }
    }

    record Const(double value) implements Field {
        public Const {
            requireFinite(value, "Constant");
        }
    }

    record Coord(Axis axis, Tiling tiling, double from, double to, double fromValue, double toValue) implements Field {
        public Coord {
            Objects.requireNonNull(axis);
            Objects.requireNonNull(tiling);
            requireFinite(from, "Coordinate from");
            requireFinite(to, "Coordinate to");
            requireFinite(fromValue, "Coordinate from value");
            requireFinite(toValue, "Coordinate to value");
            if (from == to) {
                throw new IllegalArgumentException("Coordinate gradient needs distinct from and to, got " + from);
            }
        }
    }

    record Noise(String noiseId, double xzScale, double yScale, Field shiftX, Field shiftY, Field shiftZ) implements Field {
        public Noise {
            VanillaResourceIdentifier.requireIdentifier(noiseId, "noise id");
            requireFinite(xzScale, "Noise xz scale");
            requireFinite(yScale, "Noise y scale");
        }
    }

    record Binary(BinaryOp op, Field left, Field right) implements Field {
        public Binary {
            Objects.requireNonNull(op);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
        }
    }

    record Unary(UnaryOp op, Field input) implements Field {
        public Unary {
            Objects.requireNonNull(op);
            Objects.requireNonNull(input);
        }
    }

    record Clamp(Field input, double min, double max) implements Field {
        public Clamp {
            Objects.requireNonNull(input);
            requireOrdered(min, max, "Clamp");
        }
    }

    record Lerp(Field alpha, Field first, Field second) implements Field {
        public Lerp {
            Objects.requireNonNull(alpha);
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
        }
    }

    record Round(RoundMode mode, Field input, Field multiple) implements Field {
        public Round {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(input);
            Objects.requireNonNull(multiple);
        }
    }

    record Curve(Field input, List<Knot> knots) implements Field {
        public Curve {
            Objects.requireNonNull(input);
            knots = List.copyOf(knots);
            if (knots.isEmpty()) {
                throw new IllegalArgumentException("Curve needs at least one knot");
            }
            for (int index = 1; index < knots.size(); index++) {
                if (knots.get(index).location() <= knots.get(index - 1).location()) {
                    throw new IllegalArgumentException("Curve knot locations must strictly increase, got " + knots.get(index - 1).location() + " then " + knots.get(index).location());
                }
            }
        }
    }

    record Knot(double location, Field value, double derivative) {
        public Knot {
            requireFinite(location, "Knot location");
            requireFinite(derivative, "Knot derivative");
            Objects.requireNonNull(value);
            if (!(value instanceof Const) && !(value instanceof Curve)) {
                throw new IllegalArgumentException("Curve knot value must be a constant or a nested curve, got " + value.getClass().getSimpleName());
            }
        }
    }

    record Partition(Field input, List<Double> thresholds, List<Field> leaves) implements Field {
        public Partition {
            Objects.requireNonNull(input);
            thresholds = List.copyOf(thresholds);
            leaves = List.copyOf(leaves);
            if (thresholds.isEmpty()) {
                throw new IllegalArgumentException("Partition needs at least one threshold");
            }
            if (leaves.size() != thresholds.size() + 1) {
                throw new IllegalArgumentException("Partition needs " + (thresholds.size() + 1) + " leaves for " + thresholds.size() + " thresholds, got " + leaves.size());
            }
            for (int index = 0; index < thresholds.size(); index++) {
                requireFinite(thresholds.get(index), "Partition threshold");
                if (index > 0 && thresholds.get(index) < thresholds.get(index - 1)) {
                    throw new IllegalArgumentException("Partition thresholds must not decrease, got " + thresholds.get(index - 1) + " then " + thresholds.get(index));
                }
            }
            for (Field leaf : leaves) {
                if (leaf.cost() != CostClass.CHEAP) {
                    throw new IllegalArgumentException("Partition leaves must be cheap, got a " + leaf.cost() + " " + leaf.getClass().getSimpleName() + " leaf");
                }
            }
        }
    }

    record Mux(Field index, List<Field> cases) implements Field {
        public Mux {
            Objects.requireNonNull(index);
            cases = List.copyOf(cases);
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("Mux needs at least one case");
            }
        }
    }

    record Gate(Field condition, Field expr) implements Field {
        public Gate {
            Objects.requireNonNull(condition);
            Objects.requireNonNull(expr);
        }
    }

    record RangeIf(Field input, double min, double max, Field inRange, Field outOfRange) implements Field {
        public RangeIf {
            Objects.requireNonNull(input);
            requireOrdered(min, max, "Range choice");
            Objects.requireNonNull(inRange);
            Objects.requireNonNull(outOfRange);
        }
    }

    record Grid(Field expr, int cellXZ, int cellY) implements Field {
        public Grid {
            Objects.requireNonNull(expr);
            if (cellXZ <= 0 || cellY <= 0) {
                throw new IllegalArgumentException("Grid cells must be positive, got " + cellXZ + "x" + cellY);
            }
        }
    }

    record Shared(String name, Field expr) implements Field {
        public Shared {
            VanillaResourceIdentifier.requirePath(name, "shared field name");
            Objects.requireNonNull(expr);
        }
    }

    record Slice(Field expr, Axis axis, int value) implements Field {
        public Slice {
            Objects.requireNonNull(expr);
            Objects.requireNonNull(axis);
        }
    }

    record TopSurface(Field density, int upper, int lower, int step) implements Field {
        public TopSurface {
            Objects.requireNonNull(density);
            if (lower > upper) {
                throw new IllegalArgumentException("Top surface lower bound " + lower + " exceeds upper bound " + upper);
            }
            if (step <= 0) {
                throw new IllegalArgumentException("Top surface step must be positive, got " + step);
            }
        }
    }

    record Ref(String id) implements Field {
        public Ref {
            VanillaResourceIdentifier.requireIdentifier(id, "density function reference");
        }
    }

    enum Tiling {
        CLAMP,
        REPEAT,
        MIRRORED_REPEAT
    }

    enum RoundMode {
        ROUND,
        FLOOR,
        CEIL,
        TRUNCATE
    }

    enum BinaryOp {
        ADD,
        SUB,
        MUL,
        DIV,
        MIN,
        MAX,
        POW
    }

    enum UnaryOp {
        NEG,
        ABS,
        SQUARE,
        CUBE,
        SQRT,
        LOG,
        SIGN,
        SQUEEZE,
        HALF_NEG,
        QUARTER_NEG
    }
}
