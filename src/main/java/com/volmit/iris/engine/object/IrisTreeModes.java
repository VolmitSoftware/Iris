package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Sapling override object picking options")
public enum IrisTreeModes {
    @Desc("Check biome, then region, then dimension, pick the first one that has options")
    FIRST,

    @Desc("Check biome, regions, and dimensions, and pick any option from the total list")
    ALL
}