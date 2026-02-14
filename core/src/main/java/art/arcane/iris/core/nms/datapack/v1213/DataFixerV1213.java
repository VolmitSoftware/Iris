package art.arcane.iris.core.nms.datapack.v1213;

import art.arcane.iris.core.nms.datapack.v1206.DataFixerV1206;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

public class DataFixerV1213 extends DataFixerV1206 {

    @Override
    public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        json = super.fixCustomBiome(biome, json);
        json.put("carvers", new JSONArray());
        return json;
    }
}
