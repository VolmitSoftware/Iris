package com.volmit.iris.core.nms.datapack.v1192;

import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.engine.object.IrisDimensionTypeOptions;
import com.volmit.iris.util.json.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.volmit.iris.engine.object.IrisDimensionTypeOptions.TriState.*;

public class DataFixerV1192 implements IDataFixer {
    private static final Map<Dimension, IrisDimensionTypeOptions> OPTIONS = Map.of(
            Dimension.OVERWORLD, new IrisDimensionTypeOptions(
                    FALSE,
                    TRUE,
                    FALSE,
                    FALSE,
                    TRUE,
                    TRUE,
                    TRUE,
                    FALSE,
                    1d,
                    0f,
                    null,
                    192,
                    0),
            Dimension.NETHER, new IrisDimensionTypeOptions(
                    TRUE,
                    FALSE,
                    TRUE,
                    TRUE,
                    FALSE,
                    FALSE,
                    FALSE,
                    TRUE,
                    8d,
                    0.1f,
                    18000L,
                    null,
                    15),
            Dimension.END, new IrisDimensionTypeOptions(
                    FALSE,
                    FALSE,
                    FALSE,
                    FALSE,
                    FALSE,
                    TRUE,
                    FALSE,
                    FALSE,
                    1d,
                    0f,
                    6000L,
                    null,
                    0)
    );

    private static final Map<Dimension, String> DIMENSIONS = Map.of(
            Dimension.OVERWORLD, """
            {
              "effects": "minecraft:overworld",
              "infiniburn": "#minecraft:infiniburn_overworld",
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              }
            }""",
            Dimension.NETHER, """
            {
              "effects": "minecraft:the_nether",
              "infiniburn": "#minecraft:infiniburn_nether",
              "monster_spawn_light_level": 7,
            }""",
            Dimension.END, """
            {
              "effects": "minecraft:the_end",
              "infiniburn": "#minecraft:infiniburn_end",
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              }
            }"""
    );

    @Override
    public JSONObject resolve(Dimension dimension, @Nullable IrisDimensionTypeOptions options) {
        return options == null ? OPTIONS.get(dimension).toJson() : options.resolve(OPTIONS.get(dimension)).toJson();
    }

    @Override
    public void fixDimension(Dimension dimension, JSONObject json) {
        var missing = new JSONObject(DIMENSIONS.get(dimension));
        for (String key : missing.keySet()) {
            if (json.has(key)) continue;
            json.put(key, missing.get(key));
        }
    }
}
