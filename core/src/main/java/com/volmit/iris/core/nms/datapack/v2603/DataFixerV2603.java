package com.volmit.iris.core.nms.datapack.v2603;

import com.volmit.iris.core.nms.datapack.v2601.DataFixerV2601;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;

public class DataFixerV2603 extends DataFixerV2601 {
    private static final String BED_RULE = "minecraft:gameplay/bed_rule";
    private static final String STRAW_BED_RULE = "minecraft:gameplay/straw_bed_rule";
    private static final String NATURAL_MOB_SPAWNS = "minecraft:gameplay/natural_mob_spawns";
    private static final String CREATURE_SPAWN_PROBABILITY = "minecraft:gameplay/creature_world_gen_spawn_probability";

    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        json = super.fixCustomBiome(biome, json);
        JSONObject attributes = json.getJSONObject("attributes");

        Object spawnProbability = json.remove("creature_spawn_probability");
        if (spawnProbability != null) {
            attributes.put(CREATURE_SPAWN_PROBABILITY, spawnProbability);
        }

        json.remove("spawn_costs");
        JSONObject spawners = (JSONObject) json.remove("spawners");
        if (spawners != null && spawners.length() > 0) {
            attributes.put(NATURAL_MOB_SPAWNS, new JSONObject()
                    .put("argument", new JSONObject()
                            .put("spawn_costs", new JSONObject())
                            .put("spawns_by_category", spawnsByCategory(spawners)))
                    .put("modifier", "overlay"));
        }
        return json;
    }

    private JSONObject spawnsByCategory(JSONObject spawners) {
        JSONObject categories = new JSONObject();
        for (String category : spawners.keySet()) {
            JSONArray spawns = new JSONArray();
            for (Object spawner : spawners.getJSONArray(category)) {
                spawns.put(spawnEntry((JSONObject) spawner));
            }
            categories.put(category, spawns);
        }
        return categories;
    }

    private JSONObject spawnEntry(JSONObject spawner) {
        int minCount = spawner.getInt("minCount");
        int maxCount = spawner.getInt("maxCount");
        Object count = minCount == maxCount ? minCount : new JSONObject()
                .put("type", "minecraft:uniform")
                .put("min_inclusive", minCount)
                .put("max_inclusive", maxCount);
        return new JSONObject()
                .put("type", spawner.get("type"))
                .put("count", count)
                .put("weight", spawner.get("weight"));
    }

    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);

        JSONObject attributes = json.getJSONObject("attributes");
        JSONObject bedRule = attributes.getJSONObject(BED_RULE);
        if (bedRule.remove("explodes") == null) {
            return;
        }
        bedRule.put("destroy_on_use", true);
        attributes.put(STRAW_BED_RULE, new JSONObject(bedRule.toString()));
    }
}
