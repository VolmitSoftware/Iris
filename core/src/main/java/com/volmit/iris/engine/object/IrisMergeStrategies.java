package com.volmit.iris.engine.object;


import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Modes for generator merging")
public enum IrisMergeStrategies {
    @Desc("Splits the world in height. Use the split settings to customize this option")
    SPLIT,

    @Desc("Merge from of the engine height")
    SPLIT_ENGINE_HEIGHT,
}
