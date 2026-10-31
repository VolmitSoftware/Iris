package art.arcane.iris.pack.datapack.export.ir;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.EnumSet;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

final class FieldTraits {
    private static final ValueRange NOISE_RANGE = new ValueRange(-1.0, 1.0);
    private static final int EXPENSIVE_LOAD = 2;
    private static final double HERMITE_OVERSHOOT = 4.0 / 27.0;
    private static final Cache<Field, Traits> CACHE = Caffeine.newBuilder().weakKeys().build();

    private FieldTraits() {
    }

    static EnumSet<Axis> axes(Field field) {
        return EnumSet.copyOf(traits(field).axes());
    }

    static ValueRange range(Field field) {
        return traits(field).range();
    }

    static CostClass cost(Field field) {
        int load = traits(field).load();
        if (load == 0) {
            return CostClass.CHEAP;
        }
        return load == 1 ? CostClass.NOISE : CostClass.EXPENSIVE;
    }

    private static Traits traits(Field field) {
        Traits cached = CACHE.getIfPresent(field);
        if (cached != null) {
            return cached;
        }
        Traits computed = compute(field);
        CACHE.put(field, computed);
        return computed;
    }

    private static Traits compute(Field field) {
        return switch (field) {
            case Field.Const constant -> new Traits(EnumSet.noneOf(Axis.class), ValueRange.point(constant.value()), 0);
            case Field.Coord coord -> new Traits(EnumSet.of(coord.axis()), ValueRange.hull(coord.fromValue(), coord.toValue()), 0);
            case Field.Noise noise -> noise(noise);
            case Field.Binary binary -> binary(binary);
            case Field.Unary unary -> unary(unary);
            case Field.Clamp clamp -> clamp(clamp);
            case Field.Lerp lerp -> lerp(lerp);
            case Field.Round round -> round(round);
            case Field.Curve curve -> curve(curve);
            case Field.Partition partition -> partition(partition);
            case Field.Mux mux -> mux(mux);
            case Field.Gate gate -> gate(gate);
            case Field.RangeIf rangeIf -> rangeIf(rangeIf);
            case Field.Grid grid -> traits(grid.expr());
            case Field.Shared shared -> traits(shared.expr());
            case Field.Slice slice -> slice(slice);
            case Field.TopSurface topSurface -> topSurface(topSurface);
            case Field.Ref ignored -> new Traits(EnumSet.allOf(Axis.class), ValueRange.UNBOUNDED, 0);
        };
    }

    private static Traits noise(Field.Noise noise) {
        EnumSet<Axis> axes = EnumSet.noneOf(Axis.class);
        if (noise.xzScale() != 0.0) {
            axes.add(Axis.X);
            axes.add(Axis.Z);
        }
        if (noise.yScale() != 0.0) {
            axes.add(Axis.Y);
        }
        int load = 1;
        for (Field shift : new Field[]{noise.shiftX(), noise.shiftY(), noise.shiftZ()}) {
            if (shift == null) {
                continue;
            }
            Traits shiftTraits = traits(shift);
            axes.addAll(shiftTraits.axes());
            load = sum(load, shiftTraits.load());
        }
        return new Traits(axes, NOISE_RANGE, load);
    }

    private static Traits binary(Field.Binary binary) {
        Traits left = traits(binary.left());
        Traits right = traits(binary.right());
        ValueRange range = switch (binary.op()) {
            case ADD -> left.range().add(right.range());
            case SUB -> left.range().sub(right.range());
            case MUL -> left.range().mul(right.range());
            case DIV -> left.range().div(right.range());
            case MIN -> left.range().pointwiseMin(right.range());
            case MAX -> left.range().pointwiseMax(right.range());
            case POW -> left.range().pow(right.range());
        };
        return new Traits(union(left.axes(), right.axes()), range, sum(left.load(), right.load()));
    }

