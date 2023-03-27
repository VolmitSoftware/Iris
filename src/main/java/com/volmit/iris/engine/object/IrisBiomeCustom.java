/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.*;
import java.util.Locale;

@Snippet("custom-biome")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A custom biome, generated through a datapack")
@Data
public class IrisBiomeCustom {
    @Required
    @Desc("The resource key of this biome. Just a simple id such as 'plains' or something.")
    private String id = "";

    @MinNumber(-3)
    @MaxNumber(3)
    @Desc("The biome's temperature")
    private double temperature = 0.8;

    @MinNumber(-3)
    @MaxNumber(3)
    @Desc("The biome's downfall amount (snow / rain), see preci")
    private double humidity = 0.4;

    @DependsOn("spawnRarity")
    @ArrayType(min = 1, type = IrisBiomeCustomSpawn.class)
    @Desc("The biome's mob spawns")
    private KList<IrisBiomeCustomSpawn> spawns = new KList<>();

    @Desc("The biome's downfall type")
    private IrisBiomeCustomPrecipType downfallType = IrisBiomeCustomPrecipType.rain;

    @Desc("Define an ambient particle to be rendered clientside (no server cost!)")
    private IrisBiomeCustomParticle ambientParticle = null;

    @Required
    @Desc("The biome's category type")
    private IrisBiomeCustomCategory category = IrisBiomeCustomCategory.plains;

    @Desc("The spawn rarity of any defined spawners")
    private int spawnRarity = -1;

    @Desc("The color of the sky, top half of sky. (hex format)")
    private String skyColor = "#79a8e1";

    @Desc("The color of the fog, bottom half of sky. (hex format)")
    private String fogColor = "#c0d8e1";

    @Desc("The color of the water (hex format). Leave blank / don't define to not change")
    private String waterColor = "#3f76e4";

    @Desc("The color of water fog (hex format). Leave blank / don't define to not change")
    private String waterFogColor = "#050533";

    @Desc("The color of the grass (hex format). Leave blank / don't define to not change")
    private String grassColor = "";

    @Desc("The color of foliage (hex format). Leave blank / don't define to not change")
    private String foliageColor = "";

    public String generateJson() {
        JSONObject effects = new JSONObject();
        effects.put("sky_color", parseColor(getSkyColor()));
        effects.put("fog_color", parseColor(getFogColor()));
        effects.put("water_color", parseColor(getWaterColor()));
        effects.put("water_fog_color", parseColor(getWaterFogColor()));

        if (ambientParticle != null) {
            JSONObject particle = new JSONObject();
            JSONObject po = new JSONObject();
            po.put("type", ambientParticle.getParticle().name().toLowerCase());
            particle.put("options", po);
            particle.put("probability", ambientParticle.getRarity());
            effects.put("particle", particle);
        }

        if (!getGrassColor().isEmpty()) {
            effects.put("grass_color", parseColor(getGrassColor()));
        }

        if (!getFoliageColor().isEmpty()) {
            effects.put("foliage_color", parseColor(getFoliageColor()));
        }

        JSONObject j = new JSONObject();
        j.put("surface_builder", "minecraft:grass");
        j.put("depth", 0.125);
        j.put("scale", 0.05);
        j.put("temperature", getTemperature());
        j.put("downfall", getHumidity());
        j.put("creature_spawn_probability", getSpawnRarity());
        j.put("has_precipitation", getDownfallType() != IrisBiomeCustomPrecipType.none);
        j.put("precipitation", getDownfallType().toString().toLowerCase());
        j.put("category", getCategory().toString().toLowerCase());
        j.put("effects", effects);
        j.put("starts", new JSONArray());
        j.put("spawners", new JSONObject());
        j.put("spawn_costs", new JSONObject());
        j.put("carvers", new JSONObject());
        j.put("features", new JSONArray());

        if (spawnRarity > 0) {
            j.put("creature_spawn_probability", spawnRarity);
        }

        if (getSpawns() != null && getSpawns().isNotEmpty()) {
            JSONObject spawners = new JSONObject();
            KMap<IrisBiomeCustomSpawnType, JSONArray> groups = new KMap<>();

            for (IrisBiomeCustomSpawn i : getSpawns()) {
                JSONArray g = groups.computeIfAbsent(i.getGroup(), (k) -> new JSONArray());
                JSONObject o = new JSONObject();
                o.put("type", "minecraft:" + i.getType().name().toLowerCase());
                o.put("weight", i.getWeight());
                o.put("minCount", i.getMinCount());
                o.put("maxCount", i.getMaxCount());
                g.put(o);
            }

            for (IrisBiomeCustomSpawnType i : groups.k()) {
                spawners.put(i.name().toLowerCase(Locale.ROOT), groups.get(i));
            }

            j.put("spawners", spawners);
        }

        return j.toString(4);
    }

    private int parseColor(String c) {
        String v = (c.startsWith("#") ? c : "#" + c).trim();
        try {
            return Color.decode(v).getRGB();
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Error Parsing '''color''', (" + c + ")");
        }

        return 0;
    }

    public String getId() {
        return id.toLowerCase();
    }
}
