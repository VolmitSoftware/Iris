package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public abstract class EngineAssignedModifier<T> extends EngineAssignedComponent implements EngineModifier<T>
{
    public EngineAssignedModifier(Engine engine, String name)
    {
        super(engine, name);
    }

    public abstract void onModify(int x, int z, Hunk<T> output);

    @Override
    public void modify(int x, int z, Hunk<T> output) {
        onModify(x, z, output);
    }
}
