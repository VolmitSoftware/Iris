package com.volmit.iris.core.nms.datapack.v1217;

import com.volmit.iris.core.nms.datapack.v1213.DataFixerV1213;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;

import java.util.Map;

public class DataFixerV1217 extends DataFixerV1213 {
    private static final Map<Dimension, String> DIMENSIONS = Map.of(
            Dimension.OVERWORLD, """
                    {
                      "ambient_light": 0.0,
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
                        "minecraft:visual/cloud_color": "#ccffffff",
                        "minecraft:visual/fog_color": "#c0d8ff",
                        "minecraft:visual/sky_color": "#78a7ff"
                      },
                      "timelines": "#minecraft:in_overworld"
                    }""",
            Dimension.NETHER, """
                    {
                      "ambient_light": 0.1,
                      "attributes": {
                        "minecraft:gameplay/sky_light_level": 4.0,
                        "minecraft:gameplay/snow_golem_melts": true,
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
        json = super.fixCustomBiome(biome, json);
        var effects = json.getJSONObject("effects");
        var attributes = new JSONObject();

        attributes.put("minecraft:visual/fog_color", effects.remove("fog_color"));
        attributes.put("minecraft:visual/sky_color", effects.remove("sky_color"));
        attributes.put("minecraft:visual/water_fog_color", effects.remove("water_fog_color"));

        JSONObject particle = (JSONObject) effects.remove("particle");
        if (particle != null) {
            particle.put("particle", particle.remove("options"));
            attributes.put("minecraft:visual/ambient_particles", new JSONArray()
                    .put(particle));
        }
        json.put("attributes", attributes);

        return json;
    }

    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);

        var attributes = new JSONObject();
        if ((Boolean) json.remove("ultrawarm")) {
            attributes.put("minecraft:gameplay/water_evaporates", true);
            attributes.put("minecraft:gameplay/fast_lava", true);
            attributes.put("minecraft:gameplay/snow_golem_melts", true);
            attributes.put("minecraft:visual/default_dripstone_particle", new JSONObject()
                    .put("value", "minecraft:dripstone_drip_water_lava"));
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
        attributes.put("minecraft:gameplay/piglins_zombify", json.remove("piglin_safe"));
        attributes.put("minecraft:gameplay/can_start_raid", json.remove("has_raids"));

        var cloud_height = json.remove("cloud_height");
        if (cloud_height != null) attributes.put("minecraft:visual/cloud_height", cloud_height);

        boolean natural = (Boolean) json.remove("natural");
        attributes.put("minecraft:gameplay/nether_portal_spawns_piglin", natural);
        if (natural != (dimension == Dimension.OVERWORLD)) {
            attributes.put("minecraft:gameplay/eyeblossom_open", natural);
            attributes.put("minecraft:gameplay/creaking_active", natural);
        }

        //json.put("has_fixed_time", json.remove("fixed_time") != null); //TODO investigate
        json.put("attributes", attributes);

        json.remove("effects");
        var defaults = new JSONObject(DIMENSIONS.get(dimension));
        merge(json, defaults);
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
