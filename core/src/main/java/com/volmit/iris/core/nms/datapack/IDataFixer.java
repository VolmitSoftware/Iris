package com.volmit.iris.core.nms.datapack;

import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.json.JSONObject;

public interface IDataFixer {

    default JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        return json;
    }

    JSONObject rawDimension(Dimension dimension);

    default JSONObject createDimension(Dimension dimension, int minY, int maxY, int logicalHeight) {
        JSONObject obj = rawDimension(dimension);
        obj.put("min_y", minY);
        obj.put("height", maxY - minY);
        obj.put("logical_height", logicalHeight);
        return obj;
    }

    enum Dimension {
        OVERWORLD,
        NETHER,
        END
    }
}
