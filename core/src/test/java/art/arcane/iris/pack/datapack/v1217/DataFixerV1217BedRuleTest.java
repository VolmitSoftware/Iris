package art.arcane.iris.pack.datapack.v1217;

import art.arcane.iris.pack.datapack.IDataFixer.Dimension;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataFixerV1217BedRuleTest {
    private final DataFixerV1217 fixer = new DataFixerV1217();

    @Test
    public void overworldBedsSleepWhenDark() {
        JSONObject rule = bedRule(Dimension.OVERWORLD);

        assertEquals("when_dark", rule.getString("can_sleep"));
        assertEquals("always", rule.getString("can_set_spawn"));
        assertTrue(rule.has("error_message"));
    }

    @Test
    public void netherBedsAreDestroyedOnUse() {
        assertDestroyedOnUse(bedRule(Dimension.NETHER));
    }

    @Test
    public void endBedsAreDestroyedOnUse() {
        assertDestroyedOnUse(bedRule(Dimension.END));
    }

    private JSONObject bedRule(Dimension dimension) {
        return fixer.createDimension(dimension, -64, 384, 384, null)
                .getJSONObject("attributes")
                .getJSONObject("minecraft:gameplay/bed_rule");
    }

    private static void assertDestroyedOnUse(JSONObject rule) {
        assertEquals("never", rule.getString("can_sleep"));
        assertEquals("never", rule.getString("can_set_spawn"));
        assertTrue(rule.getBoolean("destroy_on_use"));
        assertFalse(rule.has("explodes"));
    }
}
