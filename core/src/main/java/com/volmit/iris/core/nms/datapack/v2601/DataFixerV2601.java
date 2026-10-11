package com.volmit.iris.core.nms.datapack.v2601;

import com.volmit.iris.core.nms.datapack.v1217.DataFixerV1217;
import com.volmit.iris.util.json.JSONObject;

public class DataFixerV2601 extends DataFixerV1217 {
    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        super.fixDimension(dimension, json);

        JSONObject attributes = json.getJSONObject("attributes");
        switch (dimension) {
            case OVERWORLD -> {
                json.put("default_clock", "minecraft:overworld");
                attributes.put("minecraft:visual/ambient_light_color", "#0a0a0a");
            }
            case NETHER -> {
                json.put("has_fixed_time", true);
                attributes.put("minecraft:visual/ambient_light_color", "#302821");
            }
            case END -> {
                json.put("default_clock", "minecraft:the_end");
                json.put("has_fixed_time", true);
                attributes.put("minecraft:visual/ambient_light_color", "#3f473f");
            }
        }
    }
}
