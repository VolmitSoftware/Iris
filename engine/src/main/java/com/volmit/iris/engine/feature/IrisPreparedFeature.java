package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class IrisPreparedFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final IrisEngine engine;
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final S state;

    public IrisFeatureTarget<T> generate()
    {
        IrisFeatureTarget<T> target = size.hunked();

        if(Math.r(0.25))
        {
            feature.generate(engine, state, target);
        }

        return target;
    }
}
