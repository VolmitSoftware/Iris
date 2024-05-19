package com.volmit.iris.core.nms.datapack.v1192;

import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONObject;

public class DataFixerV1192 implements IDataFixer {

    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        return json;
    }

    @Override
    public JSONObject fixDimension(JSONObject json) {
        return json;
    }
}
