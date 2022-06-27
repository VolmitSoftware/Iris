package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;

@Data
public abstract class IrisFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final String name;

    public IrisFeature(String name, IrisEngine engine)
    {
        this.name = name;
    }

    public abstract S prepare(IrisEngine engine, IrisFeatureSizedTarget target);

    public abstract void generate(IrisEngine engine, S state, IrisFeatureTarget<T> target);
}
