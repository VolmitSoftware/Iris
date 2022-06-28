package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class EnginePlumbing {
    private final Engine engine;
    @Singular
    private final List<EnginePipeline> pipelines;

    public void generate(Engine engine, IrisFeatureSizedTarget target, PipedHunkStack stack)
    {
        for(EnginePipeline i : pipelines)
        {
            i.generate(engine, target, stack);
        }
    }
}
