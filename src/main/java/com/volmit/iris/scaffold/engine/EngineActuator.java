package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public interface EngineActuator<O> extends EngineComponent
{
    public void actuate(int x, int z, Hunk<O> output);
}
