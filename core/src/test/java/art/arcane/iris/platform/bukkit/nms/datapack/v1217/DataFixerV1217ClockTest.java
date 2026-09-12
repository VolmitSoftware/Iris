package art.arcane.iris.platform.bukkit.nms.datapack.v1217;

import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer.Dimension;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataFixerV1217ClockTest {
    @Test
    public void overworldProvidesTheVanillaDefaultClock() {
        JSONObject json = fixed(Dimension.OVERWORLD);

        assertEquals("minecraft:overworld", json.getString("default_clock"));
        assertFalse(json.optBoolean("has_fixed_time", false));
    }

    @Test
    public void endProvidesItsFixedVanillaClock() {
        JSONObject json = fixed(Dimension.END);

        assertEquals("minecraft:the_end", json.getString("default_clock"));
        assertTrue(json.getBoolean("has_fixed_time"));
    }

    @Test
    public void netherRemainsFixedWithoutADefaultClock() {
        JSONObject json = fixed(Dimension.NETHER);

        assertFalse(json.has("default_clock"));
        assertTrue(json.getBoolean("has_fixed_time"));
    }

    private JSONObject fixed(Dimension dimension) {
        DataFixerV1217 fixer = new DataFixerV1217();
        JSONObject json = fixer.resolve(dimension, null);
        fixer.fixDimension(dimension, json);
        return json;
    }
}
