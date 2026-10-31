package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VanillaBounds {
    private static final double NOISE_DEVIATION = 0.3333333333333333;
    private static final double NOISE_DEVIATIONS = 6.0;
    private static final double OCTAVE_PERSISTENCE = 0.5;

    private final Map<String, VanillaInterval> noises;
    private final Map<Field, VanillaInterval> ranges = new IdentityHashMap<>();

    private VanillaBounds(Map<String, VanillaInterval> noises) {
        this.noises = noises;
    }

    public static VanillaBounds of(String namespace, Collection<NoiseDef> noises) {
        Map<String, VanillaInterval> ranges = new HashMap<>(noises.size() * 2);
        for (NoiseDef noise : noises) {
            ranges.put(namespace + ":" + noise.id(), noiseRange(noise));
        }
        return new VanillaBounds(ranges);
    }

    public Field fold(Field.Binary binary) {
        if (binary.op() != Field.BinaryOp.MIN && binary.op() != Field.BinaryOp.MAX) {
            return binary;
        }
        VanillaInterval left = interval(binary.left());
        VanillaInterval right = interval(binary.right());
        if (!left.isKnown() || !right.isKnown()) {
            return binary;
        }
        boolean min = binary.op() == Field.BinaryOp.MIN;
        if (left.max() < right.min()) {
            return min ? binary.left() : binary.right();
        }
        if (right.max() < left.min()) {
            return min ? binary.right() : binary.left();
        }
        return binary;
    }

    public Optional<ValueRange> range(Field field) {
        VanillaInterval interval = interval(field);
        if (!interval.isKnown() || interval.isNaI() || interval.min() == Float.POSITIVE_INFINITY || interval.max() == Float.NEGATIVE_INFINITY) {
            return Optional.empty();
        }
        return Optional.of(new ValueRange(interval.min(), interval.max()));
    }

    private VanillaInterval interval(Field field) {
        VanillaInterval known = ranges.get(field);
        if (known != null) {
            return known;
        }
        VanillaInterval computed = compute(field);
        ranges.put(field, computed);
        return computed;
    }

    private VanillaInterval compute(Field field) {
        return switch (field) {
            case Field.Const constant -> VanillaInterval.ofExact((float) constant.value());
            case Field.Coord coord -> VanillaInterval.encapsulating((float) coord.fromValue(), (float) coord.toValue());
            case Field.Noise noise -> noises.getOrDefault(noise.noiseId(), VanillaInterval.UNKNOWN);
            case Field.Binary binary -> binary(binary);
            case Field.Unary unary -> unary(unary);
            case Field.Clamp clamp -> clamp(clamp);
            case Field.Lerp lerp -> lerp(lerp);
            case Field.Round round -> round(round);
            case Field.Curve curve -> spline(curve);
            case Field.Partition partition -> encapsulating(partition.leaves());
            case Field.Mux mux -> mux.cases().size() == 1 ? interval(mux.cases().getFirst()) : spline(SplineLowering.mux(mux));
            case Field.Gate gate -> spline(SplineLowering.gate(gate));
            case Field.RangeIf rangeIf -> encapsulating(List.of(rangeIf.inRange(), rangeIf.outOfRange()));
            case Field.Grid grid -> interval(grid.expr());
            case Field.Shared shared -> interval(shared.expr());
            case Field.Slice slice -> interval(slice.expr());
            case Field.TopSurface topSurface -> VanillaInterval.of(topSurface.lower(), Math.max(topSurface.lower(), (float) topSurface.upper()));
            case Field.Ref ignored -> VanillaInterval.UNKNOWN;
        };
    }

    private VanillaInterval binary(Field.Binary binary) {
        Field folded = fold(binary);
        if (folded != binary) {
            return interval(folded);
        }
        Field base = binary.op() == Field.BinaryOp.POW ? FieldDomains.clampPowBase(binary.left(), binary.right()) : binary.left();
        VanillaInterval left = interval(base);
        VanillaInterval right = interval(binary.right());
        if (!left.isKnown() || !right.isKnown()) {
            return VanillaInterval.UNKNOWN;
        }
        return switch (binary.op()) {
            case ADD -> VanillaInterval.add(left, right);
            case SUB -> VanillaInterval.sub(left, right);
            case MUL -> VanillaInterval.mul(left, right);
            case DIV -> VanillaInterval.div(left, right);
            case MIN -> VanillaInterval.min(left, right);
            case MAX -> VanillaInterval.max(left, right);
            case POW -> VanillaInterval.pow(left, right);
        };
    }

    private VanillaInterval unary(Field.Unary unary) {
        VanillaInterval input = interval(FieldDomains.unaryInput(unary));
        if (!input.isKnown()) {
            return VanillaInterval.UNKNOWN;
        }
        return switch (unary.op()) {
            case NEG -> VanillaInterval.sub(VanillaInterval.ofExact(0.0F), input);
            case ABS -> VanillaInterval.abs(input);
            case SQUARE -> VanillaInterval.square(input);
            case CUBE -> VanillaInterval.mapMonotonic(input, value -> value * value * value);
            case SQRT -> VanillaInterval.pow(input, VanillaInterval.ofExact(0.5F));
            case LOG -> VanillaInterval.log(input);
            case SIGN -> VanillaInterval.sign(input);
            case SQUEEZE -> VanillaInterval.mapMonotonic(input, VanillaBounds::squeeze);
            case HALF_NEG -> VanillaInterval.mapMonotonic(input, value -> value > 0.0F ? value : value * 0.5F);
            case QUARTER_NEG -> VanillaInterval.mapMonotonic(input, value -> value > 0.0F ? value : value * 0.25F);
        };
    }

    private VanillaInterval clamp(Field.Clamp clamp) {
        VanillaInterval input = interval(clamp.input());
        return input.isKnown() ? VanillaInterval.clamp(input, (float) clamp.min(), (float) clamp.max()) : VanillaInterval.UNKNOWN;
    }

    private VanillaInterval lerp(Field.Lerp lerp) {
        VanillaInterval alpha = interval(lerp.alpha());
        VanillaInterval first = interval(lerp.first());
        VanillaInterval second = interval(lerp.second());
        if (!alpha.isKnown() || !first.isKnown() || !second.isKnown()) {
            return VanillaInterval.UNKNOWN;
        }
        return VanillaInterval.lerp(alpha, first, second);
    }

    private VanillaInterval round(Field.Round round) {
        VanillaInterval input = interval(round.input());
        VanillaInterval multiple = interval(round.multiple());
        if (!input.isKnown() || !multiple.isKnown()) {
            return VanillaInterval.UNKNOWN;
        }
        VanillaInterval steps = VanillaInterval.mapMonotonic(VanillaInterval.div(input, multiple), value -> roundToInteger(value, round.mode()));
        return steps.isKnown() ? VanillaInterval.mul(steps, multiple) : VanillaInterval.UNKNOWN;
    }

    private VanillaInterval encapsulating(List<Field> fields) {
        List<VanillaInterval> intervals = new ArrayList<>(fields.size());
        for (Field field : fields) {
            intervals.add(interval(field));
        }
        return VanillaInterval.encapsulating(intervals);
    }

    private VanillaInterval spline(Field.Curve curve) {
        VanillaInterval input = interval(curve.input());
        if (!input.isKnown() || input.isNaI()) {
            return input;
        }
        List<Field.Knot> knots = curve.knots();
        int last = knots.size() - 1;
        float[] locations = new float[knots.size()];
        float[] derivatives = new float[knots.size()];
        List<VanillaInterval> values = new ArrayList<>(knots.size());
        for (int index = 0; index <= last; index++) {
            Field.Knot knot = knots.get(index);
            locations[index] = (float) knot.location();
            derivatives[index] = (float) knot.derivative();
            VanillaInterval value = knot.value() instanceof Field.Curve nested ? spline(nested) : interval(knot.value());
            if (!value.isKnown() || value.isNaI()) {
                return VanillaInterval.UNKNOWN;
            }
            values.add(value);
        }
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;
        if (input.min() < locations[0]) {
            float lower = linearExtend(input.min(), locations, values.getFirst().min(), derivatives, 0);
            float upper = linearExtend(input.min(), locations, values.getFirst().max(), derivatives, 0);
            minValue = Math.min(minValue, Math.min(lower, upper));
            maxValue = Math.max(maxValue, Math.max(lower, upper));
        }
        if (input.max() > locations[last]) {
            float lower = linearExtend(input.max(), locations, values.get(last).min(), derivatives, last);
            float upper = linearExtend(input.max(), locations, values.get(last).max(), derivatives, last);
            minValue = Math.min(minValue, Math.min(lower, upper));
            maxValue = Math.max(maxValue, Math.max(lower, upper));
        }
        for (VanillaInterval value : values) {
            minValue = Math.min(minValue, value.min());
            maxValue = Math.max(maxValue, value.max());
        }
        for (int index = 0; index < last; index++) {
            float first = derivatives[index];
            float second = derivatives[index + 1];
            if (first == 0.0F && second == 0.0F) {
                continue;
            }
            float width = locations[index + 1] - locations[index];
            VanillaInterval start = values.get(index);
            VanillaInterval end = values.get(index + 1);
            float startSlope = first * width;
            float endSlope = second * width;
            float minA = startSlope - end.max() + start.min();
            float maxA = startSlope - end.min() + start.max();
            float minB = -endSlope + end.min() - start.max();
            float maxB = -endSlope + end.max() - start.min();
            minValue = Math.min(minValue, Math.min(start.min(), end.min()) + 0.25F * Math.min(minA, minB));
            maxValue = Math.max(maxValue, Math.max(start.max(), end.max()) + 0.25F * Math.max(maxA, maxB));
        }
        return VanillaInterval.of(minValue, maxValue);
    }

    private static float linearExtend(float input, float[] locations, float value, float[] derivatives, int index) {
        float derivative = derivatives[index];
        return derivative == 0.0F ? value : value + derivative * (input - locations[index]);
    }

    private static VanillaInterval noiseRange(NoiseDef noise) {
        double[] amplitudes = new double[noise.amplitudes().size()];
        double amplitude = 1.0;
        int count = 0;
        for (double modifier : noise.amplitudes()) {
            if (modifier != 0.0) {
                amplitudes[count++] = Math.abs(amplitude * modifier);
            }
            amplitude *= OCTAVE_PERSISTENCE;
        }
        double target = Arrays.stream(amplitudes, 0, count).sum();
        return VanillaInterval.ofSymmetric((float) (target * NOISE_DEVIATION * NOISE_DEVIATIONS));
    }

    private static float roundToInteger(float value, Field.RoundMode mode) {
        return switch (mode) {
            case FLOOR -> (float) Math.floor(value);
            case ROUND -> Math.round(value);
            case CEIL -> (float) Math.ceil(value);
            case TRUNCATE -> value > 0.0F ? (float) Math.floor(value) : (float) Math.ceil(value);
        };
    }

    private static float squeeze(float value) {
        float clamped = value < -1.0F ? -1.0F : Math.min(value, 1.0F);
        return clamped / 2.0F - clamped * clamped * clamped / 24.0F;
    }
}
