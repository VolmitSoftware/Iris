package com.volmit.iris.generator.actuator;

import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.hunk.view.BiomeGridHunkView;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import org.bukkit.block.Biome;

public class IrisBiomeActuator extends EngineAssignedActuator<Biome> {
    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
    }

    @Override
    public void onActuate(int x, int z, Hunk<Biome> h) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int zf, hh;
        BurstExecutor burst = MultiBurst.burst.burst(h.getWidth() * h.getDepth());

        for (int xf = 0; xf < h.getWidth(); xf++) {
            for (zf = 0; zf < h.getDepth(); zf++) {
                int xxf = xf;
                int zzf = zf;

                burst.queue(() -> {
                    IrisBiome ib = getComplex().getTrueBiomeStream().get(modX(xxf + x), modZ(zzf + z));

                    if(ib.isCustom())
                    {
                        try
                        {
                            Object biomeBase = INMS.get().getCustomBiomeBaseFor(getDimension().getLoadKey()+":"+ib.getCustom().getId());
                            ((BiomeGridHunkView)h).forceBiomeBaseInto(x, 0, z, biomeBase);

                            for (int i = 0; i < h.getHeight(); i++) {
                                ((BiomeGridHunkView)h).forceBiomeBaseInto(xxf, i, zzf, biomeBase);
                            }
                        }

                        catch(Throwable e)
                        {
                            e.printStackTrace();
                            Biome v = ib.getSkyBiome(RNG.r, x, 0, z);
                            for (int i = 0; i < h.getHeight(); i++) {
                                h.set(xxf, i, zzf, v);
                            }
                        }
                    }

                    else
                    {
                        Biome v = ib.getSkyBiome(RNG.r, x, 0, z);
                        for (int i = 0; i < h.getHeight(); i++) {
                            h.set(xxf, i, zzf, v);
                        }
                    }
                });
            }
        }

        burst.complete();

        getEngine().getMetrics().getBiome().put(p.getMilliseconds());
    }
}
