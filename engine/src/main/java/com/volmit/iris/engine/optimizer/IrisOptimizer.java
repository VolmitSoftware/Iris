package com.volmit.iris.engine.optimizer;

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
    private final int testRuns;
    private final List<T> options;
    private final Map<T, IrisOptimizationAttempt<T>> attempts = new ConcurrentHashMap<>();
    private T defaultOption;
    private final double chanceToTest;
    private double bestTime;

    public IrisOptimizer(int testRuns, List<T> options, T defaultOption, double chanceToTest)
    {
        this.bestTime = Double.MAX_VALUE;
        this.testRuns = testRuns;
        this.options = options;
        this.defaultOption = defaultOption;
        this.chanceToTest = chanceToTest;

        for(T i : options)
        {
            attempts.put(i, new IrisOptimizationAttempt<>(i, testRuns));
        }
    }

    public String toString()
    {
        return "optimizer";
    }

    public synchronized void report(T parameters, double ms)
    {
        IrisOptimizationAttempt<T> attempt = attempts.get(parameters);

        if(attempt != null)
        {
            attempt.report(ms);

            if(attempt.isComplete())
            {
                attempts.remove(parameters);
                double result = attempt.getAverageTime();

                if(result < bestTime)
                {
                    bestTime = result;
                    defaultOption = attempt.getParameters();
                }
            }
        }
    }

    public T nextParameters()
    {
        if(!attempts.isEmpty() && Math.r(chanceToTest))
        {
            return attempts.k().popRandom();
        }

        return defaultOption;
    }
}
