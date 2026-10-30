package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

public final class FieldMacros {
    public static final double MIN_GRADIENT = 1.0e-6;
    private static final Field ZERO = new Field.Const(0.0);

    private FieldMacros() {
    }

    public static Field offset(Field expr, double dx, double dz) {
        Objects.requireNonNull(expr, "expr");
        requireFinite(dx, "x offset");
        requireFinite(dz, "z offset");
        if (dx == 0.0 && dz == 0.0) {
            return expr;
        }
        String tag = "offset_" + label('x', dx) + "_" + label('z', dz);
        return new Displacement(new Field.Const(dx), new Field.Const(dz), tag).apply(expr);
    }

    public static Field displace(Field expr, Field dx, Field dz, String tag) {
        Objects.requireNonNull(expr, "expr");
        VanillaResourceIdentifier.requirePath(tag, "displacement tag");
        return new Displacement(dx == null ? ZERO : dx, dz == null ? ZERO : dz, tag).apply(expr);
    }

    public static Field substitute(Field expr, Field target, Field replacement, String tag) {
        Objects.requireNonNull(expr, "expr");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(replacement, "replacement");
        VanillaResourceIdentifier.requirePath(tag, "substitution tag");
        return target.equals(replacement) ? expr : new Substitution(target, replacement, tag).apply(expr);
    }

    public static Field lift(Field expr, double verticalRatio) {
        Objects.requireNonNull(expr, "expr");
        if (!(verticalRatio > 0.0) || !Double.isFinite(verticalRatio)) {
            throw new IllegalArgumentException("Vertical ratio must be finite and positive, got " + verticalRatio);
        }
        return new Lift(verticalRatio, "lift_" + label('y', verticalRatio)).apply(expr);
    }

    public static Gradient grad2(Field expr, double delta) {
        Objects.requireNonNull(expr, "expr");
        if (!(delta > 0.0) || !Double.isFinite(delta)) {
            throw new IllegalArgumentException("Gradient step must be finite and positive, got " + delta);
        }
        Field x = scale(new Field.Binary(Field.BinaryOp.SUB, offset(expr, delta, 0.0), expr), 1.0 / delta);
        Field z = scale(new Field.Binary(Field.BinaryOp.SUB, offset(expr, 0.0, delta), expr), 1.0 / delta);
        return new Gradient(x, z);
    }

    public static Field norm(Gradient gradient) {
        Objects.requireNonNull(gradient, "gradient");
        Field squares = new Field.Binary(Field.BinaryOp.ADD,
                new Field.Unary(Field.UnaryOp.SQUARE, gradient.x()),
                new Field.Unary(Field.UnaryOp.SQUARE, gradient.z()));
        return new Field.Unary(Field.UnaryOp.SQRT, squares);
    }

    public static Field signedDistance(Field field, Field threshold, Field gradientNorm) {
        Field difference = new Field.Binary(Field.BinaryOp.SUB, field, threshold);
        Field denominator = new Field.Binary(Field.BinaryOp.MAX, gradientNorm, new Field.Const(MIN_GRADIENT));
        return new Field.Binary(Field.BinaryOp.DIV, difference, denominator);
    }

    public static Field signedDistance(Field field, double threshold, double delta) {
        return signedDistance(field, new Field.Const(threshold), norm(grad2(field, delta)));
    }

    public static Field product(Field mask, Field payload) {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(payload, "payload");
        if (mask instanceof Field.Const first && payload instanceof Field.Const second) {
            return new Field.Const(first.value() * second.value());
        }
        if (mask instanceof Field.Const constant) {
            return scale(payload, constant.value());
        }
        if (payload instanceof Field.Const constant) {
            return scale(mask, constant.value());
        }
        if (payload.cost() == CostClass.EXPENSIVE) {
            return new Field.Binary(Field.BinaryOp.MUL, mask, new Field.Gate(magnitude(mask), payload));
        }
        if (mask.cost() == CostClass.EXPENSIVE) {
            return new Field.Binary(Field.BinaryOp.MUL, new Field.Gate(magnitude(payload), mask), payload);
        }
        return new Field.Binary(Field.BinaryOp.MUL, mask, payload);
    }

