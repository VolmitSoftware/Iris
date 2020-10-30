package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;

public interface EngineModifier<T>  extends EngineComponent {
    public void modify(Hunk<T> t);
}
