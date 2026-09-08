package art.arcane.iris.modded;

import art.arcane.iris.core.project.SchemaBuilder;
import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SchemaBuilderPlatformIsolationTest {
    @Test
    public void enumSchemaDoesNotResolveBukkitOnlyMethodSignatures() {
        JSONObject schema = new SchemaBuilder(DirectionModel.class, null).construct();
        JSONObject direction = schema.getJSONObject("properties").getJSONObject("direction");
        String definitionKey = direction.getString("$ref").substring("#/definitions/".length());
        JSONArray values = schema.getJSONObject("definitions").getJSONObject(definitionKey).getJSONArray("oneOf");
        List<String> names = new ArrayList<>(values.length());
        for (int index = 0; index < values.length(); index++) {
            names.add(values.getJSONObject(index).getString("const"));
        }

        assertEquals(List.of(
                "UP_POSITIVE_Y",
                "DOWN_NEGATIVE_Y",
                "NORTH_NEGATIVE_Z",
                "SOUTH_POSITIVE_Z",
                "EAST_POSITIVE_X",
                "WEST_NEGATIVE_X"), names);
        assertEquals("#/definitions/" + definitionKey,
                schema.getJSONObject("properties").getJSONObject("directions")
                        .getJSONObject("items").getString("$ref"));
    }

    @Desc("Direction model.")
    public static class DirectionModel {
        @Desc("Direction.")
        private IrisDirection direction = IrisDirection.NORTH_NEGATIVE_Z;

        @Desc("Directions.")
        @ArrayType(type = IrisDirection.class)
        private KList<IrisDirection> directions = new KList<>();
    }
}
