package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** A copy of a placement made with its builder keeps every setting it does not change, and the JSON it was read from. */
public class ObjectPlacementCopyTest {
    private static final Gson GSON = new Gson();
    private static final String EVERY_SETTING = """
            {"place": ["objects/original"], "rotation": {"enabled": true}, "clamp": {"maximumHeight": 90}, "snow": 0.4,
             "dolphinTarget": true, "slopeCondition": {"minimumSlope": 1}, "rotateTowardsSlope": true, "chance": 0.25,
             "density": 7, "densityStyle": {"min": 7, "max": 7}, "stiltSettings": {"yMax": 4}, "boreExtendMaxY": 8,
             "markers": [{"marker": "camp"}], "boreExtendMinY": 3, "underwater": true, "carvingSupport": "ANYWHERE",
             "surfaceSupportBuffer": 3, "surfaceSupportDepth": 4, "requireSurfaceSupport": false, "caveAnchorMode": "CEILING",
             "smartBore": true, "waterloggable": true, "onwater": true, "meld": true, "fromBottom": true, "bottom": true,
             "bore": true, "mode": "FAST_MIN_STILT", "edit": [{}], "translate": {"y": 2}, "scale": {"size": 2},
             "loot": [{"name": "chest"}], "vanillaLoot": [{"name": "chests/simple_dungeon"}], "overrideGlobalLoot": true,
             "trees": [{}], "allowedCollisions": ["objects/allowed"], "forbiddenCollisions": ["objects/forbidden"], "force": true}
            """;

    @Test
    public void aCopyKeepsEverySettingItDoesNotChange() {
        ObjectPlacement source = TestSettings.of(ObjectPlacement.class, EVERY_SETTING);

        ObjectPlacement copy = source.toBuilder().place(List.of("objects/replaced")).build();

        assertEquals(List.of("objects/replaced"), copy.place());
        JsonObject expected = GSON.toJsonTree(source).getAsJsonObject();
        JsonObject copied = GSON.toJsonTree(copy).getAsJsonObject();
        expected.remove("place");
        copied.remove("place");
        assertEquals(expected, copied);
        assertSame(source.json(), copy.json());
    }
}
