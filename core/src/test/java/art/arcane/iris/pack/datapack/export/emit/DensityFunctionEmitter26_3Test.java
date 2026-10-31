package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DensityFunctionEmitter26_3Test {
    private static final String NAMESPACE = "iris_export";
    private static final Field NOISE = new Field.Noise("iris_export:base", 0.25, 0.0, null, null, null);
    private static final Field NOISE_3D = new Field.Noise("iris_export:base", 0.25, 0.5, null, null, null);
    private static final String NOISE_JSON = "{\"type\":\"minecraft:noise\",\"noise\":\"iris_export:base\",\"xz_scale\":0.25,\"y_scale\":0.0}";
    private static final String NOISE_3D_JSON = "{\"type\":\"minecraft:noise\",\"noise\":\"iris_export:base\",\"xz_scale\":0.25,\"y_scale\":0.5}";

    @Test
    public void disjointMinMaxEmitsTheInputThatAlwaysWins() {
        Field band = new Field.Clamp(NOISE, 0.25, 0.75);
        DensityFunctionEmitter emitter = DensityFunctionEmitter.forTarget(ExportCapabilities.forTarget(DataVersion.V26_3), NAMESPACE,
                VanillaBounds.of(NAMESPACE, List.of(new NoiseDef("base", 0, List.of(1.0)))));
        JsonElement clamp = emitter.emit(band, new FunctionTable(NAMESPACE));
        assertEquals(clamp, emitter.emit(new Field.Binary(Field.BinaryOp.MAX, band, new Field.Const(0.0)), new FunctionTable(NAMESPACE)));
        assertEquals(clamp, emitter.emit(new Field.Binary(Field.BinaryOp.MIN, new Field.Const(2.0), band), new FunctionTable(NAMESPACE)));
        assertEquals(json("0.125"), emitter.emit(new Field.Binary(Field.BinaryOp.MIN, new Field.Const(0.125), band), new FunctionTable(NAMESPACE)));
        assertEquals("minecraft:max", type(emitter.emit(new Field.Binary(Field.BinaryOp.MAX, band, new Field.Const(0.5)), new FunctionTable(NAMESPACE))));
    }

    @Test
    public void sub26_3UsesLeftRight() {
        JsonElement emitted = emit(new Field.Binary(Field.BinaryOp.SUB, NOISE, new Field.Const(2.0)));
        assertEquals(json("{\"type\":\"minecraft:sub\",\"left\":" + NOISE_JSON + ",\"right\":2.0}"), emitted);
    }

    @Test
    public void unaryUsesInput() {
        JsonElement emitted = emit(new Field.Unary(Field.UnaryOp.HALF_NEG, NOISE));
        assertEquals(json("{\"type\":\"minecraft:half_negative\",\"input\":" + NOISE_JSON + "}"), emitted);
        assertEquals("minecraft:negate", type(emit(new Field.Unary(Field.UnaryOp.NEG, NOISE))));
        assertEquals("minecraft:quarter_negative", type(emit(new Field.Unary(Field.UnaryOp.QUARTER_NEG, NOISE))));
    }

    @Test
    public void powUsesBaseAndExponent() {
        Field base = new Field.Clamp(NOISE, 0.0, 1.0);
        JsonObject emitted = emit(new Field.Binary(Field.BinaryOp.POW, base, new Field.Const(1.5))).getAsJsonObject();
        assertEquals("minecraft:pow", emitted.get("type").getAsString());
        assertEquals(emit(base), emitted.get("base"));
        assertEquals(1.5, emitted.get("exponent").getAsDouble(), 0.0);
    }

    @Test
    public void noiseIsAlwaysARegisteredReference() {
        Field shifted = new Field.Noise("iris_export:base", 0.25, 0.0, new Field.Const(3.0), null, NOISE);
        JsonObject emitted = emit(shifted).getAsJsonObject();
        assertEquals("minecraft:noise", emitted.get("type").getAsString());
        assertEquals("iris_export:base", emitted.get("noise").getAsString());
        assertEquals(3.0, emitted.get("shift_x").getAsDouble(), 0.0);
        assertEquals(json(NOISE_JSON), emitted.get("shift_z"));
        assertTrue(!emitted.has("shift_y"));
    }

    @Test
    public void coordinateEmitsGradient() {
        Field coord = new Field.Coord(Axis.X, Field.Tiling.REPEAT, 0.0, 512.0, 0.0, 1.0);
        assertEquals(json("{\"type\":\"minecraft:gradient\",\"axis\":\"x\",\"tiling\":\"repeat\",\"from_coordinate\":0,\"to_coordinate\":512,\"from_value\":0.0,\"to_value\":1.0}"), emit(coord));
        Field fractional = new Field.Coord(Axis.X, Field.Tiling.CLAMP, 0.5, 512.0, 0.0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> emit(fractional));
    }

    @Test
    public void gridCustomCell26_3EmitsCellSizes() {
        JsonElement emitted = emit(new Field.Grid(NOISE_3D, 8, 4));
        assertEquals(json("{\"type\":\"minecraft:interpolated\",\"input\":" + NOISE_3D_JSON + ",\"cell_size_xz\":8,\"cell_size_y\":4}"), emitted);
    }

    @Test
    public void muxOn26_3IsSplineWithIdentityKnots() {
        Field second = new Field.Noise("iris_export:other", 0.5, 0.0, null, null, null);
        JsonObject emitted = emit(new Field.Mux(NOISE, List.of(NOISE_3D, second))).getAsJsonObject();
        assertEquals("minecraft:spline", emitted.get("type").getAsString());
        JsonObject spline = emitted.getAsJsonObject("spline");
        assertEquals(json(NOISE_JSON), spline.get("coordinate"));
        JsonArray points = spline.getAsJsonArray("points");
        List<Double> locations = new ArrayList<>();
        for (JsonElement point : points) {
            locations.add(point.getAsJsonObject().get("location").getAsDouble());
        }
        assertEquals(List.of(0.0, 0.25, 0.75, 1.0), locations);
        assertEquals(identity(NOISE_3D_JSON, "-2.0", "2.0"), points.get(0).getAsJsonObject().get("value"));
        assertEquals(0.0, points.get(1).getAsJsonObject().get("value").getAsDouble(), 0.0);
        assertEquals(0.0, points.get(2).getAsJsonObject().get("value").getAsDouble(), 0.0);
        assertEquals(identity(emit(second).toString(), "-2.0", "2.0"), points.get(3).getAsJsonObject().get("value"));
        for (JsonElement point : points) {
            assertEquals(0.0, point.getAsJsonObject().get("derivative").getAsDouble(), 0.0);
        }
    }

    @Test
    public void gateOn26_3IsSplineGate() {
        JsonObject emitted = emit(new Field.Gate(NOISE, NOISE_3D)).getAsJsonObject();
        JsonObject spline = emitted.getAsJsonObject("spline");
        assertEquals(json(NOISE_JSON), spline.get("coordinate"));
        JsonArray points = spline.getAsJsonArray("points");
        assertEquals(2, points.size());
        assertEquals(Float.MIN_VALUE, points.get(0).getAsJsonObject().get("location").getAsFloat(), 0.0f);
        assertEquals(0.0, points.get(0).getAsJsonObject().get("value").getAsDouble(), 0.0);
        assertEquals(Math.nextUp(Float.MIN_VALUE), points.get(1).getAsJsonObject().get("location").getAsFloat(), 0.0f);
        assertEquals(identity(NOISE_3D_JSON, "-2.0", "2.0"), points.get(1).getAsJsonObject().get("value"));
    }

    @Test
    public void identitySplineSpansAPowerOfTwoAroundTheCaseRange() {
        Field scaled = new Field.Binary(Field.BinaryOp.ADD, new Field.Const(1.0), new Field.Binary(Field.BinaryOp.MUL, new Field.Const(4.0), NOISE));
        Field reference = new Field.Ref("minecraft:overworld/continents");
        JsonArray points = emit(new Field.Mux(NOISE, List.of(scaled, reference))).getAsJsonObject().getAsJsonObject("spline").getAsJsonArray("points");

        assertEquals(identity(emit(scaled).toString(), "-8.0", "8.0"), points.get(0).getAsJsonObject().get("value"));
        assertEquals(identity("\"minecraft:overworld/continents\"", "-1000000.0", "1000000.0"), points.get(3).getAsJsonObject().get("value"));
    }

    @Test
    public void identitySplineIsExactForCodesInFloat() {
        Field code = new Field.Clamp(NOISE, -1.0, 1.0);
        JsonObject identity = emit(new Field.Mux(NOISE, List.of(code, code))).getAsJsonObject().getAsJsonObject("spline")
                .getAsJsonArray("points").get(0).getAsJsonObject().getAsJsonObject("value");

        for (float value : new float[]{-0.99f, -0.5f, -0.0123f, 0.0f, 0.2731f, 0.731f, 0.99f}) {
            assertEquals(value, vanillaSpline(identity, value), 1.0e-6f);
        }
    }

    @Test
    public void identitySplineIsExactForIndexValuesInFloat() {
        List<Field> leaves = new ArrayList<>();
        List<Double> thresholds = new ArrayList<>();
        for (int index = 0; index <= 312; index++) {
            leaves.add(new Field.Const(index));
            if (index > 0) {
                thresholds.add(-1.0 + 2.0 * index / 313.0);
            }
        }
        Field index = new Field.Partition(NOISE, thresholds, leaves);
        JsonObject identity = emit(new Field.Mux(NOISE, List.of(index, index))).getAsJsonObject().getAsJsonObject("spline")
                .getAsJsonArray("points").get(0).getAsJsonObject().getAsJsonObject("value");

        for (int value = 0; value <= 312; value++) {
            assertEquals(value, vanillaSpline(identity, value), 0.0f);
        }
    }

    @Test
    public void muxTakesTheCaseAtTheRoundedClampedIndex() {
        JsonObject spline = emit(new Field.Mux(NOISE, List.of(new Field.Clamp(NOISE, -1.0, 1.0), new Field.Clamp(NOISE, -1.0, 1.0),
                new Field.Clamp(NOISE, -1.0, 1.0)))).getAsJsonObject().getAsJsonObject("spline");
        JsonArray points = spline.getAsJsonArray("points");
        List<Double> locations = new ArrayList<>();
        for (JsonElement point : points) {
            locations.add(point.getAsJsonObject().get("location").getAsDouble());
        }

        assertEquals(List.of(0.0, 0.25, 0.75, 1.0, 1.25, 1.75, 2.0), locations);
        assertTrue(points.get(0).getAsJsonObject().get("value").isJsonObject());
        assertTrue(points.get(locations.size() - 1).getAsJsonObject().get("value").isJsonObject());
        assertEquals(0.0f, derivative(points, 0), 0.0f);
        assertEquals(0.0f, derivative(points, locations.size() - 1), 0.0f);
    }

    @Test
    public void constantMuxCasesAreKnotValues() {
        JsonArray points = emit(new Field.Mux(NOISE, List.of(new Field.Const(3.0), NOISE_3D))).getAsJsonObject()
                .getAsJsonObject("spline").getAsJsonArray("points");

        assertEquals(3.0, points.get(0).getAsJsonObject().get("value").getAsDouble(), 0.0);
        assertTrue(points.get(3).getAsJsonObject().get("value").isJsonObject());
    }

    @Test
    public void singleCaseMuxEmitsTheCase() {
        assertEquals(json(NOISE_3D_JSON), emit(new Field.Mux(NOISE, List.of(NOISE_3D))));
    }

    @Test
    public void gateOpensForEveryPositiveCondition() {
        JsonObject spline = emit(new Field.Gate(NOISE, new Field.Clamp(NOISE, -1.0, 1.0))).getAsJsonObject().getAsJsonObject("spline");
        JsonArray points = spline.getAsJsonArray("points");

        assertEquals(0.0f, vanillaSpline(spline, 0.0f), 0.0f);
        assertEquals(0.0f, vanillaSpline(spline, -3.0f), 0.0f);
        assertTrue(location(points, 0) > 0.0f);
        assertEquals(Math.nextUp(Float.MIN_VALUE), location(points, 1), 0.0f);
        assertTrue(points.get(1).getAsJsonObject().get("value").isJsonObject());
    }

    @Test
    public void curveEmitsNestedSplines() {
        Field.Curve inner = new Field.Curve(NOISE_3D, List.of(new Field.Knot(-1.0, new Field.Const(0.0), 0.0), new Field.Knot(1.0, new Field.Const(0.4), 0.1)));
        Field.Curve outer = new Field.Curve(NOISE, List.of(new Field.Knot(-1.0, new Field.Const(-0.5), 0.0), new Field.Knot(0.0, inner, 0.0)));
        JsonElement expected = json("{\"type\":\"minecraft:spline\",\"spline\":{\"coordinate\":" + NOISE_JSON + ",\"points\":["
                + "{\"location\":-1.0,\"value\":-0.5,\"derivative\":0.0},"
                + "{\"location\":0.0,\"value\":{\"coordinate\":" + NOISE_3D_JSON + ",\"points\":[{\"location\":-1.0,\"value\":0.0,\"derivative\":0.0},{\"location\":1.0,\"value\":0.4,\"derivative\":0.1}]},\"derivative\":0.0}]}}");
        assertEquals(expected, emit(outer));
    }

    @Test
    public void partitionIsIntervalSelect() {
        Field partition = new Field.Partition(NOISE, List.of(-0.2, 0.3), List.of(new Field.Const(-1.0), new Field.Const(0.0), new Field.Const(1.0)));
        assertEquals(json("{\"type\":\"minecraft:interval_select\",\"input\":" + NOISE_JSON + ",\"thresholds\":[-0.2,0.3],\"functions\":[-1.0,0.0,1.0]}"), emit(partition));
    }

    @Test
    public void sharedEmitsCacheAndNamedFile() {
        FunctionTable table = new FunctionTable(NAMESPACE);
        Field.Shared shared = new Field.Shared("terrain/height", new Field.Binary(Field.BinaryOp.MUL, NOISE, new Field.Const(16.0)));
        JsonElement emitted = emitter().emit(new Field.Binary(Field.BinaryOp.ADD, shared, new Field.Binary(Field.BinaryOp.MUL, shared, new Field.Const(2.0))), table);
        assertEquals(1, table.files().size());
        assertEquals(json("{\"type\":\"minecraft:cache\",\"input\":{\"type\":\"minecraft:mul\",\"left\":" + NOISE_JSON + ",\"right\":16.0}}"), table.files().get("terrain/height"));
        JsonObject root = emitted.getAsJsonObject();
        assertEquals("iris_export:terrain/height", root.get("left").getAsString());
        assertEquals("iris_export:terrain/height", root.getAsJsonObject("right").get("left").getAsString());
    }

    @Test
    public void constantsRoundTripAsFloat26_3() {
        assertEquals("0.1", JsonNumbers.constant(0.1, DataVersion.V26_3).toString());
        assertEquals("0.12345679", JsonNumbers.constant(0.1234567890123, DataVersion.V26_3).toString());
        assertEquals("0.0001", JsonNumbers.constant(1.0e-4, DataVersion.V26_3).toString());
        assertEquals("2.0", JsonNumbers.constant(2.0, DataVersion.V26_3).toString());
        assertEquals("0.0", JsonNumbers.constant(-0.0, DataVersion.V26_3).toString());
        assertEquals(0.1, JsonNumbers.constant(0.1, DataVersion.V26_3).getAsDouble(), 0.0);
        assertEquals("0.1", emit(new Field.Const(0.1)).toString());
    }

    @Test
    public void constantBeyondLimitThrows() {
        assertEquals("1000000.0", JsonNumbers.constant(1.0e6, DataVersion.V26_3).toString());
        assertThrows(IllegalArgumentException.class, () -> JsonNumbers.constant(1.0e6 + 1.0, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class, () -> JsonNumbers.constant(-2.0e6, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class, () -> emit(new Field.Clamp(NOISE, -2.0e6, 0.0)));
    }

    @Test
    public void sqrtAndLogInputsAreClampedToTheirDomain() {
        JsonObject sqrt = emit(new Field.Unary(Field.UnaryOp.SQRT, NOISE)).getAsJsonObject();
        JsonObject sqrtInput = sqrt.getAsJsonObject("input");
        assertEquals("minecraft:clamp", sqrtInput.get("type").getAsString());
        assertEquals(0.0, sqrtInput.get("min").getAsDouble(), 0.0);
        JsonObject log = emit(new Field.Unary(Field.UnaryOp.LOG, NOISE)).getAsJsonObject();
        JsonObject logInput = log.getAsJsonObject("input");
        assertEquals("minecraft:clamp", logInput.get("type").getAsString());
        assertTrue(logInput.get("min").getAsDouble() > 0.0);
        Field positive = new Field.Clamp(NOISE, 0.5, 1.0);
        assertEquals(emit(positive), emit(new Field.Unary(Field.UnaryOp.SQRT, positive)).getAsJsonObject().get("input"));
    }

    @Test
    public void powBaseIsClampedWhenExponentIsFractional() {
        JsonObject fractional = emit(new Field.Binary(Field.BinaryOp.POW, NOISE, new Field.Const(1.5))).getAsJsonObject();
        assertEquals("minecraft:clamp", fractional.getAsJsonObject("base").get("type").getAsString());
        assertEquals(0.0, fractional.getAsJsonObject("base").get("min").getAsDouble(), 0.0);
        JsonObject negative = emit(new Field.Binary(Field.BinaryOp.POW, NOISE, new Field.Const(-0.5))).getAsJsonObject();
        assertTrue(negative.getAsJsonObject("base").get("min").getAsDouble() > 0.0);
        JsonObject integral = emit(new Field.Binary(Field.BinaryOp.POW, NOISE, new Field.Const(3.0))).getAsJsonObject();
        assertEquals(json(NOISE_JSON), integral.get("base"));
    }

    @Test
    public void remainingNodesUseTheirCodecFields() {
        assertEquals(json("{\"type\":\"minecraft:clamp\",\"input\":" + NOISE_JSON + ",\"min\":-0.5,\"max\":0.5}"), emit(new Field.Clamp(NOISE, -0.5, 0.5)));
        assertEquals(json("{\"type\":\"minecraft:lerp\",\"alpha\":" + NOISE_JSON + ",\"first\":1.0,\"second\":2.0}"), emit(new Field.Lerp(NOISE, new Field.Const(1.0), new Field.Const(2.0))));
        assertEquals(json("{\"type\":\"minecraft:floor\",\"input\":" + NOISE_JSON + ",\"multiple\":2.0}"), emit(new Field.Round(Field.RoundMode.FLOOR, NOISE, new Field.Const(2.0))));
        assertEquals(json("{\"type\":\"minecraft:range_choice\",\"input\":" + NOISE_JSON + ",\"min_inclusive\":0.0,\"max_exclusive\":1.0,\"when_in_range\":1.0,\"when_out_of_range\":0.0}"),
                emit(new Field.RangeIf(NOISE, 0.0, 1.0, new Field.Const(1.0), new Field.Const(0.0))));
        assertEquals(json("{\"type\":\"minecraft:slice\",\"axis\":\"y\",\"coordinate\":64,\"input\":" + NOISE_3D_JSON + "}"), emit(new Field.Slice(NOISE_3D, Axis.Y, 64)));
        assertEquals(json("{\"type\":\"minecraft:find_top_surface\",\"density\":" + NOISE_3D_JSON + ",\"upper_bound\":320.0,\"lower_bound\":-64,\"cell_height\":8}"), emit(new Field.TopSurface(NOISE_3D, 320, -64, 8)));
        assertEquals(json("\"minecraft:overworld/continents\""), emit(new Field.Ref("minecraft:overworld/continents")));
    }

    @Test
    public void tableNamespaceMustMatchEmitter() {
        assertThrows(IllegalArgumentException.class, () -> emitter().emit(NOISE, new FunctionTable("other")));
    }

    @Test
    public void emissionIsDeterministic() {
        Field.Shared shared = new Field.Shared("terrain/height", new Field.Binary(Field.BinaryOp.MUL, NOISE, new Field.Const(16.0)));
        Field root = new Field.Mux(new Field.Clamp(NOISE, 0.0, 1.0), List.of(shared, new Field.Gate(NOISE, new Field.Unary(Field.UnaryOp.SQRT, shared))));
        FunctionTable first = new FunctionTable(NAMESPACE);
        FunctionTable second = new FunctionTable(NAMESPACE);
        String firstRoot = emitter().emit(root, first).toString();
        String secondRoot = emitter().emit(root, second).toString();
        assertEquals(firstRoot, secondRoot);
        assertEquals(first.files().toString(), second.files().toString());
    }

    private static JsonElement identity(String coordinate, String low, String high) {
        return json("{\"coordinate\":" + coordinate + ",\"points\":[{\"location\":" + low + ",\"value\":" + low + ",\"derivative\":1.0},{\"location\":"
                + high + ",\"value\":" + high + ",\"derivative\":1.0}]}");
    }

    private static float vanillaSpline(JsonObject spline, float coordinate) {
        JsonArray points = spline.getAsJsonArray("points");
        int last = points.size() - 1;
        int start = -1;
        for (int index = 0; index <= last && coordinate >= location(points, index); index++) {
            start = index;
        }
        if (start < 0) {
            return value(points, 0) + derivative(points, 0) * (coordinate - location(points, 0));
        }
        if (start == last) {
            return value(points, last) + derivative(points, last) * (coordinate - location(points, last));
        }
        float low = location(points, start);
        float high = location(points, start + 1);
        float delta = (coordinate - low) / (high - low);
        float first = value(points, start);
        float second = value(points, start + 1);
        float entry = derivative(points, start) * (high - low) - (second - first);
        float exit = -derivative(points, start + 1) * (high - low) + (second - first);
        return first + delta * (second - first) + delta * (1.0f - delta) * (entry + delta * (exit - entry));
    }

    private static float location(JsonArray points, int index) {
        return points.get(index).getAsJsonObject().get("location").getAsFloat();
    }

    private static float derivative(JsonArray points, int index) {
        return points.get(index).getAsJsonObject().get("derivative").getAsFloat();
    }

    private static float value(JsonArray points, int index) {
        return points.get(index).getAsJsonObject().get("value").getAsFloat();
    }

    private static String type(JsonElement element) {
        return element.getAsJsonObject().get("type").getAsString();
    }

    private static DensityFunctionEmitter emitter() {
        return DensityFunctionEmitter.forTarget(ExportCapabilities.forTarget(DataVersion.V26_3), NAMESPACE, VanillaBounds.of(NAMESPACE, List.of()));
    }

    private static JsonElement emit(Field field) {
        return emitter().emit(field, new FunctionTable(NAMESPACE));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }
}
