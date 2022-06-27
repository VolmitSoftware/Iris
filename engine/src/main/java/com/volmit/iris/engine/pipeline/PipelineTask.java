package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.feature.IrisFeature;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import com.volmit.iris.engine.feature.IrisFeatureTask;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Builder
@Data
public class PipelineTask<T extends PlatformNamespaced>
{
    private final IrisFeature<T, ?> feature;
    private final Class<T> target;

    public IrisFeatureTask<T, ?> task(IrisFeatureSizedTarget target){
        return feature.task(target, 1000, 4);
    }
}
