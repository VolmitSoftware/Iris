package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.emit.DensityFunctionEmitter;
import art.arcane.iris.pack.datapack.export.emit.FunctionTable;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class VanillaBoundsParity263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final String NAMESPACE = "iris_export";
    private static final List<NoiseDef> NOISES = List.of(new NoiseDef("parity/base", -3, List.of(1.0, 0.5, 0.25)),
            new NoiseDef("parity/sparse", 0, List.of(0.0, 1.5, 0.0, 0.7)));
    private static final Field BASE = new Field.Noise(NAMESPACE + ":parity/base", 0.25, 0.0, null, null, null);
    private static final Field SPARSE = new Field.Noise(NAMESPACE + ":parity/sparse", 0.5, 0.25, null, null, null);
    private static final Field UNIT = new Field.Clamp(BASE, 0.0, 1.0);
    private static final Field BAND = new Field.Clamp(SPARSE, 0.3, 0.9);

    @Test
    public void boundsMatchMinecraftForEveryEmittedNode() throws Exception {
        List<Field> fields = fields();
        VanillaBounds bounds = VanillaBounds.of(NAMESPACE, NOISES);
        DensityFunctionEmitter emitter = DensityFunctionEmitter.forTarget(ExportCapabilities.forTarget(DataVersion.V26_3), NAMESPACE, bounds);
        FunctionTable table = new FunctionTable(NAMESPACE);
        List<String> references = new ArrayList<>(fields.size());
        for (int index = 0; index < fields.size(); index++) {
            JsonElement emitted = emitter.emit(fields.get(index), table);
            boolean reference = emitted.isJsonPrimitive() && emitted.getAsJsonPrimitive().isString();
            references.add(reference ? emitted.getAsString() : table.register("parity/field_" + index, emitted));
        }
        Map<String, String> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> function : table.files().entrySet()) {
            definitions.put("data/" + NAMESPACE + "/worldgen/density_function/" + function.getKey() + ".json", function.getValue().toString());
        }
        for (NoiseDef noise : NOISES) {
            definitions.put("data/" + NAMESPACE + "/worldgen/noise/" + noise.id() + ".json", noiseJson(noise).toString());
        }
        try (HeadlessNativeRegistries registries = HeadlessNativeRegistries.load(definitions)) {
            for (int index = 0; index < fields.size(); index++) {
                Interval vanilla = registries.registries().lookupOrThrow(Registries.DENSITY_FUNCTION)
                        .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse(references.get(index)))).value().range();
                Optional<ValueRange> modelled = bounds.range(fields.get(index));
                assertTrue("field " + index + " " + fields.get(index) + " has no modelled range, Minecraft " + vanilla, modelled.isPresent());
                assertEquals("field " + index + " " + fields.get(index), new ValueRange(vanilla.min(), vanilla.max()), modelled.get());
            }
        }
    }

    private static List<Field> fields() {
        Field scaled = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.3), BAND);
        Field curve = new Field.Curve(new Field.Binary(Field.BinaryOp.MUL, BASE, new Field.Const(1.7)), List.of(
                new Field.Knot(-1.0, new Field.Const(0.2), 0.75),
                new Field.Knot(0.1, new Field.Curve(SPARSE, List.of(new Field.Knot(-0.5, new Field.Const(-1.0), 0.0),
                        new Field.Knot(0.5, new Field.Const(1.3), 2.0))), 0.0),
                new Field.Knot(0.8, new Field.Const(0.6), -1.5)));
        List<Field> fields = new ArrayList<>();
        fields.add(BASE);
        fields.add(SPARSE);
        fields.add(new Field.Const(0.1));
        fields.add(new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -64.0, 320.0, 1.5, -0.7));
        fields.add(new Field.Clamp(BASE, -0.3, 0.8));
        for (Field.BinaryOp op : Field.BinaryOp.values()) {
            fields.add(new Field.Binary(op, BASE, new Field.Binary(Field.BinaryOp.ADD, BAND, new Field.Const(0.6))));
        }
        fields.add(new Field.Binary(Field.BinaryOp.POW, UNIT, new Field.Const(2.5)));
        fields.add(new Field.Binary(Field.BinaryOp.MAX, scaled, new Field.Const(0.0)));
        fields.add(new Field.Binary(Field.BinaryOp.MIN, new Field.Const(2.0), scaled));
        fields.add(new Field.Binary(Field.BinaryOp.MIN, new Field.Const(0.2), scaled));
        for (Field.UnaryOp op : Field.UnaryOp.values()) {
            fields.add(new Field.Unary(op, new Field.Binary(Field.BinaryOp.SUB, SPARSE, new Field.Const(0.4))));
        }
        fields.add(new Field.Lerp(UNIT, BASE, new Field.Binary(Field.BinaryOp.MUL, SPARSE, new Field.Const(3.0))));
        for (Field.RoundMode mode : Field.RoundMode.values()) {
            fields.add(new Field.Round(mode, new Field.Binary(Field.BinaryOp.MUL, BASE, new Field.Const(10.3)), new Field.Const(2.5)));
        }
        fields.add(curve);
        fields.add(new Field.Curve(UNIT, List.of(new Field.Knot(0.0, new Field.Const(0.0), -3.0), new Field.Knot(1.0, new Field.Const(1.0), -3.0))));
        fields.add(new Field.Partition(BASE, List.of(-0.2, 0.4), List.of(new Field.Const(-3.0), new Field.Const(0.5), new Field.Const(7.25))));
        fields.add(new Field.Mux(new Field.Binary(Field.BinaryOp.MUL, UNIT, new Field.Const(2.0)), List.of(new Field.Const(4.0), BAND, scaled)));
        fields.add(new Field.Gate(BASE, new Field.Binary(Field.BinaryOp.ADD, BAND, new Field.Const(40.0))));
        fields.add(new Field.RangeIf(BASE, -0.1, 0.2, new Field.Const(-2.0), BAND));
        fields.add(new Field.Grid(new Field.Binary(Field.BinaryOp.ADD, BASE, SPARSE), 4, 8));
        fields.add(new Field.Shared("parity/shared", new Field.Binary(Field.BinaryOp.SUB, BAND, BASE)));
        fields.add(new Field.Slice(new Field.Binary(Field.BinaryOp.MUL, SPARSE, new Field.Const(-2.0)), Axis.Y, 64));
        fields.add(new Field.TopSurface(new Field.Binary(Field.BinaryOp.SUB, BASE, new Field.Const(0.1)), 200, -48, 4));
        return fields;
    }

    private static JsonObject noiseJson(NoiseDef noise) {
        JsonArray modifiers = new JsonArray(noise.amplitudes().size());
        for (double amplitude : noise.amplitudes()) {
            modifiers.add(amplitude);
        }
        JsonObject json = new JsonObject();
        json.addProperty("base_octave", noise.firstOctave());
        json.addProperty("octave_count", noise.amplitudes().size());
        json.add("amplitude_modifiers", modifiers);
        json.addProperty("base_amplitude", 1.0);
        json.addProperty("normalize", false);
        return json;
    }
}
