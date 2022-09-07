package com.volmit.iris.engine.pipeline;

import art.arcane.amulet.range.IntegerRange;
import com.volmit.iris.engine.feature.*;
import com.volmit.iris.engine.optimizer.HunkSlizeConfiguration;
import com.volmit.iris.engine.optimizer.IrisOptimizer;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

import static art.arcane.amulet.MagicalSugar.*;

@AllArgsConstructor
@Data
public class PipelineTask<T extends PlatformNamespaced>
{
    private final Feature<T, ?> feature;
    private final Class<T> target;
    private final IntegerRange verticalEnvelope;
    private final IntegerRange horizontalEnvelope;
    private final IrisOptimizer<HunkSlizeConfiguration> optimizer;

    public PipelineTask(Feature<T, ?> feature, Class<T> target, IntegerRange verticalEnvelope, IntegerRange horizontalEnvelope)
    {
        this.feature = feature;
        this.target = target;
        this.verticalEnvelope = verticalEnvelope;
        this.horizontalEnvelope = horizontalEnvelope;
        List<HunkSlizeConfiguration> configurations = feature.isHeightAgnostic() ? HunkSlizeConfiguration.generateConfigurations(Integer.MAX_VALUE, horizontalEnvelope)
            : HunkSlizeConfiguration.generateConfigurations(verticalEnvelope, horizontalEnvelope);
        this.optimizer = new IrisOptimizer<>(128, configurations, configurations[0], 1, feature.getName());
    }

    public PipelineTask(Feature<T, ?> feature, Class<T> target) {
        this(feature, target, 1 to 16, 1 to 16);
    }

    public FeatureTask<T, ?> task(FeatureSizedTarget target, FeatureTarget<T> origin, FeatureStorage storage){
        HunkSlizeConfiguration configuration = getFeature().isOptimize() ? optimizer.nextParameters() : optimizer.getDefaultOption();
        return feature.task(target, origin, storage, configuration.getVerticalSlice(), configuration.getHorizontalSlize(), (ms) -> optimizer.report(configuration, ms));
    }
}
