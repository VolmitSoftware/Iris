package com.volmit.iris.engine.feature;

import art.arcane.amulet.collections.hunk.Hunk;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.concurrent.Callable;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * Continuously splits up a hunk of work in all 3 dimensions until the job size is within the
 * specified limits. This allows a single hunk to be split until the ideal amount of tasks can run in parallel.
 * @param <T> The namespaced object type
 * @param <S> The feature state type
 */
@Builder
@AllArgsConstructor
public class IrisFeatureTask<T extends PlatformNamespaced, S extends IrisFeatureState> extends RecursiveTask<IrisFeatureTarget<T>> implements Callable<IrisFeatureTarget<T>> {
    private final IrisEngine engine;
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final int verticalPrepareSize;
    private final int horizontalPrepareSize;
    private final boolean heightAgnostic;

    @Override
    protected IrisFeatureTarget<T> compute() {
        if(!heightAgnostic && size.getHeight() > verticalPrepareSize * 2) {
            return IrisFeatureTarget.mergedTarget(size.splitY().map(i -> engine.getExecutor().getForks().invoke(with(i))));
        }

        else if(size.getWidth() > horizontalPrepareSize * 2) {
            return IrisFeatureTarget.mergedTarget(size.splitX().map(i -> engine.getExecutor().getForks().invoke(with(i))));
        }

        else if(size.getDepth() > horizontalPrepareSize * 2) {
            return IrisFeatureTarget.mergedTarget(size.splitZ().map(i -> engine.getExecutor().getForks().invoke(with(i))));
        }

        IrisPreparedFeature<T, S> preparedFeature = new IrisPreparedFeature<>(engine, feature, size, feature.prepare(engine, size));
        return preparedFeature.generate();
    }

    private IrisFeatureTask<T, S> with(IrisFeatureSizedTarget size)
    {
        return new IrisFeatureTask<>(engine, feature, size, verticalPrepareSize, horizontalPrepareSize, heightAgnostic);
    }

    @Override
    public IrisFeatureTarget<T> call() throws Exception {
        return compute();
    }
}
