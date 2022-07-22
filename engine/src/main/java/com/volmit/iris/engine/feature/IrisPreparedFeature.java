package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class IrisPreparedFeature<T extends PlatformNamespaced, S extends FeatureState> {
    private final Engine engine;
    private final Feature<T, S> feature;
    private final FeatureSizedTarget size;
    private final S state;

    public FeatureTarget<T> generate(FeatureTarget<T> origin, FeatureStorage storage) {
        FeatureTarget<T> target = size.hunked(origin);
        feature.generate(engine, state, target, storage);
        return target;
    }
}
