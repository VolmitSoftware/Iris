package com.volmit.iris.core.nms.datapack.v1192;

import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.util.json.JSONObject;
import java.util.Map;

public class DataFixerV1192 implements IDataFixer {

    private static final Map<Dimension, String> DIMENSIONS = Map.of(
            Dimension.OVERWORLD, """
            {
              "ambient_light": 0.0,
              "bed_works": true,
              "coordinate_scale": 1.0,
              "effects": "minecraft:overworld",
              "has_ceiling": false,
              "has_raids": true,
              "has_skylight": true,
              "infiniburn": "#minecraft:infiniburn_overworld",
              "monster_spawn_block_light_limit": 0,
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              },
              "natural": true,
              "piglin_safe": false,
              "respawn_anchor_works": false,
              "ultrawarm": false
            }""",
            Dimension.NETHER, """
            {
              "ambient_light": 0.1,
              "bed_works": false,
              "coordinate_scale": 8.0,
              "effects": "minecraft:the_nether",
              "fixed_time": 18000,
              "has_ceiling": true,
              "has_raids": false,
              "has_skylight": false,
              "infiniburn": "#minecraft:infiniburn_nether",
              "monster_spawn_block_light_limit": 15,
              "monster_spawn_light_level": 7,
              "natural": false,
              "piglin_safe": true,
              "respawn_anchor_works": true,
              "ultrawarm": true
            }""",
            Dimension.END, """
            {
              "ambient_light": 0.0,
              "bed_works": false,
              "coordinate_scale": 1.0,
              "effects": "minecraft:the_end",
              "fixed_time": 6000,
              "has_ceiling": false,
              "has_raids": true,
              "has_skylight": false,
              "infiniburn": "#minecraft:infiniburn_end",
              "monster_spawn_block_light_limit": 0,
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              },
              "natural": false,
              "piglin_safe": false,
              "respawn_anchor_works": false,
              "ultrawarm": false
            }"""
    );

    @Override
    public JSONObject rawDimension(Dimension dimension) {
        return new JSONObject(DIMENSIONS.get(dimension));
    }
}
