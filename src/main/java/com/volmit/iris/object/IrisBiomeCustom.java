package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.*;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A custom biome, generated through a datapack")
@Data
public class IrisBiomeCustom {
    @DontObfuscate
    @Required
    @Desc("The resource key of this biome. Just a simple id such as 'plains' or something.")
    private String id = "";

    @MinNumber(-3)
    @MaxNumber(3)
    @DontObfuscate
    @Desc("The biome's temperature")
    private double temperature = 0.8;

    @MinNumber(-3)
    @MaxNumber(3)
    @DontObfuscate
    @Desc("The biome's downfall amount (snow / rain), see preci")
    private double humidity = 0.4;

    @DontObfuscate
    @Desc("The biome's downfall type")
    private IrisBiomeCustomPrecipType downfallType = IrisBiomeCustomPrecipType.rain;

    @DontObfuscate
    @Desc("The biome's category type")
    private IrisBiomeCustomCategory category = IrisBiomeCustomCategory.plains;

    @DontObfuscate
    @Desc("The color of the sky, top half of sky. (hex format)")
    private String skyColor = "#79a8e1";

    @DontObfuscate
    @Desc("The color of the fog, bottom half of sky. (hex format)")
    private String fogColor = "#c0d8e1";

    @DontObfuscate
    @Desc("The color of the water (hex format). Leave blank / don't define to not change")
    private String waterColor = "#3f76e4";

    @DontObfuscate
    @Desc("The color of water fog (hex format). Leave blank / don't define to not change")
    private String waterFogColor = "#050533";

    @DontObfuscate
    @Desc("The color of the grass (hex format). Leave blank / don't define to not change")
    private String grassColor = "";

    @DontObfuscate
    @Desc("The color of foliage (hex format). Leave blank / don't define to not change")
    private String foliageColor = "";

    public String generateJson()
    {
        JSONObject effects = new JSONObject();
        effects.put("sky_color", parseColor(getSkyColor()));
        effects.put("fog_color", parseColor(getFogColor()));
        effects.put("water_color", parseColor(getWaterColor()));
        effects.put("water_fog_color", parseColor(getWaterFogColor()));

        if(getGrassColor() != null)
        {
            effects.put("grass_color", parseColor(getGrassColor()));
        }

        if(getFoliageColor() != null)
        {
            effects.put("foliage_color", parseColor(getFoliageColor()));
        }

        JSONObject j = new JSONObject();
        j.put("surface_builder", "minecraft:grass");
        j.put("depth", 0.125);
        j.put("scale", 0.05);
        j.put("temperature", getTemperature());
        j.put("downfall", getHumidity());
        j.put("precipitation", getDownfallType().toString().toLowerCase());
        j.put("category", getCategory().toString().toLowerCase());
        j.put("effects", effects);
        j.put("starts", new JSONArray());
        j.put("spawners", new JSONObject());
        j.put("spawn_costs", new JSONObject());
        j.put("carvers", new JSONObject());
        j.put("features", new JSONArray());

        return j.toString(4);
    }

    private int parseColor(String c) {
        String v = (c.startsWith("#") ? c : "#" + c).trim();
        try {
            return Color.decode(v).getRGB();
        } catch (Throwable e) {
            Iris.error("Error Parsing '''color''', (" + c+ ")");
        }

        return 0;
    }
}
