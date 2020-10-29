package com.volmit.iris.gen.v2.generator.actuator;

import com.volmit.iris.gen.v2.scaffold.engine.Engine;
import com.volmit.iris.gen.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import org.bukkit.block.data.BlockData;

public class IrisTerrainActuator extends EngineAssignedActuator<BlockData>
{
    public IrisTerrainActuator(Engine engine) {
        super(engine);
    }

    @Override
    public void actuate(int x, int z, Hunk<BlockData> output) {
        getComplex().getHeightFluidStream().fill2D(output, x, z, getComplex().getTerrainStream(), getParallelism());
    }
}
