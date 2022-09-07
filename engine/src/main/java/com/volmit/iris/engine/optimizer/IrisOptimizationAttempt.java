package com.volmit.iris.engine.optimizer;

import art.arcane.chrono.Average;
import art.arcane.spatial.hunk.storage.AtomicDoubleHunk;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class IrisOptimizationAttempt<T> {
    private double value;
    private final AtomicInteger runs;
    private final int testRuns;
    private final T parameters;

    public IrisOptimizationAttempt(T parameters, int testRuns)
    {
        this.parameters = parameters;
        this.testRuns = testRuns;
        this.value = 0;
        this.runs = new AtomicInteger(0);
    }

    public double getAverageTime()
    {
        return value;
    }

    public boolean isComplete()
    {
        return runs.get() >= testRuns;
    }

    public void report(double ms) {
        value += ms;
        runs.incrementAndGet();
    }
}
