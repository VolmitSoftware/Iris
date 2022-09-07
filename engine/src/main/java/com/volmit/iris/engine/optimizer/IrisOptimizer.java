package com.volmit.iris.engine.optimizer;

import art.arcane.amulet.format.Form;
import art.arcane.chrono.Average;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IrisOptimizer<T> {
    private final String optimizedFeatureName;
    private final int testRuns;
    private int dummyRuns;
    private final List<T> options;
    private final Map<T, Double> results;
    private final Map<T, IrisOptimizationAttempt<T>> attempts = new ConcurrentHashMap<>();
    private T defaultOption;
    private final double chanceToTest;
    private double bestTime;

    public IrisOptimizer(int testRuns, List<T> options, T defaultOption, double chanceToTest, String optimizedFeatureName) {
        this.bestTime = Double.MAX_VALUE;
        this.dummyRuns = 1024;
        this.testRuns = testRuns;
        this.results = new HashMap<>();
        this.options = options;
        this.optimizedFeatureName = optimizedFeatureName;
        this.defaultOption = defaultOption;
        this.chanceToTest = chanceToTest;

        for(T i : options) {
            attempts.put(i, new IrisOptimizationAttempt<>(i, testRuns));
        }
    }

    public String toString() {
        return "optimizer";
    }

    public synchronized void report(T parameters, double ms)
    {
        if(dummyRuns-- > 0)
        {
            return;
        }

        IrisOptimizationAttempt<T> attempt = attempts.get(parameters);

        if(attempt != null) {
            attempt.report(ms);

            if(attempt.isComplete()) {
                results.put(parameters, attempt.getAverageTime());
                attempts.remove(parameters);
                double result = attempt.getAverageTime();

                if(result < bestTime) {
                    bestTime = result;
                    defaultOption = attempt.getParameters();
                }

                d("Attempted " + optimizedFeatureName + " with " + defaultOption.toString() + " " + Form.duration(attempt.getAverageTime(), 2));

                if(attempts.isEmpty()) {
                    d("Fully Optimized " + optimizedFeatureName + " with " + defaultOption.toString());

                    for(T i : results.keySet()) {
                        d(i.toString() + ": " + Form.duration(results.get(i), 2));
                    }
                }
            }
        }
    }

    public T nextParameters() {
        if(!attempts.isEmpty() && Math.r(chanceToTest)) {
            return attempts.k().popRandom();
        }

        return defaultOption;
    }
}
