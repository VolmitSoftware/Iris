package com.volmit.iris.generator.decorator;

import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.block.data.BlockData;

public class IrisCeilingDecorator extends IrisEngineDecorator
{
    public IrisCeilingDecorator(Engine engine) {
        super(engine, "Ceiling", DecorationPart.CEILING);
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        IrisDecorator decorator = getDecorator(biome, realX, realZ);

        if(decorator != null)
        {
            if(!decorator.isStacking())
            {
                data.set(x, height-1, z, decorator.getBlockData100(biome, getRng(), realX, realZ, getData()));
            }

            else
            {
                int stack = Math.min(getRng().nextParallelRNG(Cache.key(realX, realZ)).i(decorator.getStackMin(), decorator.getStackMax()), max);

                for(int i = 0; i < stack; i++)
                {
                    data.set(x, height-1-i, z, i == stack-1 ? decorator.getBlockDataForTop(biome, getRng(), realX+i, realZ-i, getData()) : decorator.getBlockData100(biome, getRng(), realX+i, realZ-i, getData()));
                }
            }
        }
    }
}
