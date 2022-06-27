package com.volmit.iris.engine.pipeline;

import art.arcane.amulet.collections.ObjectBiset;
import art.arcane.amulet.collections.hunk.Hunk;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.feature.IrisFeature;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import com.volmit.iris.engine.feature.IrisFeatureTarget;
import com.volmit.iris.engine.feature.IrisFeatureTask;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import static art.arcane.amulet.MagicalSugar.*;

@Data
@Builder
@AllArgsConstructor
public class PipelinePhase
{
    @Singular
    private final List<PipelineTask<?>> tasks;

    @SuppressWarnings({"unchecked"})
    public void generate(IrisEngine engine, IrisFeatureSizedTarget target, PipedHunkStack stack) {
        List<IrisFeatureTarget<?>> targets = engine.getExecutor().getForks().invokeAll(tasks.stream().map(i -> i.task(target)).collect(Collectors.toList())).stream().map(i -> {
            try {
                return i.get();
            } catch(InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        for(int i : index targets)
        {
            IrisFeatureTarget<?> targetResult = targets[i];
            stack.hunk(tasks[i].getTarget()).insert((Hunk<PlatformNamespaced>) targetResult.getHunk());
        }
    }
}
