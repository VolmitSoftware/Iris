package com.volmit.iris.generator.actuator;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.scaffold.hunk.Hunk;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class IrisTerrainActuator extends EngineAssignedActuator<BlockData>
{
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    private static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
    @Getter
    private final RNG rng;
    private final boolean hasUnder;

    public IrisTerrainActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getWorld().getSeed());
        hasUnder = getDimension().getUndercarriage() != null && !getDimension().getUndercarriage().getGenerator().isFlat();
    }

    @Override
    public void onActuate(int x, int z, Hunk<BlockData> h) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int i, zf, depth, realX, realZ, hf, he, b, fdepth;
        IrisBiome biome;
        KList<BlockData> blocks, fblocks;

        for(int xf = 0; xf < h.getWidth(); xf++)
        {
            for(zf = 0; zf < h.getDepth(); zf++)
            {
                realX = (int) modX(xf + x);
                realZ = (int) modZ(zf + z);
                b = hasUnder ? (int) Math.round(getDimension().getUndercarriage().get(rng, realX, realZ)) : 0;
                he = (int) Math.round(Math.min(h.getHeight(), getComplex().getHeightStream().get(realX, realZ)));
                hf = (int) Math.round(Math.max(Math.min(h.getHeight(), getDimension().getFluidHeight()), he));
                biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                blocks = null;
                fblocks = null;

                if(hf < b)
                {
                    continue;
                }

                for(i = hf; i >= b; i--) {
                    if (i >= h.getHeight())
                    {
                        continue;
                    }

                    if(i == b)
                    {
                        if(getDimension().isBedrock())
                        {
                            h.set(xf, i, zf, BEDROCK);
                            continue;
                        }
                    }

                    if(getDimension().isCarved(realX, i, realZ, rng, he))
                    {
                        continue;
                    }

                    if(i > he  && i <= hf)
                    {
                        fdepth = hf - i;

                        if(fblocks == null)
                        {
                            fblocks = biome.generateSeaLayers(realX, realZ, rng, hf - he, getData());
                        }

                        if(fblocks.hasIndex(fdepth))
                        {
                            h.set(xf, i, zf, fblocks.get(fdepth));
                            continue;
                        }

                        h.set(xf, i, zf, getComplex().getFluidStream().get(realX, +realZ));
                        continue;
                    }

                    if(i <= he)
                    {
                        depth = he - i;
                        if(blocks == null)
                        {
                            blocks = biome.generateLayers(realX, realZ, rng, (int)he, (int)he, getData(), getComplex());
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

        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }
}
