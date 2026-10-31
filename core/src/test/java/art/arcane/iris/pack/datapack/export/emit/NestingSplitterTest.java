package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NestingSplitterTest {
    private static final String NAMESPACE = "iris_export";
    private static final int LIMIT = 255;

    @Test
    public void deepChainSplitsUnderLimit() {
        for (DataVersion target : WorldgenExportTargets.SUPPORTED) {
            FunctionTable table = new FunctionTable(NAMESPACE);
            Field chain = new Field.Noise("iris_export:base", 0.25, 0.0, null, null, null);
            for (int index = 0; index < 500; index++) {
                chain = new Field.Binary(Field.BinaryOp.ADD, chain, new Field.Const(index));
            }
            DensityFunctionEmitter emitter = DensityFunctionEmitter.forTarget(ExportCapabilities.forTarget(target), NAMESPACE, VanillaBounds.of(NAMESPACE, List.of()));
            table.register("deep/root", emitter.emit(chain, table));
            assertTrue(NestingSplitter.depth(table.files().get("deep/root")) > LIMIT);
            NestingSplitter.split(table, LIMIT);
            assertTrue(table.files().size() > 1);
            for (Map.Entry<String, JsonElement> file : table.files().entrySet()) {
                assertTrue(file.getKey() + " nests too deep", NestingSplitter.depth(file.getValue()) <= LIMIT);
                assertReferencesResolve(table, file.getValue());
            }
            assertEquals(501, countNodes(table, table.files().get("deep/root")));
        }
    }

    @Test
    public void shallowFilesAreUntouched() {
        FunctionTable table = new FunctionTable(NAMESPACE);
        JsonElement body = JsonParser.parseString("{\"type\":\"minecraft:abs\",\"input\":{\"type\":\"minecraft:abs\",\"input\":1.0}}");
        table.register("shallow", body);
        NestingSplitter.split(table, LIMIT);
        assertEquals(1, table.files().size());
        assertEquals(body, table.files().get("shallow"));
    }

    @Test
    public void depthCountsObjectsAndArrays() {
        assertEquals(0, NestingSplitter.depth(JsonParser.parseString("1.0")));
        assertEquals(1, NestingSplitter.depth(JsonParser.parseString("{\"a\":1}")));
        assertEquals(3, NestingSplitter.depth(JsonParser.parseString("{\"a\":[{\"b\":1}]}")));
    }

    @Test
    public void unsplittableNestingFails() {
        FunctionTable table = new FunctionTable(NAMESPACE);
        JsonElement nested = JsonParser.parseString("1.0");
        for (int index = 0; index < 10; index++) {
            JsonArray array = new JsonArray();
            array.add(nested);
            nested = array;
        }
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:spline");
        root.add("spline", nested);
        table.register("unsplittable", root);
        assertThrows(IllegalStateException.class, () -> NestingSplitter.split(table, 4));
    }

    private static int countNodes(FunctionTable table, JsonElement element) {
        if (element.isJsonPrimitive()) {
            String value = element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
            if (value != null && value.startsWith(NAMESPACE + ":")) {
                return countNodes(table, table.files().get(value.substring(NAMESPACE.length() + 1)));
            }
            return 0;
        }
        int count = 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && "minecraft:add".equals(object.get("type").getAsString())) {
                count++;
            }
            if (object.has("type") && "minecraft:noise".equals(object.get("type").getAsString())) {
                count++;
            }
            for (Map.Entry<String, JsonElement> member : object.entrySet()) {
                if (!member.getKey().equals("type") && !member.getKey().equals("noise")) {
                    count += countNodes(table, member.getValue());
                }
            }
        }
        return count;
    }

    private static void assertReferencesResolve(FunctionTable table, JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString() && element.getAsString().startsWith(NAMESPACE + ":") && !element.getAsString().equals("iris_export:base")) {
                assertTrue(element.getAsString(), table.files().containsKey(element.getAsString().substring(NAMESPACE.length() + 1)));
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                assertReferencesResolve(table, member.getValue());
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                assertReferencesResolve(table, item);
            }
        }
    }
}
