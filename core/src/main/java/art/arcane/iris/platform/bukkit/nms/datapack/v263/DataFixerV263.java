package art.arcane.iris.platform.bukkit.nms.datapack.v263;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.platform.bukkit.nms.datapack.v1217.DataFixerV1217;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.util.Locale;

public final class DataFixerV263 extends DataFixerV1217 {
    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        JSONObject fixed = super.fixCustomBiome(biome, json);
        JSONObject attributes = fixed.getJSONObject("attributes");
        JSONObject effects = fixed.getJSONObject("effects");
        for (String key : new String[]{"water_color", "foliage_color", "dry_foliage_color", "grass_color"}) {
            color(effects, key);
        }
        for (String key : new String[]{"minecraft:visual/sky_color", "minecraft:visual/fog_color", "minecraft:visual/water_fog_color"}) {
            color(attributes, key);
        }
        JSONObject spawners = fixed.getJSONObject("spawners");
        for (String category : spawners.keySet()) {
            JSONArray entries = spawners.getJSONArray(category);
            for (int index = 0; index < entries.length(); index++) {
                JSONObject spawn = entries.getJSONObject(index);
                int minimum = spawn.getInt("minCount");
                int maximum = spawn.getInt("maxCount");
                spawn.remove("minCount");
                spawn.remove("maxCount");
                spawn.put("count", minimum == maximum ? minimum : new JSONObject()
                        .put("type", "minecraft:uniform")
                        .put("min_inclusive", minimum)
                        .put("max_inclusive", maximum));
            }
        }
        attributes.put("minecraft:gameplay/natural_mob_spawns", new JSONObject()
                .put("spawns_by_category", spawners)
                .put("spawn_costs", fixed.getJSONObject("spawn_costs")));
        Object probability = fixed.remove("creature_spawn_probability");
        if (probability != null) {
            attributes.put("minecraft:gameplay/creature_world_gen_spawn_probability", probability);
        }
        fixed.remove("spawners");
        fixed.remove("spawn_costs");
        return fixed;
    }

    private static void color(JSONObject object, String key) {
        if (object.opt(key) instanceof Number value) {
            object.put(key, String.format(Locale.ROOT, "#%06x", value.intValue() & 0xFFFFFF));
        }
    }
}
