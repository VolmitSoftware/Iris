package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
public class IrisFeatureTask<T extends PlatformNamespaced, S extends IrisFeatureState> extends RecursiveTask<IrisPreparedFeature<T, S>> {
    private final IrisEngine engine;
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final int verticalPrepareSize;
    private final int horizontalPrepareSize;
    private final boolean heightAgnostic;

    @Override
    protected IrisPreparedFeature<T, S> compute() {
        if(!heightAgnostic && size.getHeight() > verticalPrepareSize * 2) {
            invokeAll(size.splitY().map(this::with).collect(Collectors.toList()));
        }

        else if(size.getWidth() > horizontalPrepareSize * 2) {
            invokeAll(size.splitX().map(this::with).collect(Collectors.toList()));
        }

        else if(size.getDepth() > horizontalPrepareSize * 2) {
            invokeAll(size.splitZ().map(this::with).collect(Collectors.toList()));
        }

        else {
            return new IrisPreparedFeature<>(feature, size, feature.prepare(engine, size));
        }

        return null;
    }

    private IrisFeatureTask<T, S> with(IrisFeatureSizedTarget size)
    {
        return new IrisFeatureTask<>(engine, feature, size, verticalPrepareSize, horizontalPrepareSize, heightAgnostic);
    }
}
