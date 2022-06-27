package com.volmit.iris.engine.feature;

import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class IrisPreparedFeature<T extends PlatformNamespaced, S extends IrisFeatureState> {
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final S state;
}
