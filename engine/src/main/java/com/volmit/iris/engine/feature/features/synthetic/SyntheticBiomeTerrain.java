package com.volmit.iris.engine.feature.features.synthetic;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.engine.feature.FeatureStorage;
import com.volmit.iris.engine.feature.FeatureTarget;
import com.volmit.iris.engine.feature.SyntheticFeature;
import com.volmit.iris.engine.feature.features.FeatureTerrain;
import com.volmit.iris.platform.block.PlatformBlock;

public class SyntheticBiomeTerrain implements SyntheticFeature<PlatformBlock, IrisBiome, FeatureTerrain.State>
{
    @Override
    public void generate(Engine engine, IrisBiome component, FeatureTarget<PlatformBlock> target, FeatureTerrain.State state) {

    }
}
