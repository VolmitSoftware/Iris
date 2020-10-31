package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.scaffold.hunk.Hunk;

public interface EngineActuator<O> extends EngineComponent
{
    public void actuate(int x, int z, Hunk<O> output);
}
