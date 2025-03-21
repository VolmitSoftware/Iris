package com.volmit.iris.core.nms.datapack;

import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.engine.object.IrisRange;
import com.volmit.iris.util.json.JSONObject;

public interface IDataFixer {

    default JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
        return json;
    }

    JSONObject rawDimension(Dimension dimension);

    default JSONObject createDimension(Dimension dimension, IrisRange height, int logicalHeight) {
        JSONObject obj = rawDimension(dimension);
        obj.put("min_y", height.getMin());
        obj.put("height", height.getMax() - height.getMin());
        obj.put("logical_height", logicalHeight);
        return obj;
    }

    enum Dimension {
        OVERRWORLD,
        NETHER,
        THE_END
    }
}
