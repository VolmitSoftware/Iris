package com.volmit.iris.core.scripting.func;

import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.documentation.BlockCoordinates;

@FunctionalInterface
public interface BiomeLookup {
    @BlockCoordinates
    IrisBiome at(int x, int z);
}
