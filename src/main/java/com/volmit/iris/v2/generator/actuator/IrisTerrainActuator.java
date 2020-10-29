package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public class IrisTerrainActuator extends EngineAssignedActuator<BlockData>
{
    public IrisTerrainActuator(Engine engine) {
        super(engine);
    }

    @Override
    public void actuate(int x, int z, Hunk<BlockData> output) {
        output.compute2D(getParallelism(), (xx, yy, zz, h) -> {
            int i,zf;
            double he;

            for(int xf = 0; xf < h.getWidth(); xf++)
            {
                for(zf = 0; zf < h.getDepth(); zf++)
                {
                    he = Math.min(h.getHeight(), getComplex().getHeightFluidStream().get(xx+xf+x, zz+zf+z));

                    for(i = 0; i < he; i++)
                    {
                        h.set(xx+xf, i, zz+zf, getComplex().getTerrainStream().get(xx+xf+x, i, zz+zf+z));
                    }
                }
            }
        });
    }
}
