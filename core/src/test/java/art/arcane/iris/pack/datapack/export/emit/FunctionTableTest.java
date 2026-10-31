package art.arcane.iris.pack.datapack.export.emit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FunctionTableTest {
    @Test
    public void registerReturnsNamespacedReference() {
        FunctionTable table = new FunctionTable("iris_export");
        assertEquals("iris_export:terrain/height", table.register("terrain/height", json("{\"type\":\"minecraft:abs\",\"input\":1.0}")));
        assertEquals(json("{\"type\":\"minecraft:abs\",\"input\":1.0}"), table.files().get("terrain/height"));
        assertEquals("iris_export", table.namespace());
    }

    @Test
    public void identicalBodiesShareOneFile() {
        FunctionTable table = new FunctionTable("iris_export");
        String first = table.register("first", json("{\"type\":\"minecraft:add\",\"left\":1.0,\"right\":{\"type\":\"minecraft:abs\",\"input\":2.0}}"));
        String second = table.register("second", json("{\"right\":{\"input\":2.0,\"type\":\"minecraft:abs\"},\"left\":1.0,\"type\":\"minecraft:add\"}"));
        assertEquals("iris_export:first", first);
        assertEquals(first, second);
        assertEquals(1, table.files().size());
    }

    @Test
    public void reRegisteringSameIdAndBodyIsIdempotent() {
        FunctionTable table = new FunctionTable("iris_export");
        table.register("height", json("{\"type\":\"minecraft:abs\",\"input\":1.0}"));
        assertEquals("iris_export:height", table.register("height", json("{\"type\":\"minecraft:abs\",\"input\":1.0}")));
        assertEquals(1, table.files().size());
    }

    @Test
    public void conflictingBodyForIdIsRejected() {
        FunctionTable table = new FunctionTable("iris_export");
        table.register("height", json("{\"type\":\"minecraft:abs\",\"input\":1.0}"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> table.register("height", json("{\"type\":\"minecraft:abs\",\"input\":2.0}")));
        assertEquals("Density function 'iris_export:height' is already registered with a different body", error.getMessage());
    }

    @Test
    public void filesAreSortedById() {
        FunctionTable table = new FunctionTable("iris_export");
        table.register("zeta", json("1.0"));
        table.register("alpha", json("2.0"));
        table.register("mid/beta", json("3.0"));
        assertEquals(List.of("alpha", "mid/beta", "zeta"), new ArrayList<>(table.files().keySet()));
    }

    @Test
    public void registeredBodyIsCopied() {
        FunctionTable table = new FunctionTable("iris_export");
        JsonObject body = json("{\"type\":\"minecraft:abs\",\"input\":1.0}").getAsJsonObject();
        table.register("height", body);
        body.addProperty("input", 5.0);
        assertEquals(json("{\"type\":\"minecraft:abs\",\"input\":1.0}"), table.files().get("height"));
        assertEquals("iris_export:height", table.register("other", json("{\"type\":\"minecraft:abs\",\"input\":1.0}")));
    }

    @Test
    public void filesViewIsReadOnly() {
        FunctionTable table = new FunctionTable("iris_export");
        assertThrows(UnsupportedOperationException.class, () -> table.files().put("height", json("1.0")));
    }

    @Test
    public void invalidIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FunctionTable("Iris"));
        FunctionTable table = new FunctionTable("iris_export");
        assertThrows(IllegalArgumentException.class, () -> table.register("Height", json("1.0")));
        assertThrows(IllegalArgumentException.class, () -> table.register("iris:height", json("1.0")));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }
}
