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
    public void onActuate(int x, int z, Hunk<BlockData> h) {
        int i,zf, depth, realX, realZ,hf, he;
        IrisBiome biome;
        KList<BlockData> blocks;

        for(int xf = 0; xf < h.getWidth(); xf++)
        {
            for(zf = 0; zf < h.getDepth(); zf++)
            {
                realX = xf + x;
                realZ = zf + z;
                he = (int) Math.round(Math.min(h.getHeight(), getComplex().getHeightStream().get(realX, realZ)));
                hf = (int) Math.round(Math.max(Math.min(h.getHeight(), getDimension().getFluidHeight()), he));
                biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                blocks = null;

                for(i = hf; i >= 0; i--)
                {
                    if(i > he  && i <= hf)
                    {
                        h.set(xf, i, zf, getComplex().getFluidStream().get(realX, +realZ));
                        continue;
                    }

                    if(i <= he)
                    {
                        depth = he - i;
                        if(blocks == null)
                        {
                            blocks = biome.generateLayers(realX, realZ, rng, (int)he, (int)he, getData());
                        }

                        if(blocks.hasIndex(depth))
                        {
                            h.set(xf, i, zf, blocks.get(depth));
                            continue;
                        }

                        h.set(xf, i, zf, getComplex().getRockStream().get(realX, realZ));
                    }
                }
            }
        }
    }
}
