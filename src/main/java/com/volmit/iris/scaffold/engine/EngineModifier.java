package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;

public interface EngineModifier<T> extends EngineComponent {
    void modify(int x, int z, Hunk<T> t);
}
