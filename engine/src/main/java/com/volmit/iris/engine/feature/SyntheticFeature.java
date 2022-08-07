package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.FeatureStorage;
import com.volmit.iris.engine.feature.FeatureTarget;
import com.volmit.iris.engine.feature.features.FeatureTerrain;
import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.platform.PlatformNamespaced;

public interface SyntheticFeature<T extends PlatformNamespaced, V extends EngineResolvable, S extends FeatureState> {
    void generate(Engine engine, V component, FeatureTarget<T> target, S state);
}
