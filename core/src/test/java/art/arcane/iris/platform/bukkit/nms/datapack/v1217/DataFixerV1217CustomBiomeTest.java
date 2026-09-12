package art.arcane.iris.platform.bukkit.nms.datapack.v1217;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomParticle;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataFixerV1217CustomBiomeTest {
    private final DataFixerV1217 fixer = new DataFixerV1217();

    @Test
    public void movesEnvironmentColorsAndParticlesIntoAttributes() {
        IrisBiomeCustom biome = new IrisBiomeCustom();
        biome.setId("spigot_colors");
        biome.setGrassColor("#28a040");
        biome.setFoliageColor("#249030");
        biome.setFogColor("#330808");
        biome.setSkyColor("#102030");
        biome.setWaterFogColor("#405060");
        biome.setAmbientParticle(new IrisBiomeCustomParticle()
                .setParticle("minecraft:ash")
                .setRarity(40));

        JSONObject json = new JSONObject(biome.generateJson(fixer));
        JSONObject effects = json.getJSONObject("effects");
        JSONObject attributes = json.getJSONObject("attributes");

        assertTrue(effects.has("water_color"));
        assertEquals(0x28a040, effects.getInt("grass_color"));
        assertEquals(0x249030, effects.getInt("foliage_color"));
        assertFalse(effects.has("sky_color"));
        assertFalse(effects.has("fog_color"));
        assertFalse(effects.has("water_fog_color"));
        assertFalse(effects.has("particle"));
        assertEquals(0x330808, attributes.getInt("minecraft:visual/fog_color"));
        assertEquals(0x102030, attributes.getInt("minecraft:visual/sky_color"));
        assertEquals(0x405060, attributes.getInt("minecraft:visual/water_fog_color"));
        JSONArray ambientParticles = attributes.getJSONArray("minecraft:visual/ambient_particles");
        JSONObject ambientParticle = ambientParticles.getJSONObject(0);
        assertEquals("minecraft:ash", ambientParticle.getJSONObject("particle").getString("type"));
        assertEquals(0.025D, ambientParticle.getDouble("probability"), 0.000001D);
    }
}
