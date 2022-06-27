package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.EngineExecutor;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class EnginePipeline
{
    @Singular
    private final List<PipelinePhase> phases;

    public void generate(IrisEngine engine,IrisFeatureSizedTarget target,  PipedHunkStack stack)
    {
        for(PipelinePhase i : phases)
        {
            i.generate(engine, target, stack);
        }
    }
}
