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
        output.compute2D(getParallelism(), (xx, yy, zz, h) -> {
            int i,zf;
            Biome v;

            for(int xf = 0; xf < h.getWidth(); xf++)
            {
                for(zf = 0; zf < h.getDepth(); zf++)
                {
                    v = getComplex().getTrueBiomeDerivativeStream().get(xx+xf+x, zz+zf+z);

                    for(i = 0; i < h.getHeight(); i++)
                    {
                        h.set(xx+xf, i, zz+zf, v);
                    }
                }
            }
        });
    }
}
