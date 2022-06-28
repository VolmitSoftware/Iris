package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import com.volmit.iris.engine.feature.IrisFeatureTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class PipelinePhase
{
    @Singular
    private final List<PipelineTask<?>> tasks;

    public List<IrisFeatureTarget<?>> generate(Engine engine, IrisFeatureSizedTarget target, PipedHunkStack stack) {
        return engine.getExecutor().getForks().invokeAll(tasks.stream().map(i -> i.task(target, stack.hunk(i.getTarget())))
            .collect(Collectors.toList())).stream().map(i -> {
            try {
                return i.get();
            } catch(InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }
}
