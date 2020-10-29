package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.util.RNG;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData>
{
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;

    public IrisDecorantActuator(Engine engine) {
        super(engine);
        this.rng = new RNG(engine.getTarget().getWorld().getSeed());
    }

    @Override
    public void actuate(int x, int z, Hunk<BlockData> output) {
        output.iterateSurfaces2D(getParallelism(), PREDICATE_SOLID, (hunkRelativeX, hunkRelativeZ, hunkOffsetX, hunkOffsetZ, top, bottom, lastBottom, h) ->
        {
            int realX = x + hunkOffsetX + hunkRelativeX;
            int realZ = z + hunkOffsetZ + hunkRelativeZ;
            RNG g = rng.nextParallelRNG(realX).nextParallelRNG(realZ); //TODO: Technically incorrect! Use chunkX & chunkZ
            IrisBiome b = getComplex().getTrueBiomeStream().get(realX, realZ);
            boolean surface = lastBottom == -1;
            int floor = top + 1;
            int ceiling = lastBottom == -1 ? floor < getDimension().getFluidHeight() ? getDimension().getFluidHeight() : output.getHeight() : lastBottom - 1;
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
        });
    }
}
