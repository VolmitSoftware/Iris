package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomePaletteLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public class IrisTerrainActuator extends EngineAssignedActuator<BlockData>
{
    private static final BlockData AIR = Material.AIR.createBlockData();
    private final RNG rng;

    public IrisTerrainActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getWorld().getSeed());
    }

    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output) {
        output.compute2D(getParallelism(), (xx, yy, zz, h) -> {
            int i,zf, depth, atDepth;
            double he;
            BlockData block;
            IrisBiome biome;

            for(int xf = 0; xf < h.getWidth(); xf++)
            {
                for(zf = 0; zf < h.getDepth(); zf++)
                {
                    he = Math.min(h.getHeight(), getComplex().getHeightFluidStream().get(xx+xf+x, zz+zf+z));
                    biome = getComplex().getTrueBiomeStream().get(xx+xf+x, zz+zf+z);
                    KList<BlockData> blocks = biome.generateLayers(xx+xf+x, zz+zf+z, rng, (int)he, (int)he, getData());

                    for(i = 0; i < he; i++)
                    {
                        depth = ((int)he) - i;

                        if(i > he  && i <= he)
                        {
                            h.set(xx+xf, i, zz+zf, getComplex().getFluidStream().get(xx+xf+x, zz+zf+z));
                            continue;
                        }

                        if(depth < -1)
                        {
                            h.set(xx+xf, i, zz+zf, AIR);
                            continue;
                        }

                        if(blocks.hasIndex(blocks.last() - ((int)he - depth)))
                        {
                            h.set(xx+xf, i, zz+zf, blocks.get(blocks.last() - ((int)he - depth)));
                            continue;
                        }

                        h.set(xx+xf, i, zz+zf, getComplex().getRockStream().get(xx+xf+x, zz+zf+z));
                    }
                }
            }
        });
    }
}
