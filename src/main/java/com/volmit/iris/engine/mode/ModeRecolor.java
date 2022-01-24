package com.volmit.iris.engine.mode;

import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisEngineMode;

public class ModeRecolor extends IrisEngineMode {
    public ModeRecolor(Engine engine) {
        super(engine);

        var biome = new IrisBiomeActuator(getEngine());

        registerStage(burst((x, z, k, p, m) -> biome.actuate(x, z, p, m)));
    }
}
