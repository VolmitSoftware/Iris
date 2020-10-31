package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.scaffold.hunk.Hunk;

public interface EngineModifier<T>  extends EngineComponent {
    public void modify(Hunk<T> t);
}