    public static Field scale(Field input, double factor) {
        Objects.requireNonNull(input, "input");
        requireFinite(factor, "scale factor");
        if (factor == 1.0) {
            return input;
        }
        if (input instanceof Field.Const constant) {
            return new Field.Const(constant.value() * factor);
        }
        if (factor == 0.0) {
            return ZERO;
        }
        return new Field.Binary(Field.BinaryOp.MUL, new Field.Const(factor), input);
    }

    public static Field sum(List<Field> terms) {
        double constant = 0.0;
        List<Field> variable = new ArrayList<>(terms.size());
        for (Field term : terms) {
            if (term instanceof Field.Const value) {
                constant += value.value();
            } else {
                variable.add(Objects.requireNonNull(term, "term"));
            }
        }
        if (variable.isEmpty()) {
            return new Field.Const(constant);
        }
        Field balanced = balancedSum(variable, 0, variable.size());
        return constant == 0.0 ? balanced : new Field.Binary(Field.BinaryOp.ADD, balanced, new Field.Const(constant));
    }

    public static Field smoothstep(Field input, double from, double to) {
        requireFinite(from, "smoothstep start");
        requireFinite(to, "smoothstep end");
        if (!(to > from)) {
            throw new IllegalArgumentException("Smoothstep end " + to + " must exceed its start " + from);
        }
        return new Field.Curve(input, List.of(
                new Field.Knot(from, ZERO, 0.0),
                new Field.Knot(to, new Field.Const(1.0), 0.0)));
    }

