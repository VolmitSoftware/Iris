package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineFramework;
import com.volmit.iris.v2.scaffold.engine.EngineParallax;
import com.volmit.iris.v2.scaffold.engine.EngineStructure;
import lombok.Getter;

public class IrisEngineParallax implements EngineParallax {
    @Getter
    private final Engine engine;

    @Getter
    private final EngineStructure structureManager;

    @Getter
    private final int parallaxSize;

    public IrisEngineParallax(Engine engine)
    {
        this.engine = engine;
        parallaxSize = computeParallaxSize();
        structureManager = new IrisEngineStructure(getEngine());
    }
}
