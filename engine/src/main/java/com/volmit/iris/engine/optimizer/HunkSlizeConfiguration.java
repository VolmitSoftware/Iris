package com.volmit.iris.engine.optimizer;

import art.arcane.amulet.range.IntegerRange;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class HunkSlizeConfiguration {
    private final int verticalSlice;
    private final int horizontalSlize;

    public static List<HunkSlizeConfiguration> generateConfigurations(IntegerRange vertical, IntegerRange horizontal)
    {
        List<HunkSlizeConfiguration> configurations = new ArrayList<>();

        for(int i : slice(vertical))
        {
            for(int j : slice(horizontal))
            {
                configurations.add(new HunkSlizeConfiguration(i, j));
            }
        }

        return configurations;
    }

    public static List<HunkSlizeConfiguration> generateConfigurations(int vertical, IntegerRange horizontal)
    {
        List<HunkSlizeConfiguration> configurations = new ArrayList<>();

        for(int j : slice(horizontal))
        {
            configurations.add(new HunkSlizeConfiguration(vertical, j));
        }

        return configurations;
    }

    private static List<Integer> slice(IntegerRange range)
    {
        List<Integer> v = new ArrayList<>();
        v.add(range.getRightEndpoint());
        v.add(range.getLeftEndpoint());
        int i = (int) (range.getRightEndpoint() / 1.25);

        while(i > range.getLeftEndpoint() && i >= 1)
        {
            v.add(i);
            i /= 1.25;
        }

        return v.withoutDuplicates();
    }
}
