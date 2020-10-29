package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import org.bukkit.block.Biome;

public class IrisBiomeActuator extends EngineAssignedActuator<Biome>
{
    public IrisBiomeActuator(Engine engine) {
        super(engine);
    }

    @Override
    public void actuate(int x, int z, Hunk<Biome> output) {
        getComplex().getMaxHeightStream().fill2DYLocked(output, x, z, getComplex().getTrueBiomeDerivativeStream(), getParallelism());
    }
}
