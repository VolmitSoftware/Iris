package com.volmit.iris.engine.optimizer;

import art.arcane.amulet.metric.Average;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class IrisOptimizationAttempt<T> {
    private final Average average;
    private final AtomicInteger runs;
    private final int testRuns;
    private final T parameters;

    public IrisOptimizationAttempt(T parameters, int testRuns)
    {
        this.parameters = parameters;
        this.testRuns = testRuns;
        this.average = new Average(testRuns);
        this.runs = new AtomicInteger(0);
    }

    public double getAverageTime()
    {
        return average.getAverage();
    }

    public boolean isComplete()
    {
        return runs.get() >= testRuns;
    }

    public void report(double ms) {
        average.put(ms);
        runs.incrementAndGet();
    }
}
