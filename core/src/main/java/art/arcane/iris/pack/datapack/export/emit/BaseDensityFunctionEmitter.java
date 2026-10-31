package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

abstract class BaseDensityFunctionEmitter implements DensityFunctionEmitter {
    private static final int LOWEST_SURFACE_BOUND = -4064;
    private static final int HIGHEST_SURFACE_BOUND = 4062;

    private final String namespace;
    private final DataVersion target;
    private final Map<FunctionTable, Map<Field.Shared, String>> sharedReferences = new WeakHashMap<>();

    BaseDensityFunctionEmitter(String namespace, DataVersion target) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "density function namespace");
        this.target = target;
    }

    @Override
    public final JsonElement emit(Field field, FunctionTable table) {
        Objects.requireNonNull(field);
        Objects.requireNonNull(table);
        if (!table.namespace().equals(namespace)) {
            throw new IllegalArgumentException("Density function table namespace '" + table.namespace() + "' does not match emitter namespace '" + namespace + "'");
        }
        return node(field, table);
    }

    protected abstract JsonElement node(Field field, FunctionTable table);

    final JsonPrimitive number(double value) {
        return JsonNumbers.constant(value, target);
    }

    final JsonObject typed(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "minecraft:" + type);
        return object;
    }

    final JsonObject unary(String type, String inputKey, Field input, FunctionTable table) {
        JsonObject object = typed(type);
        object.add(inputKey, node(input, table));
        return object;
    }

    final JsonObject binary(String type, String leftKey, Field left, String rightKey, Field right, FunctionTable table) {
        JsonObject object = typed(type);
        object.add(leftKey, node(left, table));
        object.add(rightKey, node(right, table));
        return object;
    }

    final JsonObject clamp(Field input, double min, double max, FunctionTable table) {
        JsonObject object = unary("clamp", "input", input, table);
        object.add("min", number(min));
        object.add("max", number(max));
        return object;
    }

    final JsonObject spline(Field.Curve curve, FunctionTable table) {
        JsonObject object = typed("spline");
        object.add("spline", splineBody(curve, table));
        return object;
    }

    final JsonObject intervalSelect(Field input, List<Double> thresholds, List<Field> functions, FunctionTable table) {
        JsonObject object = unary("interval_select", "input", input, table);
        JsonArray thresholdArray = new JsonArray(thresholds.size());
        for (double threshold : thresholds) {
            thresholdArray.add(number(threshold));
        }
        JsonArray functionArray = new JsonArray(functions.size());
        for (Field function : functions) {
            functionArray.add(node(function, table));
        }
        object.add("thresholds", thresholdArray);
        object.add("functions", functionArray);
        return object;
    }

    final JsonObject rangeChoice(Field input, double min, double max, Field inRange, Field outOfRange, FunctionTable table) {
        JsonObject object = unary("range_choice", "input", input, table);
        object.add("min_inclusive", number(min));
        object.add("max_exclusive", number(max));
        object.add("when_in_range", node(inRange, table));
        object.add("when_out_of_range", node(outOfRange, table));
        return object;
    }

    final JsonObject findTopSurface(Field.TopSurface topSurface, FunctionTable table) {
        if (topSurface.lower() < LOWEST_SURFACE_BOUND || topSurface.lower() > HIGHEST_SURFACE_BOUND) {
            throw new IllegalArgumentException("Top surface lower bound must be within [-4064, 4062], got " + topSurface.lower());
        }
        JsonObject object = typed("find_top_surface");
        object.add("density", node(topSurface.density(), table));
        object.add("upper_bound", number(topSurface.upper()));
        object.addProperty("lower_bound", topSurface.lower());
        object.addProperty("cell_height", topSurface.step());
        return object;
    }

    final JsonPrimitive shared(Field.Shared shared, String wrapperType, String inputKey, FunctionTable table) {
        Map<Field.Shared, String> references = sharedReferences.computeIfAbsent(table, ignored -> new IdentityHashMap<>());
        String existing = references.get(shared);
        if (existing != null) {
            return new JsonPrimitive(existing);
        }
        String reference = table.register(shared.name(), unary(wrapperType, inputKey, shared.expr(), table));
        references.put(shared, reference);
        return new JsonPrimitive(reference);
    }

    static String axisName(Axis axis) {
        return axis.name().toLowerCase(Locale.ROOT);
    }

    static int requireInteger(double value, String role) {
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(role + " must be an integer, got " + value);
        }
        return (int) value;
    }

    private JsonObject splineBody(Field.Curve curve, FunctionTable table) {
        JsonArray points = new JsonArray(curve.knots().size());
        for (Field.Knot knot : curve.knots()) {
            JsonObject point = new JsonObject();
            point.add("location", number(knot.location()));
            point.add("value", knot.value() instanceof Field.Curve nested ? splineBody(nested, table) : number(((Field.Const) knot.value()).value()));
            point.add("derivative", number(knot.derivative()));
            points.add(point);
        }
        JsonObject body = new JsonObject();
        body.add("coordinate", node(curve.input(), table));
        body.add("points", points);
        return body;
    }
}
