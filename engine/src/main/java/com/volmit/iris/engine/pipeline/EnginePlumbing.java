package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import com.volmit.iris.engine.pipeline.EnginePipeline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class EnginePlumbing {
    private final IrisEngine engine;
    @Singular
    private final List<EnginePipeline> pipelines;

    public void generate(IrisEngine engine, IrisFeatureSizedTarget target, PipedHunkStack stack)
    {
        for(EnginePipeline i : pipelines)
        {
            i.generate(engine, target, stack);
        }
    }
}
