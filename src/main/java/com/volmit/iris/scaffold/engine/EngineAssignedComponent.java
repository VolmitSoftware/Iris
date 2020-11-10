package com.volmit.iris.scaffold.engine;

import com.volmit.iris.util.RollingSequence;
import lombok.Data;

@Data
public class EngineAssignedComponent implements EngineComponent {
    private final Engine engine;
    private final RollingSequence metrics;
    private final String name;

    public EngineAssignedComponent(Engine engine, String name)
    {
        this.engine = engine;
        this.metrics = new RollingSequence(16);
        this.name = name;
    }
}
