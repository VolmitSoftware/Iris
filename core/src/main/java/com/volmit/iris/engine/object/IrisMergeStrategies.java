package com.volmit.iris.engine.object;


import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.stream.ProceduralStream;

import java.util.function.Function;

@Desc("Modes for generator merging")
public enum IrisMergeStrategies {
    @Desc("Splits the world in height. Use the split settings to customize this option")
    SPLIT,

    @Desc("Split from of the engine height")
    SPLIT_ENGINE_HEIGHT,

    @Desc("Merge from of the engine height")
    MERGE_ENGINE_HEIGHT,
}
