package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class IrisPreparedFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final Engine engine;
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final S state;

    public IrisFeatureTarget<T> generate(IrisFeatureTarget<T> origin)
    {
        IrisFeatureTarget<T> target = size.hunked(origin);
        feature.generate(engine, state, target);
        return target;
    }
}
