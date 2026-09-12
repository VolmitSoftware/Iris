package art.arcane.iris.platform.bukkit.nms.datapack.v1217;

import art.arcane.iris.platform.bukkit.nms.datapack.v1213.DataFixerV1213;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.util.Map;

public class DataFixerV1217 extends DataFixerV1213 {
    private static final Map<Dimension, String> DIMENSIONS = Map.of(
            Dimension.OVERWORLD, """
                    {
                      "ambient_light": 0.0,
                      "default_clock": "minecraft:overworld",
                      "has_ender_dragon_fight": false,
                      "attributes": {
                        "minecraft:audio/ambient_sounds": {
                          "mood": {
                            "block_search_extent": 8,
                            "offset": 2.0,
                            "sound": "minecraft:ambient.cave",
                            "tick_delay": 6000
                          }
                        },
                        "minecraft:audio/background_music": {
                          "creative": {
                            "max_delay": 24000,
                            "min_delay": 12000,
                            "sound": "minecraft:music.creative"
                          },
                          "default": {
                            "max_delay": 24000,
                            "min_delay": 12000,
                            "sound": "minecraft:music.game"
                          }
                        },
                        "minecraft:visual/ambient_light_color": "#0a0a0a",
                        "minecraft:visual/cloud_color": "#ccffffff",
                        "minecraft:visual/fog_color": "#c0d8ff",
                        "minecraft:visual/sky_color": "#78a7ff"
                      },
                      "timelines": "#minecraft:in_overworld"
                    }""",
            Dimension.NETHER, """
                    {
                      "ambient_light": 0.1,
                      "has_fixed_time": true,
                      "has_ender_dragon_fight": false,
                      "attributes": {
                        "minecraft:gameplay/sky_light_level": 4.0,
                        "minecraft:gameplay/snow_golem_melts": true,
                        "minecraft:visual/ambient_light_color": "#302821",
                        "minecraft:visual/fog_end_distance": 96.0,
                        "minecraft:visual/fog_start_distance": 10.0,
                        "minecraft:visual/sky_light_color": "#7a7aff",
                        "minecraft:visual/sky_light_factor": 0.0
                      },
                      "cardinal_light": "nether",
                      "skybox": "none",
                      "timelines": "#minecraft:in_nether"
                    }""",
            Dimension.END, """
                    {
                      "ambient_light": 0.25,
                      "default_clock": "minecraft:the_end",
                      "has_fixed_time": true,
                      "has_ender_dragon_fight": true,
                      "attributes": {
                        "minecraft:audio/ambient_sounds": {
                          "mood": {
                            "block_search_extent": 8,
                            "offset": 2.0,
                            "sound": "minecraft:ambient.cave",
                            "tick_delay": 6000
                          }
                        },
                        "minecraft:audio/background_music": {
                          "default": {
                            "max_delay": 24000,
                            "min_delay": 6000,
                            "replace_current_music": true,
                            "sound": "minecraft:music.end"
                          }
                        },
                        "minecraft:visual/ambient_light_color": "#3f473f",
                        "minecraft:visual/fog_color": "#181318",
                        "minecraft:visual/sky_color": "#000000",
                        "minecraft:visual/sky_light_color": "#e580ff",
                        "minecraft:visual/sky_light_factor": 0.0
                      },
                      "skybox": "end",
                      "timelines": "#minecraft:in_end"
                    }"""
    );

    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        JSONObject fixed = super.fixCustomBiome(biome, json);
        JSONObject effects = fixed.getJSONObject("effects");
        JSONObject attributes = fixed.optJSONObject("attributes");
        if (attributes == null) {
            attributes = new JSONObject();
            fixed.put("attributes", attributes);
        }

        moveAttribute(effects, attributes, "sky_color", "minecraft:visual/sky_color");
        moveAttribute(effects, attributes, "fog_color", "minecraft:visual/fog_color");
        moveAttribute(effects, attributes, "water_fog_color", "minecraft:visual/water_fog_color");

        JSONObject particle = effects.optJSONObject("particle");
        if (particle != null) {
            JSONObject ambientParticle = new JSONObject();
            ambientParticle.put("particle", particle.remove("options"));
            ambientParticle.put("probability", particle.remove("probability"));
            attributes.put("minecraft:visual/ambient_particles", new JSONArray().put(ambientParticle));
            effects.remove("particle");
        }

        return fixed;
    }

    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);

        JSONObject attributes = new JSONObject();
        if ((Boolean) json.remove("ultrawarm")) {
            attributes.put("minecraft:gameplay/water_evaporates", true);
            attributes.put("minecraft:gameplay/fast_lava", true);
            attributes.put("minecraft:gameplay/snow_golem_melts", true);
            attributes.put("minecraft:visual/default_dripstone_particle", new JSONObject()
                    .put("type", "minecraft:dripping_dripstone_lava"));
        }

        if ((Boolean) json.remove("bed_works")) {
            attributes.put("minecraft:gameplay/bed_rule", new JSONObject()
                    .put("can_set_spawn", "always")
                    .put("can_sleep", "when_dark")
                    .put("error_message", new JSONObject()
                            .put("translate", "block.minecraft.bed.no_sleep")));
        } else {
            attributes.put("minecraft:gameplay/bed_rule", new JSONObject()
                    .put("can_set_spawn", "never")
                    .put("can_sleep", "never")
                    .put("explodes", true));
        }

        attributes.put("minecraft:gameplay/respawn_anchor_works", json.remove("respawn_anchor_works"));
        attributes.put("minecraft:gameplay/piglins_zombify", !(Boolean) json.remove("piglin_safe"));
        attributes.put("minecraft:gameplay/can_start_raid", json.remove("has_raids"));

        Object cloudHeight = json.remove("cloud_height");
        if (cloudHeight != null) {
            attributes.put("minecraft:visual/cloud_height", cloudHeight);
        }

        boolean natural = (Boolean) json.remove("natural");
        attributes.put("minecraft:gameplay/nether_portal_spawns_piglin", natural);
        if (natural != (dimension == Dimension.OVERWORLD)) {
            attributes.put("minecraft:gameplay/eyeblossom_open", natural);
            attributes.put("minecraft:gameplay/creaking_active", natural);
        }

        //json.put("has_fixed_time", json.remove("fixed_time") != null); //TODO investigate
        json.put("attributes", attributes);

        json.remove("effects");
        JSONObject defaults = new JSONObject(DIMENSIONS.get(dimension));
        merge(json, defaults);

        Object ambientLight = json.opt("ambient_light");
        if (ambientLight instanceof Number number && number.doubleValue() >= 1D) {
            json.getJSONObject("attributes").put("minecraft:visual/ambient_light_color", "#ffffff");
        }
    }

    private void moveAttribute(JSONObject source, JSONObject target, String sourceKey, String targetKey) {
        Object value = source.remove(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private void merge(JSONObject base, JSONObject override) {
        for (String key : override.keySet()) {
            switch (base.opt(key)) {
                case null -> base.put(key, override.opt(key));
                case JSONObject base1 when override.opt(key) instanceof JSONObject override1 -> merge(base1, override1);
                case JSONArray base1 when override.opt(key) instanceof JSONArray override1 -> {
                    for (Object o : override1) {
                        base1.put(o);
                    }
                }
                default -> {}
            }
        }
    }
}
