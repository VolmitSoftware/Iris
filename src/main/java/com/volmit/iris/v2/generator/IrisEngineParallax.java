package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineParallaxManager;
import com.volmit.iris.v2.scaffold.engine.EngineStructureManager;
import lombok.Getter;

public class IrisEngineParallax implements EngineParallaxManager {
    @Getter
    private final Engine engine;

    @Getter
    private final EngineStructureManager structureManager;

    @Getter
    private final int parallaxSize;

    public IrisEngineParallax(Engine engine)
    {
        this.engine = engine;
        parallaxSize = computeParallaxSize();
        structureManager = new IrisEngineStructure(getEngine());
    }
}
