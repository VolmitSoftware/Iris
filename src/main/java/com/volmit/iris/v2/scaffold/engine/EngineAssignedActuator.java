package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.block.data.BlockData;

public abstract class EngineAssignedActuator<T> extends EngineAssignedComponent implements EngineActuator<T>
{
    public EngineAssignedActuator(Engine engine, String name)
    {
        super(engine, name);
    }

    public abstract void onActuate(int x, int z, Hunk<T> output);

    @Override
    public void actuate(int x, int z, Hunk<T> output) {
        onActuate(x, z, output);
    }
}
