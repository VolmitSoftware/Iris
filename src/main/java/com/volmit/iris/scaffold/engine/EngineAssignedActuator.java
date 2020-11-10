package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public abstract class EngineAssignedActuator<T> extends EngineAssignedComponent implements EngineActuator<T>
{
    public EngineAssignedActuator(Engine engine, String name)
    {
        super(engine, name);
    }

    public abstract void onActuate(int x, int z, Hunk<T> output);

    @Override
    public void actuate(int x, int z, Hunk<T> output) {
        onActuate(x, z, output);
    }
}
