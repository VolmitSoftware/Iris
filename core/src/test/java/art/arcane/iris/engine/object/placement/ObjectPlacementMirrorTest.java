package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A placement read from pack JSON keeps that JSON, which Iris reads its module settings from. */
public class ObjectPlacementMirrorTest {
    private static final Gson GSON = new Gson();

    @Test
    public void aPlacementKeepsTheJsonItWasReadFrom() {
        String json = "{\"place\":[\"trees/oak\"],\"chance\":0.5,\"snow\":0.25}";
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, json);

        assertEquals(GSON.fromJson(json, JsonObject.class), GSON.fromJson(placement.json(), JsonObject.class));
        assertEquals(0.5, placement.chance(), 0);
        assertNull(placement.hostForm());
    }

    @Test
    public void aPlacementMadeInCodeHasNoJson() {
        assertNull(ObjectPlacement.builder().build().json());
    }
}
