package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineFramework;
import com.volmit.iris.v2.scaffold.engine.EngineTarget;
import lombok.Getter;
import lombok.Setter;

public class IrisEngine implements Engine
{
    @Getter
    private final EngineTarget target;

    @Getter
    private final EngineFramework framework;

    @Setter
    @Getter
    private volatile int parallelism;

    public IrisEngine(EngineTarget target)
    {
        this.target = target;
        this.framework = new IrisEngineFramework(this);
    }
}
