package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;

@Data
public abstract class IrisFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final String name;
    private boolean heightAgnostic;

    public IrisFeature(String name, IrisEngine engine)
    {
        this.name = name;
        this.heightAgnostic = true;
    }

    public S prepare(IrisEngine engine, IrisFeatureSizedTarget target)
    {
        return onPrepare(engine, target);
    }

    public void generate(IrisEngine engine, S state, IrisFeatureTarget<T> target)
    {
        onGenerate(engine, state, target);
    }

    public abstract S onPrepare(IrisEngine engine, IrisFeatureSizedTarget target);

    public abstract void onGenerate(IrisEngine engine, S state, IrisFeatureTarget<T> target);
}
