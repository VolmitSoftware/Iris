package com.volmit.iris.engine.feature;

import art.arcane.amulet.metric.PrecisionStopwatch;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * Continuously splits up a hunk of work in all 3 dimensions until the job size is within the
 * specified limits. This allows a single hunk to be split until the ideal amount of tasks can run in parallel.
 * @param <T> The namespaced object type
 * @param <S> The feature state type
 */
@Builder
@AllArgsConstructor
public class IrisFeatureTask<T extends PlatformNamespaced, S extends IrisFeatureState> extends RecursiveTask<IrisFeatureTarget<T>> implements Callable<IrisFeatureTarget<T>> {
    private final Engine engine;
    private final IrisFeature<T, S> feature;
    private final IrisFeatureSizedTarget size;
    private final IrisFeatureTarget<T> origin;
    private final int verticalPrepareSize;
    private final int horizontalPrepareSize;
    private final boolean heightAgnostic;
    private final IrisFeatureTaskTiming timings;

    @Override
    protected IrisFeatureTarget<T> compute() {
        IrisFeatureTarget<T> result;
        PrecisionStopwatch p = null;

        if(timings != null)
        {
            p = PrecisionStopwatch.start();
        }

        if(!heightAgnostic && size.getHeight() > verticalPrepareSize * 2) {

            result = IrisFeatureTarget.mergedTarget(size.splitY()
                .map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<IrisFeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, false, true, false);
        }

        else if(size.getWidth() > horizontalPrepareSize * 2) {
            result = IrisFeatureTarget.mergedTarget(size.splitX().map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<IrisFeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, true, false, false);
        }

        else if(size.getDepth() > horizontalPrepareSize * 2) {
            result = IrisFeatureTarget.mergedTarget(size.splitZ().map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<IrisFeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, false, false, true);
        }

        else {
            IrisPreparedFeature<T, S> preparedFeature = new IrisPreparedFeature<>(engine, feature, size, feature.prepare(engine, size));
            result = preparedFeature.generate(origin);
        }

        if(timings != null)
        {
            timings.onCompleted(p.getMilliseconds());
        }

        return result;
    }

    private IrisFeatureTask<T, S> with(IrisFeatureSizedTarget size)
    {
        return new IrisFeatureTask<>(engine, feature, size, origin, verticalPrepareSize, horizontalPrepareSize, heightAgnostic, null);
    }

    @Override
    public IrisFeatureTarget<T> call() throws Exception {
        return compute();
    }
}
