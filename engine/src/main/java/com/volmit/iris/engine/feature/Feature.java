package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;

@Data
public abstract class Feature<T extends PlatformNamespaced, S extends FeatureState> {
    private final String name;
    private final Engine engine;
    private boolean heightAgnostic;

    public Feature(String name, Engine engine)
    {
        this.engine = engine;
        this.name = name;
        this.heightAgnostic = true;
    }

    public FeatureTask<T, S> task(FeatureSizedTarget target, FeatureTarget<T> origin, FeatureStorage storage, int verticalExecutionSize, int horizontalExecutionSize, FeatureTaskTiming timings)
    {
        return new FeatureTask<>(engine, this, storage, target, origin, verticalExecutionSize, horizontalExecutionSize, heightAgnostic, timings);
    }

    public FeatureTask<T, S> task(FeatureSizedTarget target, FeatureTarget<T> origin, FeatureStorage storage, int horizontalExecutionSize, FeatureTaskTiming timings)
    {
        return new FeatureTask<>(engine, this, storage, target, origin, Integer.MAX_VALUE, horizontalExecutionSize, heightAgnostic, timings);
    }

    public abstract S prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage);

    public abstract void generate(Engine engine, S state, FeatureTarget<T> target, FeatureStorage storage);
}
