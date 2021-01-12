package com.volmit.iris.generator;

import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineParallaxManager;
import lombok.Getter;

public class IrisEngineParallax implements EngineParallaxManager {
    @Getter
    private final Engine engine;

    @Getter
    private final int parallaxSize;

    public IrisEngineParallax(Engine engine)
    {
        this.engine = engine;
        parallaxSize = computeParallaxSize();
    }
}
