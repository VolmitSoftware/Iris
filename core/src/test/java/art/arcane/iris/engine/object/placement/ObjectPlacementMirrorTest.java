package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** The settings of {@code iris:objects} describe the same JSON an Iris object placement reads, and keep it for Iris to read. */
public class ObjectPlacementMirrorTest {
    private static final Gson GSON = new Gson();

    @Test
    public void theSettingsDeclareEveryOptionOfIrisObjectPlacementAndNoOther() {
        JsonObject host = GSON.toJsonTree(new IrisObjectPlacement()).getAsJsonObject();
        JsonObject settings = GSON.toJsonTree(TestSettings.of(ObjectPlacement.class, "{}")).getAsJsonObject();

        assertEquals(new TreeSet<>(host.keySet()), new TreeSet<>(settings.keySet()));
    }

    @Test
    public void theSettingsDefaultToWhatIrisObjectPlacementDefaultsTo() {
        JsonObject host = GSON.toJsonTree(new IrisObjectPlacement()).getAsJsonObject();
        JsonObject settings = GSON.toJsonTree(TestSettings.of(ObjectPlacement.class, "{}")).getAsJsonObject();

        for (String option : host.keySet()) {
            assertEquals("default of " + option, host.get(option), settings.get(option));
        }
    }

    @Test
    public void aPlacementKeepsTheJsonItWasReadFrom() {
        String json = "{\"place\":[\"trees/oak\"],\"chance\":0.5,\"snow\":0.25}";

        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, json);

        assertEquals(GSON.fromJson(json, JsonObject.class), GSON.fromJson(placement.json(), JsonObject.class));
        assertEquals(0.5, placement.chance(), 0);
        assertNull(placement.hostForm());
    }

    @Test
    public void irisReadsTheKeptJsonAsItsOwnPlacement() {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class,
                "{\"place\":[\"trees/oak\"],\"force\":true,\"density\":3,\"mode\":\"STILT\"}");

        IrisObjectPlacement host = GSON.fromJson(placement.json(), IrisObjectPlacement.class);

        assertNotNull(host);
        assertEquals("trees/oak", host.getPlace().get(0));
        assertEquals(true, host.isForcePlace());
        assertEquals(3, host.getDensity());
        assertEquals(placement.mode(), host.getMode());
    }
}
