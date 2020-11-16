package com.volmit.iris.generator.actuator;

import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.PrecisionStopwatch;
import org.bukkit.block.Biome;

public class IrisBiomeActuator extends EngineAssignedActuator<Biome>
{
    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
    }

    @Override
    public void onActuate(int x, int z, Hunk<Biome> h) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int i,zf;
        Biome v;

        for(int xf = 0; xf < h.getWidth(); xf++)
        {
            for(zf = 0; zf < h.getDepth(); zf++)
            {
                v = getComplex().getTrueBiomeStream().get(modX(xf+x), modZ(zf+z)).getDerivative();

                for(i = 0; i < h.getHeight(); i++)
                {
                    h.set(xf, i, zf, v);
                }
            }
        }
        getEngine().getMetrics().getBiome().put(p.getMilliseconds());
    }
}
