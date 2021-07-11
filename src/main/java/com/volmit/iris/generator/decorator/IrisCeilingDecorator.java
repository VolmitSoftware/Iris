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
                if(height >= 0 || height < getEngine().getHeight())
                {
                    data.set(x, height, z, decorator.getBlockData100(biome, getRng(), realX, realZ, getData()));
                }
            }

            else
            {
                int stack = decorator.getHeight(getRng().nextParallelRNG(Cache.key(realX, realZ)), realX, realZ, getData());
                stack = Math.min(max + 1, stack);

                BlockData top = decorator.getBlockDataForTop(biome, getRng(), realX, realZ, getData());
                BlockData fill = decorator.getBlockData100(biome, getRng(), realX, realZ, getData());

                for(int i = 0; i < stack; i++)
                {
                    if(height - i < 0 || height - i > getEngine().getHeight())
                    {
                        continue;
                    }

                    double threshold = (((double)i) / (double)(stack - 1));
                    data.set(x, height - i, z, threshold >= decorator.getTopThreshold() ? top : fill);
                }
            }
        }
    }
}
