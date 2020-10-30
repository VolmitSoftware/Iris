package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.util.RNG;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData>
{
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;

    public IrisDecorantActuator(Engine engine) {
        super(engine, "Decorant");
        this.rng = new RNG(engine.getTarget().getWorld().getSeed());
    }

    private boolean shouldDecorate()
    {
        return getEngine().getDimension().isDecorate();
    }

    private boolean shouldRayDecorate()
    {
        return getEngine().getDimension().isCarving() || getEngine().getDimension().isCaves() || getEngine().getDimension().isVanillaCaves() || getEngine().getDimension().isRavines();
    }

    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output) {
        if(!shouldDecorate())
        {
            return;
        }

        if(shouldRayDecorate())
        {
            output.iterateSurfaces2D(getParallelism(), PREDICATE_SOLID, (hunkRelativeX, hunkRelativeZ, hunkOffsetX, hunkOffsetZ, top, bottom, lastBottom, h) ->  decorateLayer(x, z, hunkRelativeX, hunkRelativeZ, hunkOffsetX, hunkOffsetZ,top,bottom,lastBottom,h));
        }

        else
        {
            output.compute2D(getParallelism(), (xx, yy, zz, h) -> {
                int he;
                for(int i = 0; i < h.getWidth(); i++)
                {
                    for(int j = 0; j < h.getDepth(); j++)
                    {
                        he = getComplex().getHeightFluidStream().get(x + xx+i, z + zz+j).intValue();
                        decorateLayer(x, z, i, j, xx, zz, he, 0, getEngine().getHeight(), h);
                    }
                }
            });
        }
    }

    private void decorateLayer(int x, int z, int hunkRelativeX, int hunkRelativeZ, int hunkOffsetX, int hunkOffsetZ, int top, int bottom,int  lastBottom, Hunk<BlockData> h)
    {
        int realX = x + hunkOffsetX + hunkRelativeX;
        int realZ = z + hunkOffsetZ + hunkRelativeZ;
        IrisBiome b = getComplex().getTrueBiomeStream().get(realX, realZ);

        if(b.getDecorators().isEmpty())
        {
            return;
        }

        RNG g = rng.nextParallelRNG(realX >> 4).nextParallelRNG(realZ >> 4);
        boolean surface = lastBottom == -1;
        int floor = top + 1;
        int ceiling = lastBottom == -1 ? floor < getDimension().getFluidHeight() ? getDimension().getFluidHeight() : getEngine().getHeight() : lastBottom - 1;
        int height = ceiling - floor;

        if(height < 2)
        {
            return;
        }

        IrisDecorator deco = getComplex().getTerrainSurfaceDecoration().get(realX, realZ);

        if(deco != null)
        {
            if(deco.isStacking())
            {
                int stack = Math.min(g.i(deco.getStackMin(), deco.getStackMax()), height);

                for(int i = 0; i < stack; i++)
                {
                    h.set(hunkRelativeX, i + floor, hunkRelativeZ, deco.getBlockData100(b, rng, realX - i, realZ + i, getData()));
                }

                if(deco.getTopPalette().isNotEmpty())
                {
                    h.set(hunkRelativeX, stack + floor - 1, hunkRelativeZ, deco.getBlockDataForTop(b, rng, realX - stack, realZ + stack, getData()));
                }
            }

            else
            {
                h.set(hunkRelativeX, floor, hunkRelativeZ, deco.getBlockData100(b, rng, realX, realZ, getData()));
            }
        }

        if(!surface)
        {
            IrisDecorator cdeco = getComplex().getTerrainCeilingDecoration().get(realX, realZ);

            if(cdeco != null)
            {
                if(cdeco.isStacking())
                {
                    int stack = Math.min(g.i(cdeco.getStackMin(), cdeco.getStackMax()), height);

                    for(int i = 0; i < stack; i++)
                    {
                        h.set(hunkRelativeX, -i + ceiling, hunkRelativeZ, cdeco.getBlockData100(b, rng, realX - i, realZ + i, getData()));
                    }

                    if(cdeco.getTopPalette().isNotEmpty())
                    {
                        h.set(hunkRelativeX, -stack + ceiling - 1, hunkRelativeZ, cdeco.getBlockDataForTop(b, rng, realX - stack, realZ + stack, getData()));
                    }
                }

                else
                {
                    h.set(hunkRelativeX, ceiling, hunkRelativeZ, cdeco.getBlockData100(b, rng, realX, realZ, getData()));
                }
            }
        }
    }
}
