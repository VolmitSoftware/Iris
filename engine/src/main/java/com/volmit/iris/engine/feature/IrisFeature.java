package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;

@Data
public abstract class IrisFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final String name;
    private final IrisEngine engine;
    private boolean heightAgnostic;

    public IrisFeature(String name, IrisEngine engine)
    {
        this.engine = engine;
        this.name = name;
        this.heightAgnostic = true;
    }

    public IrisFeatureTask<T, S> task(IrisFeatureSizedTarget target, int verticalExecutionSize, int horizontalExecutionSize)
    {
        return new IrisFeatureTask<>(engine, this, target, verticalExecutionSize, horizontalExecutionSize, heightAgnostic);
    }

    public IrisFeatureTask<T, S> task(IrisFeatureSizedTarget target, int horizontalExecutionSize)
    {
        return new IrisFeatureTask<>(engine, this, target, Integer.MAX_VALUE, horizontalExecutionSize, heightAgnostic);
    }

    public abstract S prepare(IrisEngine engine, IrisFeatureSizedTarget target);

    public abstract void generate(IrisEngine engine, S state, IrisFeatureTarget<T> target);
}
