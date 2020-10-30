package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.block.data.BlockData;

public abstract class EngineAssignedActuator<T> implements EngineActuator<T>
{

    @Getter
    private final Engine engine;

    @Getter
    private final RollingSequence metrics;

    @Getter
    private final String name;

    public EngineAssignedActuator(Engine engine, String name)
    {
        this.engine = engine;
        this.name = name;
        metrics = new RollingSequence(16);
    }

    public abstract void onActuate(int x, int z, Hunk<T> output);

    @Override
    public void actuate(int x, int z, Hunk<T> output) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        onActuate(x, z, output);
        p.end();
        getMetrics().put(p.getMilliseconds());
    }
}
