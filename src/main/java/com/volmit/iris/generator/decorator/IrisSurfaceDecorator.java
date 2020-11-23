package com.volmit.iris.generator.decorator;

import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

public class IrisSurfaceDecorator extends IrisEngineDecorator
{
    public IrisSurfaceDecorator(Engine engine) {
        super(engine, "Surface", DecorationPart.NONE);
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        if(biome.getInferredType().equals(InferredType.SHORE) && height < getDimension().getFluidHeight())
        {
            return;
        }

        BlockData bd, bdx;
        IrisDecorator decorator = getDecorator(biome, realX, realZ);
        bdx = data.get(x, height, z);
        boolean underwater = height < getDimension().getFluidHeight();

        if(decorator != null)
        {
            if(!decorator.isStacking())
            {
                bd = decorator.getBlockData100(biome, getRng(), realX, realZ, getData());

                if(!underwater)
                {
                    if(!canGoOn(bd, bdx))
                    {
                        return;
                    }
                }

                if(bd instanceof Bisected)
                {
                    bd = bd.clone();
                    ((Bisected)bd).setHalf(Bisected.Half.TOP);
                    try
                    {
                        data.set(x, height+2, z, bd);
                    }

                    catch(Throwable e)
                    {

                    }
                    bd = bd.clone();
                    ((Bisected)bd).setHalf(Bisected.Half.BOTTOM);
                }

                data.set(x, height+1, z, bd);

            }

            else {
                if (height < getDimension().getFluidHeight())
                {
                    max = getDimension().getFluidHeight() - height;
                }

                int stack = Math.min(getRng().nextParallelRNG(Cache.key(realX, realZ)).i(decorator.getStackMin(), decorator.getStackMax()), max);

                for(int i = 0; i < stack; i++)
                {
                    bd = i == stack-1 ? decorator.getBlockDataForTop(biome, getRng(), realX+i, realZ-i, getData()) : decorator.getBlockData100(biome, getRng(), realX+i, realZ-i, getData());

                    if(bd == null && i == stack-1)
                    {
                        bd = decorator.getBlockData100(biome, getRng(), realX+i, realZ-i, getData());
                    }

                    if(bd == null)
                    {
                        return;
                    }

                    if(i == 0 && !underwater && !canGoOn(bd, bdx))
                    {
                        return;
                    }

                    data.set(x, height+1+i, z, bd);
                }
            }
        }
    }
}
