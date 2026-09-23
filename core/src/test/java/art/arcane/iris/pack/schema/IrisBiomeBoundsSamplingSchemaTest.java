package art.arcane.iris.pack.schema;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IrisBiomeBoundsSamplingSchemaTest {
    @Test
    public void omittedSettingKeepsFourAndExplicitValuesRoundTrip() {
        Gson gson = new Gson();
        assertEquals(4, gson.fromJson("{}", IrisDimension.class).getBiomeBoundsSamplingStep());
        for (int step : new int[]{4, 8, 16, 32}) {
            IrisDimension dimension = new IrisDimension().setBiomeBoundsSamplingStep(step);
            assertEquals(step, gson.fromJson(gson.toJson(dimension), IrisDimension.class).getBiomeBoundsSamplingStep());
        }
        for (int step : new int[]{0, 1, 2, 3, 5, 64}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new IrisDimension().setBiomeBoundsSamplingStep(step).getBiomeBoundsSamplingStep());
        }
    }

    @Test
    public void schemaListsOnlySupportedIntegerSteps() throws Exception {
        SchemaBuilder builder = new SchemaBuilder(IrisDimension.class, null);
        for (String name : new String[]{"biomeBoundsSamplingStep", "terrainSamplingStep", "caveDensitySamplingStep"}) {
            int minimum = name.equals("biomeBoundsSamplingStep") ? 4 : 1;
            Field field = IrisDimension.class.getDeclaredField(name);
            JSONObject property = new SchemaPropertyBuilder(builder).buildProperty(field, IrisDimension.class);
            assertEquals("integer", property.getString("type"));
            assertEquals(minimum, property.getInt("minimum"));
            assertEquals(minimum * 8, property.getInt("maximum"));
            JSONArray options = property.getJSONArray("enum");
            assertEquals(4, options.length());
            for (int index = 0; index < options.length(); index++) {
                assertEquals(minimum << index, options.getInt(index));
            }
        }
    }
}