    private static Traits unary(Field.Unary unary) {
        Traits input = traits(unary.input());
        ValueRange source = input.range();
        ValueRange range = switch (unary.op()) {
            case NEG -> source.negate();
            case ABS -> source.abs();
            case SQUARE -> monotone(source.abs(), value -> value * value);
            case CUBE -> monotone(source, value -> value * value * value);
            case SQRT -> monotone(source, value -> Math.sqrt(Math.max(value, 0.0)));
            case LOG -> source.max() <= 0.0 ? ValueRange.UNBOUNDED : monotone(source, value -> value <= 0.0 ? Double.NEGATIVE_INFINITY : Math.log(value));
            case SIGN -> monotone(source, Math::signum);
            case SQUEEZE -> monotone(source, FieldTraits::squeeze);
            case HALF_NEG -> monotone(source, value -> value > 0.0 ? value : value * 0.5);
            case QUARTER_NEG -> monotone(source, value -> value > 0.0 ? value : value * 0.25);
        };
        return new Traits(input.axes(), range, input.load());
    }

    private static Traits clamp(Field.Clamp clamp) {
        Traits input = traits(clamp.input());
        return new Traits(input.axes(), input.range().clamp(clamp.min(), clamp.max()), input.load());
    }

    private static Traits lerp(Field.Lerp lerp) {
        Traits alpha = traits(lerp.alpha());
        Traits first = traits(lerp.first());
        Traits second = traits(lerp.second());
        ValueRange range = alpha.range().min() >= 0.0 && alpha.range().max() <= 1.0
                ? first.range().union(second.range())
                : first.range().add(alpha.range().mul(second.range().sub(first.range())));
        return new Traits(union(alpha.axes(), union(first.axes(), second.axes())), range, sum(alpha.load(), sum(first.load(), second.load())));
    }

    private static Traits round(Field.Round round) {
        Traits input = traits(round.input());
        Traits multiple = traits(round.multiple());
        return new Traits(union(input.axes(), multiple.axes()), roundRange(round.mode(), input.range(), multiple.range()), sum(input.load(), multiple.load()));
    }

    private static ValueRange roundRange(Field.RoundMode mode, ValueRange input, ValueRange multiple) {
        if (multiple.min() == multiple.max()) {
            double step = multiple.min();
            if (step == 0.0) {
                return ValueRange.UNBOUNDED;
            }
            return ValueRange.hull(quantise(mode, input.min() / step) * step, quantise(mode, input.max() / step) * step);
        }
        double margin = Math.max(Math.abs(multiple.min()), Math.abs(multiple.max()));
        return Double.isFinite(margin) ? input.widen(margin) : ValueRange.UNBOUNDED;
    }

    private static double quantise(Field.RoundMode mode, double value) {
        return switch (mode) {
            case ROUND -> Math.floor(value + 0.5);
            case FLOOR -> Math.floor(value);
            case CEIL -> Math.ceil(value);
            case TRUNCATE -> value < 0.0 ? Math.ceil(value) : Math.floor(value);
        };
    }

    private static Traits curve(Field.Curve curve) {
        Traits input = traits(curve.input());
        EnumSet<Axis> axes = EnumSet.copyOf(input.axes());
        int knotLoad = 0;
        for (Field.Knot knot : curve.knots()) {
            Traits value = traits(knot.value());
            axes.addAll(value.axes());
            knotLoad = Math.max(knotLoad, value.load());
        }
        return new Traits(axes, curveRange(input.range(), curve.knots()), sum(input.load(), knotLoad));
    }

    private static ValueRange curveRange(ValueRange input, List<Field.Knot> knots) {
        Field.Knot first = knots.getFirst();
        Field.Knot last = knots.getLast();
        ValueRange result = null;
        if (input.min() < first.location()) {
            result = include(result, extension(first, input.min()));
        }
        if (input.max() > last.location()) {
            result = include(result, extension(last, input.max()));
        }
        for (Field.Knot knot : knots) {
            if (input.contains(knot.location())) {
                result = include(result, traits(knot.value()).range());
            }
        }
        for (int index = 0; index + 1 < knots.size(); index++) {
            Field.Knot start = knots.get(index);
            Field.Knot end = knots.get(index + 1);
            if (input.max() < start.location() || input.min() > end.location()) {
                continue;
            }
            double width = end.location() - start.location();
            double overshoot = HERMITE_OVERSHOOT * width * (Math.abs(start.derivative()) + Math.abs(end.derivative()));
            ValueRange ends = traits(start.value()).range().union(traits(end.value()).range());
            result = include(result, ends.widen(overshoot));
        }
        return result == null ? traits(first.value()).range() : result;
    }

