package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.FeatureSizedTarget;
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
    private final EnginePipeline errorPipeline;

    public void generate(Engine engine, FeatureSizedTarget target, PipedHunkStack stack) {
        for(EnginePipeline i : pipelines) {
            i.generate(engine, target, stack);
        }
        try {
        }

        catch(Throwable e) {
            e.printStackTrace();
            getErrorPipeline().generate(engine, target, stack);
        }
    }
}
