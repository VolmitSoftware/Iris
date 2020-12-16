package com.volmit.iris.generator.decorator;

import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedComponent;
import com.volmit.iris.scaffold.engine.EngineDecorator;
import lombok.Getter;

public abstract class IrisEngineDecorator extends EngineAssignedComponent implements EngineDecorator {

    @Getter
    private final RNG rng;

    @Getter
    private final DecorationPart part;

    public IrisEngineDecorator(Engine engine, String name, DecorationPart part) {
        super(engine, name + " Decorator");
        this.part = part;
        this.rng = new RNG(getSeed() + 29356788 - (part.ordinal() * 10439677));
    }

    protected IrisDecorator getDecorator(IrisBiome biome, double realX, double realZ)
    {
        KList<IrisDecorator> v = new KList<>();
        RNG rng = new RNG(Cache.key((int)realX, (int)realZ));

        for(IrisDecorator i : biome.getDecorators())
        {
            if(i.getPartOf().equals(part) && i.getBlockData(biome, this.rng, realX, realZ, getData()) != null)
            {
                v.add(i);
            }
        }

        if(v.isNotEmpty()) {
            return v.get(rng.nextInt(v.size()));
        }

        return null;
    }
}
