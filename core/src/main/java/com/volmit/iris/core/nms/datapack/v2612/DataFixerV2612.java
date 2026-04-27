package com.volmit.iris.core.nms.datapack.v2612;

import com.volmit.iris.core.nms.datapack.v1217.DataFixerV1217;
import com.volmit.iris.util.json.JSONObject;

public class DataFixerV2612 extends DataFixerV1217 {
    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);

        var attributes = json.getJSONObject("attributes");
        switch (dimension) {
            case OVERWORLD -> {
                json.put("has_ender_dragon_fight", false);
                attributes.put("minecraft:visual/ambient_light_color", "#0a0a0a");
                json.put("default_clock", "minecraft:overworld");
            }
            case NETHER -> {
                json.put("has_ender_dragon_fight", false);
                attributes.put("minecraft:visual/ambient_light_color", "#302821");
            }
            case END -> {
                json.put("has_ender_dragon_fight", true);
                attributes.put("minecraft:visual/ambient_light_color", "#3f473f");
                attributes.put("minecraft:visual/sky_light_color", "#ac60cd");
                json.put("default_clock", "minecraft:the_end");
            }
        }
    }
}
