package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.FeatureSizedTarget;
import com.volmit.iris.engine.feature.FeatureStorage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class EnginePipeline
{
    @Singular
    private final List<PipelinePhase> phases;

    public void generate(Engine engine, FeatureSizedTarget target, PipedHunkStack stack) {
        FeatureStorage storage = new FeatureStorage(engine.getConfiguration().getChunkSize(), engine.getConfiguration().getChunkSize());

        for(PipelinePhase i : phases) {
            i.generate(engine, target, stack, storage);
        }
    }
}
