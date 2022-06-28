package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;

@Data
public abstract class IrisFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final String name;
    private final Engine engine;
    private boolean heightAgnostic;

    public IrisFeature(String name, Engine engine)
    {
        this.engine = engine;
        this.name = name;
        this.heightAgnostic = true;
    }

    public IrisFeatureTask<T, S> task(IrisFeatureSizedTarget target, IrisFeatureTarget<T> origin, int verticalExecutionSize, int horizontalExecutionSize, IrisFeatureTaskTiming timings)
    {
        return new IrisFeatureTask<>(engine, this, target, origin, verticalExecutionSize, horizontalExecutionSize, heightAgnostic, timings);
    }

    public IrisFeatureTask<T, S> task(IrisFeatureSizedTarget target, IrisFeatureTarget<T> origin, int horizontalExecutionSize, IrisFeatureTaskTiming timings)
    {
        return new IrisFeatureTask<>(engine, this, target, origin, Integer.MAX_VALUE, horizontalExecutionSize, heightAgnostic, timings);
    }

    public abstract S prepare(Engine engine, IrisFeatureSizedTarget target);

    public abstract void generate(Engine engine, S state, IrisFeatureTarget<T> target);
}
