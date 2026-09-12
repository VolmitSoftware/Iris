package art.arcane.iris.platform.bukkit.nms.datapack.v1217;

import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer.Dimension;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DataFixerV1217DimensionTypeTest {
    private final DataFixerV1217 fixer = new DataFixerV1217();

    @Test
    public void createsOverworldDimensionWithDragonFightDisabled() {
        JSONObject json = fixer.createDimension(Dimension.OVERWORLD, -256, 768, 512, null);

        assertTrue(json.has("has_ender_dragon_fight"));
        assertEquals(false, json.getBoolean("has_ender_dragon_fight"));
        assertEquals("#0a0a0a", json.getJSONObject("attributes")
                .getString("minecraft:visual/ambient_light_color"));
    }

    @Test
    public void createsEndDimensionWithDragonFightEnabled() {
        JSONObject json = fixer.createDimension(Dimension.END, 0, 256, 256, null);

        assertTrue(json.has("has_ender_dragon_fight"));
        assertEquals(true, json.getBoolean("has_ender_dragon_fight"));
        assertEquals("#3f473f", json.getJSONObject("attributes")
                .getString("minecraft:visual/ambient_light_color"));
    }

    @Test
    public void createsNetherDimensionWithVanillaAmbientColor() {
        JSONObject json = fixer.createDimension(Dimension.NETHER, -256, 768, 512, null);

        assertEquals("#302821", json.getJSONObject("attributes")
                .getString("minecraft:visual/ambient_light_color"));
    }

    @Test
    public void mapsMaximumAmbientLightToWhite() {
        IrisDimensionTypeOptions options = new IrisDimensionTypeOptions().ambientLight(1F);
        JSONObject json = fixer.createDimension(Dimension.NETHER, -256, 768, 512, options);

        assertEquals(1D, json.getDouble("ambient_light"), 0D);
        assertEquals("#ffffff", json.getJSONObject("attributes")
                .getString("minecraft:visual/ambient_light_color"));
    }
}
