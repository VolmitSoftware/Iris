package com.volmit.iris.engine.feature;

import art.arcane.chrono.PrecisionStopwatch;
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
public class FeatureTask<T extends PlatformNamespaced, S extends FeatureState> extends RecursiveTask<FeatureTarget<T>> implements Callable<FeatureTarget<T>> {
    private final Engine engine;
    private final Feature<T, S> feature;
    private final FeatureStorage storage;
    private final FeatureSizedTarget size;
    private final FeatureTarget<T> origin;
    private final int verticalPrepareSize;
    private final int horizontalPrepareSize;
    private final boolean heightAgnostic;
    private final FeatureTaskTiming timings;

    @Override
    protected FeatureTarget<T> compute() {
        FeatureTarget<T> result;
        PrecisionStopwatch p = null;

        if(timings != null)
        {
            p = PrecisionStopwatch.start();
        }

        if(!heightAgnostic && size.getHeight() > verticalPrepareSize * 2) {

            result = FeatureTarget.mergedTarget(size.splitY()
                .map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<FeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, false, true, false);
        }

        else if(size.getWidth() > horizontalPrepareSize * 2) {
            result = FeatureTarget.mergedTarget(size.splitX().map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<FeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, true, false, false);
        }

        else if(size.getDepth() > horizontalPrepareSize * 2) {
            result = FeatureTarget.mergedTarget(size.splitZ().map(i -> engine.getExecutor().getForks().submit((ForkJoinTask<FeatureTarget<T>>) with(i)))
                .map(ForkJoinTask::join), origin, false, false, true);
        }

        else {
            IrisPreparedFeature<T, S> preparedFeature = new IrisPreparedFeature<>(engine, feature, size, feature.prepare(engine, size, storage));
            result = preparedFeature.generate(origin, storage);
        }

        if(timings != null)
        {
            timings.onCompleted(p.getMilliseconds());
        }

        return result;
    }

    private FeatureTask<T, S> with(FeatureSizedTarget size)
    {
        return new FeatureTask<>(engine, feature, storage, size, origin, verticalPrepareSize, horizontalPrepareSize, heightAgnostic, null);
    }

    @Override
    public FeatureTarget<T> call() throws Exception {
        return compute();
    }
}
