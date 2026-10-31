package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.FieldDomains;
import art.arcane.iris.pack.datapack.export.lower.SplineLowering;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Objects;

final class DensityFunctionEmitter26_3 extends BaseDensityFunctionEmitter {
    private final VanillaBounds bounds;

    DensityFunctionEmitter26_3(String namespace, VanillaBounds bounds) {
        super(namespace, DataVersion.V26_3);
        this.bounds = Objects.requireNonNull(bounds);
    }

    @Override
    protected JsonElement node(Field field, FunctionTable table) {
        return switch (field) {
            case Field.Const constant -> number(constant.value());
            case Field.Coord coord -> gradient(coord);
            case Field.Noise noise -> noise(noise, table);
            case Field.Binary binary -> binary(binary, table);
            case Field.Unary unary -> unary(unaryType(unary.op()), "input", FieldDomains.unaryInput(unary), table);
            case Field.Clamp clamp -> clamp(clamp.input(), clamp.min(), clamp.max(), table);
            case Field.Lerp lerp -> lerp(lerp, table);
            case Field.Round round -> round(round, table);
            case Field.Curve curve -> spline(curve, table);
            case Field.Partition partition -> intervalSelect(partition.input(), partition.thresholds(), partition.leaves(), table);
            case Field.Mux mux -> mux.cases().size() == 1 ? node(mux.cases().getFirst(), table) : spline(SplineLowering.mux(mux), table);
            case Field.Gate gate -> spline(SplineLowering.gate(gate), table);
            case Field.RangeIf rangeIf -> rangeChoice(rangeIf.input(), rangeIf.min(), rangeIf.max(), rangeIf.inRange(), rangeIf.outOfRange(), table);
            case Field.Grid grid -> interpolated(grid, table);
            case Field.Shared shared -> shared(shared, "cache", "input", table);
            case Field.Slice slice -> slice(slice, table);
            case Field.TopSurface topSurface -> findTopSurface(topSurface, table);
            case Field.Ref ref -> new JsonPrimitive(ref.id());
        };
    }

    private JsonObject gradient(Field.Coord coord) {
        JsonObject object = typed("gradient");
        object.addProperty("axis", axisName(coord.axis()));
        object.addProperty("tiling", switch (coord.tiling()) {
            case CLAMP -> "clamp_to_edge";
            case REPEAT -> "repeat";
            case MIRRORED_REPEAT -> "mirrored_repeat";
        });
        object.addProperty("from_coordinate", requireInteger(coord.from(), "Gradient from coordinate"));
        object.addProperty("to_coordinate", requireInteger(coord.to(), "Gradient to coordinate"));
        object.add("from_value", number(coord.fromValue()));
        object.add("to_value", number(coord.toValue()));
        return object;
    }

    private JsonObject noise(Field.Noise noise, FunctionTable table) {
        JsonObject object = typed("noise");
        object.addProperty("noise", noise.noiseId());
        object.add("xz_scale", JsonNumbers.unbounded(noise.xzScale()));
        object.add("y_scale", JsonNumbers.unbounded(noise.yScale()));
        if (noise.shiftX() != null) {
            object.add("shift_x", node(noise.shiftX(), table));
        }
        if (noise.shiftY() != null) {
            object.add("shift_y", node(noise.shiftY(), table));
        }
        if (noise.shiftZ() != null) {
            object.add("shift_z", node(noise.shiftZ(), table));
        }
        return object;
    }

    private JsonElement binary(Field.Binary binary, FunctionTable table) {
        Field folded = bounds.fold(binary);
        if (folded != binary) {
            return node(folded, table);
        }
        if (binary.op() == Field.BinaryOp.POW) {
            return binary("pow", "base", FieldDomains.clampPowBase(binary.left(), binary.right()), "exponent", binary.right(), table);
        }
        String type = switch (binary.op()) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case MIN -> "min";
            case MAX -> "max";
            case POW -> "pow";
        };
        return binary(type, "left", binary.left(), "right", binary.right(), table);
    }

    private JsonObject lerp(Field.Lerp lerp, FunctionTable table) {
        JsonObject object = typed("lerp");
        object.add("alpha", node(lerp.alpha(), table));
        object.add("first", node(lerp.first(), table));
        object.add("second", node(lerp.second(), table));
        return object;
    }

    private JsonObject round(Field.Round round, FunctionTable table) {
        String type = switch (round.mode()) {
            case ROUND -> "round";
            case FLOOR -> "floor";
            case CEIL -> "ceil";
            case TRUNCATE -> "truncate";
        };
        return binary(type, "input", round.input(), "multiple", round.multiple(), table);
    }

    private JsonObject interpolated(Field.Grid grid, FunctionTable table) {
        JsonObject object = unary("interpolated", "input", grid.expr(), table);
        object.addProperty("cell_size_xz", grid.cellXZ());
        object.addProperty("cell_size_y", grid.cellY());
        return object;
    }

    private JsonObject slice(Field.Slice slice, FunctionTable table) {
        JsonObject object = typed("slice");
        object.addProperty("axis", axisName(slice.axis()));
        object.addProperty("coordinate", slice.value());
        object.add("input", node(slice.expr(), table));
        return object;
    }

    private static String unaryType(Field.UnaryOp op) {
        return switch (op) {
            case NEG -> "negate";
            case ABS -> "abs";
            case SQUARE -> "square";
            case CUBE -> "cube";
            case SQRT -> "sqrt";
            case LOG -> "log";
            case SIGN -> "sign";
            case SQUEEZE -> "squeeze";
            case HALF_NEG -> "half_negative";
            case QUARTER_NEG -> "quarter_negative";
        };
    }
}
