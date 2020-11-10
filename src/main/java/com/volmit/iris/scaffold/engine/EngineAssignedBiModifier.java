package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public abstract class EngineAssignedBiModifier<A, B> extends EngineAssignedComponent implements EngineBiModifier<A, B>
{
    public EngineAssignedBiModifier(Engine engine, String name)
    {
        super(engine, name);
    }

    public abstract void onModify(int x, int z, Hunk<A> a, Hunk<B> b);

    @Override
    public void modify(int x, int z, Hunk<A> a, Hunk<B> b) {
        onModify(x, z, a, b);
    }
}
