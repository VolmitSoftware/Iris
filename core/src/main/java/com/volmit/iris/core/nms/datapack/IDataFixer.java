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

    default JSONObject createPreset() {
        return new JSONObject("""
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "iris:overworld",
      "generator": {
        "type": "minecraft:noise",
        "biome_source": {
          "type": "minecraft:multi_noise",
          "preset": "minecraft:overworld"
        },
        "settings": "minecraft:overworld"
      }
    },
    "minecraft:the_end": {
      "type": "iris:the_end",
      "generator": {
        "type": "minecraft:noise",
        "biome_source": {
          "type": "minecraft:the_end"
        },
        "settings": "minecraft:end"
      }
    },
    "minecraft:the_nether": {
      "type": "iris:the_nether",
      "generator": {
        "type": "minecraft:noise",
        "biome_source": {
          "type": "minecraft:multi_noise",
          "preset": "minecraft:nether"
        },
        "settings": "minecraft:nether"
      }
    }
  }
}""");
    }

    enum Dimension {
        OVERRWORLD,
        NETHER,
        THE_END
    }
}
