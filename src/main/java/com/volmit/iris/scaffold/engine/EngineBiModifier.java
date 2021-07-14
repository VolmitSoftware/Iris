package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public interface EngineBiModifier<A, B> extends EngineComponent {
    void modify(int x, int z, Hunk<A> a, Hunk<B> b);
}