    public static Field lookup(Field input, DoubleUnaryOperator function, DoubleUnaryOperator derivative, double from, double to, int segments) {
        requireFinite(from, "lookup start");
        requireFinite(to, "lookup end");
        if (!(to > from) || segments < 1) {
            throw new IllegalArgumentException("Lookup needs an increasing range and at least one segment, got [" + from + ", " + to + "] with " + segments);
        }
        List<Field.Knot> knots = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double location = from + (to - from) * index / segments;
            knots.add(new Field.Knot(location, new Field.Const(function.applyAsDouble(location)), derivative.applyAsDouble(location)));
        }
        return new Field.Curve(input, knots);
    }

    public static Field knotValue(Field value) {
        if (value instanceof Field.Const || value instanceof Field.Curve) {
            return value;
        }
        return new Field.Curve(value, List.of(new Field.Knot(0.0, ZERO, 1.0)));
    }

    public static Field share(String name, Field field) {
        Objects.requireNonNull(field, "field");
        if (field instanceof Field.Shared || field.cost() == CostClass.CHEAP) {
            return field;
        }
        return new Field.Shared(name, field);
    }

    public static boolean isGuard(Field field) {
        return field instanceof Field.Gate || field instanceof Field.Mux;
    }

    public static List<Field> children(Field field) {
        return switch (field) {
            case Field.Const ignored -> List.of();
            case Field.Coord ignored -> List.of();
            case Field.Ref ignored -> List.of();
            case Field.Noise noise -> present(noise.shiftX(), noise.shiftY(), noise.shiftZ());
            case Field.Binary binary -> List.of(binary.left(), binary.right());
            case Field.Unary unary -> List.of(unary.input());
            case Field.Clamp clamp -> List.of(clamp.input());
            case Field.Lerp lerp -> List.of(lerp.alpha(), lerp.first(), lerp.second());
            case Field.Round round -> List.of(round.input(), round.multiple());
            case Field.Curve curve -> curveChildren(curve);
            case Field.Partition partition -> prepend(partition.input(), partition.leaves());
            case Field.Mux mux -> prepend(mux.index(), mux.cases());
            case Field.Gate gate -> List.of(gate.condition(), gate.expr());
            case Field.RangeIf rangeIf -> List.of(rangeIf.input(), rangeIf.inRange(), rangeIf.outOfRange());
            case Field.Grid grid -> List.of(grid.expr());
            case Field.Shared shared -> List.of(shared.expr());
            case Field.Slice slice -> List.of(slice.expr());
            case Field.TopSurface topSurface -> List.of(topSurface.density());
        };
    }

    private static List<Field> present(Field... fields) {
        List<Field> present = new ArrayList<>(fields.length);
        for (Field field : fields) {
            if (field != null) {
                present.add(field);
            }
        }
        return present;
    }

    private static List<Field> curveChildren(Field.Curve curve) {
        List<Field> children = new ArrayList<>(curve.knots().size() + 1);
        children.add(curve.input());
        for (Field.Knot knot : curve.knots()) {
            children.add(knot.value());
        }
        return children;
    }

    private static List<Field> prepend(Field first, List<Field> rest) {
        List<Field> children = new ArrayList<>(rest.size() + 1);
        children.add(first);
        children.addAll(rest);
        return children;
    }

    private static Field magnitude(Field field) {
        return field.range().min() >= 0.0 ? field : new Field.Unary(Field.UnaryOp.ABS, field);
    }

    private static Field balancedSum(List<Field> terms, int from, int to) {
        if (to - from == 1) {
            return terms.get(from);
        }
        int middle = (from + to) >>> 1;
        return new Field.Binary(Field.BinaryOp.ADD, balancedSum(terms, from, middle), balancedSum(terms, middle, to));
    }

    private static String label(char axis, double value) {
        String sign = value < 0.0 ? "m" : "";
        double magnitude = Math.abs(value);
        if (magnitude == Math.rint(magnitude) && magnitude < 1.0e15) {
            return axis + sign + (long) magnitude;
        }
        return axis + sign + String.format(Locale.ROOT, "%.4f", magnitude).replace('.', 'p');
    }

    private static void requireFinite(double value, String role) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("The " + role + " must be finite, got " + value);
        }
    }

    public record Gradient(Field x, Field z) {
        public Gradient {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(z, "z");
        }
    }

    private abstract static class Rewriter {
        private final Map<Field, Field> memo = new IdentityHashMap<>();
        private final String tag;

        private Rewriter(String tag) {
            this.tag = tag;
        }

        abstract boolean skips(Field field);

        abstract Field noise(Field.Noise noise);

        abstract Field coord(Field.Coord coord);

        abstract Field slice(Field.Slice slice);

        Field apply(Field field) {
            if (field == null) {
                return null;
            }
            Field cached = memo.get(field);
            if (cached != null) {
                return cached;
            }
            Field result = skips(field) ? field : rewrite(field);
            memo.put(field, result);
            return result;
        }

        String tag() {
            return tag;
        }

        private Field rewrite(Field field) {
            return switch (field) {
                case Field.Const constant -> constant;
                case Field.Coord coord -> coord(coord);
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
                case Field.Grid grid -> grid(grid);
                case Field.Shared shared -> shared(shared);
                case Field.Slice slice -> slice(slice);
                case Field.TopSurface topSurface -> topSurface(topSurface);
                case Field.Ref ref -> throw new IllegalArgumentException("Density function reference '" + ref.id() + "' cannot be cloned for " + tag);
            };
        }

        private Field binary(Field.Binary binary) {
            Field left = apply(binary.left());
            Field right = apply(binary.right());
            return left == binary.left() && right == binary.right() ? binary : new Field.Binary(binary.op(), left, right);
        }

        private Field unary(Field.Unary unary) {
            Field input = apply(unary.input());
            return input == unary.input() ? unary : new Field.Unary(unary.op(), input);
        }

        private Field clamp(Field.Clamp clamp) {
            Field input = apply(clamp.input());
            return input == clamp.input() ? clamp : new Field.Clamp(input, clamp.min(), clamp.max());
        }

        private Field lerp(Field.Lerp lerp) {
            Field alpha = apply(lerp.alpha());
            Field first = apply(lerp.first());
            Field second = apply(lerp.second());
            if (alpha == lerp.alpha() && first == lerp.first() && second == lerp.second()) {
                return lerp;
            }
            return new Field.Lerp(alpha, first, second);
        }

        private Field round(Field.Round round) {
            Field input = apply(round.input());
            Field multiple = apply(round.multiple());
            if (input == round.input() && multiple == round.multiple()) {
                return round;
            }
            return new Field.Round(round.mode(), input, multiple);
        }

        private Field curve(Field.Curve curve) {
            Field input = apply(curve.input());
            boolean changed = input != curve.input();
            List<Field.Knot> knots = new ArrayList<>(curve.knots().size());
            for (Field.Knot knot : curve.knots()) {
                Field value = apply(knot.value());
                changed |= value != knot.value();
                knots.add(value == knot.value() ? knot : new Field.Knot(knot.location(), value, knot.derivative()));
            }
            return changed ? new Field.Curve(input, knots) : curve;
        }

        private Field partition(Field.Partition partition) {
            Field input = apply(partition.input());
            List<Field> leaves = applyAll(partition.leaves());
            if (input == partition.input() && leaves == partition.leaves()) {
                return partition;
            }
            return new Field.Partition(input, partition.thresholds(), leaves);
        }

        private Field mux(Field.Mux mux) {
            Field index = apply(mux.index());
            List<Field> cases = applyAll(mux.cases());
            return index == mux.index() && cases == mux.cases() ? mux : new Field.Mux(index, cases);
        }

        private Field gate(Field.Gate gate) {
            Field condition = apply(gate.condition());
            Field expr = apply(gate.expr());
            return condition == gate.condition() && expr == gate.expr() ? gate : new Field.Gate(condition, expr);
        }

        private Field rangeIf(Field.RangeIf rangeIf) {
            Field input = apply(rangeIf.input());
            Field inRange = apply(rangeIf.inRange());
            Field outOfRange = apply(rangeIf.outOfRange());
            if (input == rangeIf.input() && inRange == rangeIf.inRange() && outOfRange == rangeIf.outOfRange()) {
                return rangeIf;
            }
            return new Field.RangeIf(input, rangeIf.min(), rangeIf.max(), inRange, outOfRange);
        }

        private Field grid(Field.Grid grid) {
            Field expr = apply(grid.expr());
            return expr == grid.expr() ? grid : new Field.Grid(expr, grid.cellXZ(), grid.cellY());
        }

        private Field shared(Field.Shared shared) {
            Field expr = apply(shared.expr());
            return expr == shared.expr() ? shared : new Field.Shared(shared.name() + "/" + tag, expr);
        }

        private Field topSurface(Field.TopSurface topSurface) {
            Field density = apply(topSurface.density());
            if (density == topSurface.density()) {
                return topSurface;
            }
            return new Field.TopSurface(density, topSurface.upper(), topSurface.lower(), topSurface.step());
        }

        private List<Field> applyAll(List<Field> fields) {
            List<Field> result = new ArrayList<>(fields.size());
            boolean changed = false;
            for (Field field : fields) {
                Field rewritten = apply(field);
                changed |= rewritten != field;
                result.add(rewritten);
            }
            return changed ? result : fields;
        }
    }

    private static final class Displacement extends Rewriter {
        private final Field dx;
        private final Field dz;

        private Displacement(Field dx, Field dz, String tag) {
            super(tag);
            this.dx = dx;
            this.dz = dz;
        }

        @Override
        boolean skips(Field field) {
            EnumSet<Axis> axes = field.axes();
            return !axes.contains(Axis.X) && !axes.contains(Axis.Z);
        }

        @Override
        Field noise(Field.Noise noise) {
            Field shiftX = combine(apply(noise.shiftX()), noise.xzScale() == 0.0 ? null : scale(dx, noise.xzScale()));
            Field shiftY = apply(noise.shiftY());
            Field shiftZ = combine(apply(noise.shiftZ()), noise.xzScale() == 0.0 ? null : scale(dz, noise.xzScale()));
            return new Field.Noise(noise.noiseId(), noise.xzScale(), noise.yScale(), shiftX, shiftY, shiftZ);
        }

        @Override
        Field coord(Field.Coord coord) {
            Field displacement = coord.axis() == Axis.X ? dx : dz;
            if (displacement instanceof Field.Const constant) {
                double shift = Math.rint(constant.value());
                if (shift == 0.0) {
                    return coord;
                }
                return new Field.Coord(coord.axis(), coord.tiling(), coord.from() - shift, coord.to() - shift, coord.fromValue(), coord.toValue());
            }
            if (coord.tiling() != Field.Tiling.CLAMP) {
                throw new IllegalArgumentException("A " + coord.tiling() + " coordinate gradient cannot be displaced by a field for " + tag());
            }
            double factor = (coord.toValue() - coord.fromValue()) / (coord.to() - coord.from());
            return new Field.Binary(Field.BinaryOp.ADD, coord, scale(displacement, factor));
        }

        @Override
        Field slice(Field.Slice slice) {
            Field expr = slice.axis() == Axis.Y
                    ? apply(slice.expr())
                    : new Displacement(slice.axis() == Axis.X ? ZERO : dx, slice.axis() == Axis.Z ? ZERO : dz, tag()).apply(slice.expr());
            return expr == slice.expr() ? slice : new Field.Slice(expr, slice.axis(), slice.value());
        }

        private static Field combine(Field first, Field second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            if (first instanceof Field.Const left && second instanceof Field.Const right) {
                return new Field.Const(left.value() + right.value());
            }
            return new Field.Binary(Field.BinaryOp.ADD, first, second);
        }
    }

    private static final class Substitution extends Rewriter {
        private final Field target;
        private final Field replacement;

        private Substitution(Field target, Field replacement, String tag) {
            super(tag);
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        Field apply(Field field) {
            return field == target ? replacement : super.apply(field);
        }

        @Override
        boolean skips(Field field) {
            return field instanceof Field.Const || field instanceof Field.Coord || field instanceof Field.Ref;
        }

        @Override
        Field noise(Field.Noise noise) {
            Field shiftX = apply(noise.shiftX());
            Field shiftY = apply(noise.shiftY());
            Field shiftZ = apply(noise.shiftZ());
            if (shiftX == noise.shiftX() && shiftY == noise.shiftY() && shiftZ == noise.shiftZ()) {
                return noise;
            }
            return new Field.Noise(noise.noiseId(), noise.xzScale(), noise.yScale(), shiftX, shiftY, shiftZ);
        }

        @Override
        Field coord(Field.Coord coord) {
            return coord;
        }

        @Override
        Field slice(Field.Slice slice) {
            Field expr = apply(slice.expr());
            return expr == slice.expr() ? slice : new Field.Slice(expr, slice.axis(), slice.value());
        }
    }

    private static final class Lift extends Rewriter {
        private final double verticalRatio;

        private Lift(double verticalRatio, String tag) {
            super(tag);
            this.verticalRatio = verticalRatio;
        }

        @Override
        boolean skips(Field field) {
            EnumSet<Axis> axes = field.axes();
            return !axes.contains(Axis.X) && !axes.contains(Axis.Z);
        }

        @Override
        Field noise(Field.Noise noise) {
            double yScale = noise.yScale() == 0.0 && noise.xzScale() != 0.0 ? noise.xzScale() * verticalRatio : noise.yScale();
            Field shiftX = apply(noise.shiftX());
            Field shiftY = apply(noise.shiftY());
            Field shiftZ = apply(noise.shiftZ());
            if (yScale == noise.yScale() && shiftX == noise.shiftX() && shiftY == noise.shiftY() && shiftZ == noise.shiftZ()) {
                return noise;
            }
            return new Field.Noise(noise.noiseId(), noise.xzScale(), yScale, shiftX, shiftY, shiftZ);
        }

        @Override
        Field coord(Field.Coord coord) {
            return coord;
        }

        @Override
        Field slice(Field.Slice slice) {
            Field expr = apply(slice.expr());
            return expr == slice.expr() ? slice : new Field.Slice(expr, slice.axis(), slice.value());
        }
    }
}
