package com.volmit.iris.core.nms.datapack;

import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONObject;

public interface IDataFixer {

    JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json);

    JSONObject fixDimension(JSONObject json);
}
