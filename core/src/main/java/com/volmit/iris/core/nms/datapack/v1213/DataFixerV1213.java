package com.volmit.iris.core.nms.datapack.v1213;

import com.volmit.iris.core.nms.datapack.v1206.DataFixerV1206;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;

public class DataFixerV1213 extends DataFixerV1206 {

    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        json = super.fixCustomBiome(biome, json);
        json.put("carvers", new JSONArray());
        return json;
    }
}