    private static ValueRange extension(Field.Knot knot, double coordinate) {
        ValueRange value = traits(knot.value()).range();
        if (knot.derivative() == 0.0) {
            return value;
        }
        return value.add(ValueRange.hull(0.0, knot.derivative() * (coordinate - knot.location())));
    }

    private static Traits partition(Field.Partition partition) {
        Traits input = traits(partition.input());
        List<Double> thresholds = partition.thresholds();
        EnumSet<Axis> axes = EnumSet.copyOf(input.axes());
        ValueRange range = null;
        int load = input.load();
        for (int index = 0; index < partition.leaves().size(); index++) {
            Traits leaf = traits(partition.leaves().get(index));
            axes.addAll(leaf.axes());
            load = sum(load, leaf.load());
            boolean belowUpper = index == thresholds.size() || input.range().min() < thresholds.get(index);
            boolean aboveLower = index == 0 || input.range().max() >= thresholds.get(index - 1);
            if (belowUpper && aboveLower) {
                range = include(range, leaf.range());
            }
        }
        return new Traits(axes, range == null ? traits(partition.leaves().getFirst()).range() : range, load);
    }

    private static Traits mux(Field.Mux mux) {
        Traits index = traits(mux.index());
        EnumSet<Axis> axes = EnumSet.copyOf(index.axes());
        ValueRange range = null;
        int load = index.load();
        for (Field selected : mux.cases()) {
            Traits traits = traits(selected);
            axes.addAll(traits.axes());
            range = include(range, traits.range());
            load = Math.max(load, traits.load());
        }
        return new Traits(axes, range, load);
    }

    private static Traits gate(Field.Gate gate) {
        Traits condition = traits(gate.condition());
        Traits expr = traits(gate.expr());
        return new Traits(union(condition.axes(), expr.axes()), expr.range().union(ValueRange.point(0.0)), Math.max(condition.load(), expr.load()));
    }

    private static Traits rangeIf(Field.RangeIf rangeIf) {
        Traits input = traits(rangeIf.input());
        Traits inRange = traits(rangeIf.inRange());
        Traits outOfRange = traits(rangeIf.outOfRange());
        ValueRange source = input.range();
        ValueRange range;
        if (source.min() >= rangeIf.min() && source.max() < rangeIf.max()) {
            range = inRange.range();
        } else if (source.max() < rangeIf.min() || source.min() >= rangeIf.max()) {
            range = outOfRange.range();
        } else {
            range = inRange.range().union(outOfRange.range());
        }
        EnumSet<Axis> axes = union(input.axes(), union(inRange.axes(), outOfRange.axes()));
        return new Traits(axes, range, sum(input.load(), sum(inRange.load(), outOfRange.load())));
    }

    private static Traits slice(Field.Slice slice) {
        Traits expr = traits(slice.expr());
        EnumSet<Axis> axes = EnumSet.copyOf(expr.axes());
        axes.remove(slice.axis());
        return new Traits(axes, expr.range(), expr.load());
    }

    private static Traits topSurface(Field.TopSurface topSurface) {
        Traits density = traits(topSurface.density());
        EnumSet<Axis> axes = EnumSet.copyOf(density.axes());
        axes.remove(Axis.Y);
        int load = density.load() == 0 ? 0 : EXPENSIVE_LOAD;
        return new Traits(axes, new ValueRange(topSurface.lower(), topSurface.upper()), load);
    }

    private static ValueRange monotone(ValueRange source, DoubleUnaryOperator function) {
        return ValueRange.hull(function.applyAsDouble(source.min()), function.applyAsDouble(source.max()));
    }

    private static double squeeze(double value) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        return clamped / 2.0 - clamped * clamped * clamped / 24.0;
    }

    private static ValueRange include(ValueRange current, ValueRange addition) {
        return current == null ? addition : current.union(addition);
    }

    private static EnumSet<Axis> union(EnumSet<Axis> first, EnumSet<Axis> second) {
        EnumSet<Axis> axes = EnumSet.copyOf(first);
        axes.addAll(second);
        return axes;
    }

    private static int sum(int first, int second) {
        return Math.min(EXPENSIVE_LOAD, first + second);
    }

    private record Traits(EnumSet<Axis> axes, ValueRange range, int load) {
    }
}
