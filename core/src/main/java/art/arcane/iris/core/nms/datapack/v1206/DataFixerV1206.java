package art.arcane.iris.core.nms.datapack.v1206;

import art.arcane.iris.Iris;
import art.arcane.iris.core.nms.datapack.v1192.DataFixerV1192;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisBiomeCustomSpawn;
import art.arcane.iris.engine.object.IrisBiomeCustomSpawnType;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

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
                if (i == null) {
                    continue;
                }
                EntityType type = i.getType();
                if (type == null) {
                    Iris.warn("Skipping custom biome spawn with null entity type in biome " + biome.getId());
                    continue;
                }
                IrisBiomeCustomSpawnType group = i.getGroup() == null ? IrisBiomeCustomSpawnType.MISC : i.getGroup();
                JSONArray g = groups.computeIfAbsent(group, (k) -> new JSONArray());
                JSONObject o = new JSONObject();
                NamespacedKey key = type.getKey();
                if (key == null) {
                    Iris.warn("Skipping custom biome spawn with unresolved entity key in biome " + biome.getId());
                    continue;
                }
                o.put("type", key.toString());
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
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);
        if (!(json.get("monster_spawn_light_level") instanceof JSONObject lightLevel))
            return;
        var value = (JSONObject) lightLevel.remove("value");
        lightLevel.put("max_inclusive", value.get("max_inclusive"));
        lightLevel.put("min_inclusive", value.get("min_inclusive"));
    }
}
