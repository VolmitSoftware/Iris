package com.volmit.iris.generator.actuator;

import com.volmit.iris.nms.BiomeBaseInjector;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeCustom;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.hunk.view.BiomeGridHunkView;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.TerrainChunk;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

public class IrisBiomeActuator extends EngineAssignedActuator<Biome> {
    private final RNG rng;

    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
        rng = new RNG(engine.getWorld().getSeed() + 243995);
    }

    private boolean injectBiome(Hunk<Biome> h, int x, int y, int z, Object bb)
    {
        try
        {
            if(h instanceof BiomeGridHunkView)
            {
                BiomeGridHunkView hh = (BiomeGridHunkView) h;
                ChunkGenerator.BiomeGrid g = hh.getChunk();
                if(g instanceof TerrainChunk)
                {
                    ((TerrainChunk) g).getBiomeBaseInjector().setBiome(x,y,z,bb);
                    return true;
                }

                else
                {
                    hh.forceBiomeBaseInto(x, y, z, bb);
                    return true;
                }
            }
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }

        return false;
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
                            IrisBiomeCustom custom = ib.getCustomBiome(rng, x, 0, z);
                            Object biomeBase = INMS.get().getCustomBiomeBaseFor(getDimension().getLoadKey()+":"+custom.getId());

                            if(!injectBiome(h, x, 0, z, biomeBase))
                            {
                                throw new RuntimeException("Cant inject biome!");
                            }

                            for (int i = 0; i < h.getHeight(); i++) {
                                injectBiome(h, xxf, i, zzf, biomeBase);
                            }
                        }

                        catch(Throwable e)
                        {
                            e.printStackTrace();
                            Biome v = ib.getSkyBiome(rng, x, 0, z);
                            for (int i = 0; i < h.getHeight(); i++) {
                                h.set(xxf, i, zzf, v);
                            }
                        }
                    }

                    else
                    {
                        Biome v = ib.getSkyBiome(rng, x, 0, z);
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
