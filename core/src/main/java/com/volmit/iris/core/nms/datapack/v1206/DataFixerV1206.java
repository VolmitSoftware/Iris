package com.volmit.iris.core.nms.datapack.v1206;

import com.volmit.iris.core.nms.datapack.v1192.DataFixerV1192;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.engine.object.IrisBiomeCustomSpawn;
import com.volmit.iris.engine.object.IrisBiomeCustomSpawnType;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;

import java.util.Locale;

public class DataFixerV1206 extends DataFixerV1192 {
    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        int spawnRarity = biome.getSpawnRarity();
        if (spawnRarity > 0) {
            json.put("creature_spawn_probability", Math.min(spawnRarity/20d, 0.9999999));
        } else {
            json.remove("creature_spawn_probability");
        }

        var spawns = biome.getSpawns();
        if (spawns != null && spawns.isNotEmpty()) {
            JSONObject spawners = new JSONObject();
            KMap<IrisBiomeCustomSpawnType, JSONArray> groups = new KMap<>();

            for (IrisBiomeCustomSpawn i : spawns) {
                JSONArray g = groups.computeIfAbsent(i.getGroup(), (k) -> new JSONArray());
                JSONObject o = new JSONObject();
                o.put("type", i.getType().getKey());
                o.put("weight", i.getWeight());
                o.put("minCount", i.getMinCount());
                o.put("maxCount", i.getMaxCount());
                g.put(o);
            }

            for (IrisBiomeCustomSpawnType i : groups.k()) {
                spawners.put(i.name().toLowerCase(Locale.ROOT), groups.get(i));
            }

            json.put("spawners", spawners);
        }
        return json;
    }

    @Override
    public JSONObject rawDimension(Dimension dimension) {
        JSONObject json = super.rawDimension(dimension);
        if (!(json.get("monster_spawn_light_level") instanceof JSONObject lightLevel))
            return json;
        var value = (JSONObject) lightLevel.remove("value");
        lightLevel.put("max_inclusive", value.get("max_inclusive"));
        lightLevel.put("min_inclusive", value.get("min_inclusive"));
        return json;
    }
}
