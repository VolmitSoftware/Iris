package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FieldEvaluator {
    private static final int MODEL_CACHE_SIZE = 4096;
    private static final Cache<ModelKey, VanillaNoiseModel> MODELS = Caffeine.newBuilder().maximumSize(MODEL_CACHE_SIZE).build();

    private final Map<String, NoiseDef> noises;
    private final long worldSeed;
    private final DataVersion target;

    private FieldEvaluator(Map<String, NoiseDef> noises, long worldSeed, DataVersion target) {
        this.noises = noises;
        this.worldSeed = worldSeed;
        this.target = target;
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
    }

    public static double sample(Field field, Map<String, NoiseDef> noises, long worldSeed, DataVersion target, int x, int y, int z) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(noises, "noises");
        Objects.requireNonNull(target, "target");
        return new FieldEvaluator(noises, worldSeed, target).evaluate(field, new Point(x, y, z));
    }

    private double evaluate(Field field, Point point) {
        return switch (field) {
            case Field.Const constant -> narrow(constant.value());
            case Field.Coord coord -> coord(coord, point);
            case Field.Noise noise -> noise(noise, point);
            case Field.Binary binary -> binary(binary, point);
            case Field.Unary unary -> narrow(unary(unary.op(), evaluate(unary.input(), point)));
            case Field.Clamp clamp -> clamp(evaluate(clamp.input(), point), narrow(clamp.min()), narrow(clamp.max()));
            case Field.Lerp lerp -> lerp(lerp, point);
            case Field.Round round -> round(round, point);
            case Field.Curve curve -> spline(curve, point);
            case Field.Partition partition -> evaluate(partition.leaves().get(intervalIndex(evaluate(partition.input(), point), partition.thresholds())), point);
            case Field.Mux mux -> evaluate(mux.cases().get(caseIndex(evaluate(mux.index(), point), mux.cases().size())), point);
            case Field.Gate gate -> evaluate(gate.condition(), point) > 0.0 ? evaluate(gate.expr(), point) : 0.0;
            case Field.RangeIf rangeIf -> rangeIf(rangeIf, point);
            case Field.Grid grid -> grid(grid, point);
            case Field.Shared shared -> shared(shared, point);
            case Field.Slice slice -> evaluate(slice.expr(), point.pinned(slice.axis(), slice.value()));
            case Field.TopSurface topSurface -> topSurface(topSurface, point);
            case Field.Ref ref -> throw new IllegalArgumentException("Field evaluation cannot resolve density function reference '" + ref.id() + "'");
        };
    }

    private double coord(Field.Coord coord, Point point) {
        int from = blockCoordinate(coord.from(), "from");
        int to = blockCoordinate(coord.to(), "to");
        int range = to - from;
        double fromValue = narrow(coord.fromValue());
        double factor = narrow(narrow(narrow(coord.toValue()) - fromValue) / range);
        int coordinate = point.get(coord.axis());
        int offset = switch (coord.tiling()) {
            case CLAMP -> Math.clamp(coordinate, Math.min(from, to), Math.max(from, to)) - from;
            case REPEAT -> Math.floorMod(coordinate - from, range);
            case MIRRORED_REPEAT -> mirrored(coordinate - from, range);
        };
        return narrow(fromValue + narrow(offset * factor));
    }

    private double noise(Field.Noise noise, Point point) {
        NoiseDef definition = noises.get(noise.noiseId());
        if (definition == null) {
            throw new IllegalArgumentException("Noise '" + noise.noiseId() + "' has no definition among " + noises.keySet());
        }
        VanillaNoiseModel model = MODELS.get(new ModelKey(noise.noiseId(), definition, worldSeed, target),
                key -> VanillaNoiseModel.create(key.noiseId(), key.definition().firstOctave(), key.definition().amplitudes(), key.worldSeed(), key.target()));
        double x = point.x() * noise.xzScale() + shift(noise.shiftX(), point);
        double y = point.y() * noise.yScale() + shift(noise.shiftY(), point);
        double z = point.z() * noise.xzScale() + shift(noise.shiftZ(), point);
        return narrow(model.sample(x, y, z));
    }

    private double shift(Field shift, Point point) {
        return shift == null ? 0.0 : evaluate(shift, point);
    }

    private double binary(Field.Binary binary, Point point) {
        double left = evaluate(binary.left(), point);
        if (binary.op() == Field.BinaryOp.DIV && left == 0.0) {
            return 0.0;
        }
        double right = evaluate(binary.right(), point);
        return narrow(switch (binary.op()) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> left / right;
            case MIN -> Math.min(left, right);
            case MAX -> Math.max(left, right);
            case POW -> Math.pow(left, right);
        });
    }

    private double lerp(Field.Lerp lerp, Point point) {
        double alpha = evaluate(lerp.alpha(), point);
        if (alpha == 0.0) {
            return evaluate(lerp.first(), point);
        }
        if (alpha == 1.0) {
            return evaluate(lerp.second(), point);
        }
        double first = evaluate(lerp.first(), point);
        double second = evaluate(lerp.second(), point);
        return narrow(first + narrow(alpha * narrow(second - first)));
    }

    private double round(Field.Round round, Point point) {
        double input = evaluate(round.input(), point);
        double multiple = evaluate(round.multiple(), point);
        if (multiple == 0.0) {
            return input;
        }
        return narrow(roundToInteger(round.mode(), narrow(input / multiple)) * multiple);
    }

    private double roundToInteger(Field.RoundMode mode, double value) {
        return switch (mode) {
            case FLOOR -> Math.floor(value);
            case ROUND -> Math.round((float) value);
            case CEIL -> Math.ceil(value);
            case TRUNCATE -> value > 0.0 ? Math.floor(value) : Math.ceil(value);
        };
    }

    private float spline(Field.Curve curve, Point point) {
        float coordinate = (float) evaluate(curve.input(), point);
        List<Field.Knot> knots = curve.knots();
        int start = intervalStart(knots, coordinate);
        int last = knots.size() - 1;
        if (start < 0) {
            return extend(coordinate, knots.getFirst(), point);
        }
        if (start == last) {
            return extend(coordinate, knots.get(last), point);
        }
        Field.Knot lower = knots.get(start);
        Field.Knot upper = knots.get(start + 1);
        float lowerLocation = (float) lower.location();
        float upperLocation = (float) upper.location();
        float t = (coordinate - lowerLocation) / (upperLocation - lowerLocation);
        float lowerValue = knotValue(lower, point);
        float upperValue = knotValue(upper, point);
        float width = upperLocation - lowerLocation;
        float rise = upperValue - lowerValue;
        float lowerTangent = (float) lower.derivative() * width - rise;
        float upperTangent = -(float) upper.derivative() * width + rise;
        return lerp(t, lowerValue, upperValue) + t * (1.0F - t) * lerp(t, lowerTangent, upperTangent);
    }

    private float extend(float coordinate, Field.Knot knot, Point point) {
        float value = knotValue(knot, point);
        float derivative = (float) knot.derivative();
        if (derivative == 0.0F) {
            return value;
        }
        return value + derivative * (coordinate - (float) knot.location());
    }

    private float knotValue(Field.Knot knot, Point point) {
        return switch (knot.value()) {
            case Field.Const constant -> (float) constant.value();
            case Field.Curve nested -> spline(nested, point);
            default -> throw new IllegalStateException("Curve knot value must be a constant or a nested curve");
        };
    }

    private int intervalIndex(double input, List<Double> thresholds) {
        for (int index = 0; index < thresholds.size(); index++) {
            if (input < narrow(thresholds.get(index))) {
                return index;
            }
        }
        return thresholds.size();
    }

    private double rangeIf(Field.RangeIf rangeIf, Point point) {
        double input = evaluate(rangeIf.input(), point);
        boolean inside = input >= narrow(rangeIf.min()) && input < narrow(rangeIf.max());
        return evaluate(inside ? rangeIf.inRange() : rangeIf.outOfRange(), point);
    }

    private double grid(Field.Grid grid, Point point) {
        EnumSet<Axis> axes = grid.expr().axes();
        int cellXz = grid.cellXZ();
        int cellY = grid.cellY();
        int offsetX = axes.contains(Axis.X) ? Math.floorMod(point.x(), cellXz) : 0;
        int offsetY = axes.contains(Axis.Y) ? Math.floorMod(point.y(), cellY) : 0;
        int offsetZ = axes.contains(Axis.Z) ? Math.floorMod(point.z(), cellXz) : 0;
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return evaluate(grid.expr(), point);
        }
        int baseX = point.x() - offsetX;
        int baseY = point.y() - offsetY;
        int baseZ = point.z() - offsetZ;
        int stepX = offsetX == 0 ? 0 : cellXz;
        int stepY = offsetY == 0 ? 0 : cellY;
        int stepZ = offsetZ == 0 ? 0 : cellXz;
        int collapsed = (stepX == 0 ? 1 : 0) | (stepY == 0 ? 2 : 0) | (stepZ == 0 ? 4 : 0);
        double[] corners = new double[8];
        for (int index = 0; index < corners.length; index++) {
            int source = index & ~collapsed;
            if (source != index) {
                corners[index] = corners[source];
                continue;
            }
            int cornerX = baseX + ((index & 1) == 0 ? 0 : stepX);
            int cornerY = baseY + ((index & 2) == 0 ? 0 : stepY);
            int cornerZ = baseZ + ((index & 4) == 0 ? 0 : stepZ);
            corners[index] = evaluate(grid.expr(), new Point(cornerX, cornerY, cornerZ));
        }
        double fractionX = narrow(offsetX / (double) cellXz);
        double fractionY = narrow(offsetY / (double) cellY);
        double fractionZ = narrow(offsetZ / (double) cellXz);
        double bottom = lerp2(fractionX, fractionY, corners[0], corners[1], corners[2], corners[3]);
        double top = lerp2(fractionX, fractionY, corners[4], corners[5], corners[6], corners[7]);
        return lerpNarrow(fractionZ, bottom, top);
    }

    private double lerp2(double fractionX, double fractionY, double lowLow, double highLow, double lowHigh, double highHigh) {
        return lerpNarrow(fractionY, lerpNarrow(fractionX, lowLow, highLow), lerpNarrow(fractionX, lowHigh, highHigh));
    }

    private double lerpNarrow(double delta, double start, double end) {
        return narrow(start + narrow(delta * narrow(end - start)));
    }

    private double shared(Field.Shared shared, Point point) {
        Map<Field.Shared, Double> memo = point.memo();
        Double cached = memo.get(shared);
        if (cached != null) {
            return cached;
        }
        double value = evaluate(shared.expr(), point);
        memo.put(shared, value);
        return value;
    }

    private double topSurface(Field.TopSurface topSurface, Point point) {
        int step = topSurface.step();
        int lower = topSurface.lower();
        int top = (int) Math.floor((float) topSurface.upper() / (float) step) * step;
        if (top <= lower) {
            return lower;
        }
        for (int y = top; y >= lower; y -= step) {
            if (evaluate(topSurface.density(), point.pinned(Axis.Y, y)) > 0.0) {
                return y;
            }
        }
        return lower;
    }

    private static double narrow(double value) {
        return (float) value;
    }

    private static double unary(Field.UnaryOp op, double value) {
        return switch (op) {
            case NEG -> -value;
            case ABS -> Math.abs(value);
            case SQUARE -> value * value;
            case CUBE -> value * value * value;
            case SQRT -> Math.sqrt(value);
            case LOG -> Math.log(value);
            case SIGN -> Math.signum(value);
            case SQUEEZE -> squeeze(value);
            case HALF_NEG -> value > 0.0 ? value : value * 0.5;
            case QUARTER_NEG -> value > 0.0 ? value : value * 0.25;
        };
    }

    private static double squeeze(double value) {
        double clamped = clamp(value, -1.0, 1.0);
        return clamped / 2.0 - clamped * clamped * clamped / 24.0;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int mirrored(int offset, int range) {
        int period = Math.floorDiv(offset, range);
        int remainder = offset - period * range;
        return (period & 1) == 0 ? remainder : range - remainder;
    }

    private static int blockCoordinate(double value, String role) {
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Coordinate gradient " + role + " must be a whole block coordinate, got " + value);
        }
        return (int) value;
    }

    private static int intervalStart(List<Field.Knot> knots, float coordinate) {
        int low = 0;
        int length = knots.size();
        while (length > 0) {
            int half = length / 2;
            int middle = low + half;
            if (coordinate < (float) knots.get(middle).location()) {
                length = half;
            } else {
                low = middle + 1;
                length -= half + 1;
            }
        }
        return low - 1;
    }

    private static int caseIndex(double index, int cases) {
        return (int) Math.clamp(Math.round(index), 0L, cases - 1L);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private record ModelKey(String noiseId, NoiseDef definition, long worldSeed, DataVersion target) {
    }

    private static final class Point {
        private final int x;
        private final int y;
        private final int z;
        private Map<Field.Shared, Double> memo;

        private Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private int z() {
            return z;
        }

        private int get(Axis axis) {
            return switch (axis) {
                case X -> x;
                case Y -> y;
                case Z -> z;
            };
        }

        private Point pinned(Axis axis, int value) {
            if (get(axis) == value) {
                return this;
            }
            return switch (axis) {
                case X -> new Point(value, y, z);
                case Y -> new Point(x, value, z);
                case Z -> new Point(x, y, value);
            };
        }

        private Map<Field.Shared, Double> memo() {
            if (memo == null) {
                memo = new IdentityHashMap<>();
            }
            return memo;
        }
    }
}
