package com.volmit.iris.scaffold;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

public class IrisWorldCreator
{
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private long seed = 1337;

    public IrisWorldCreator()
    {

    }

    public IrisWorldCreator dimension(String loadKey)
    {
        this.dimensionName = dimensionName;
        return this;
    }

    public IrisWorldCreator dimension(IrisDimension dim)
    {
        this.dimensionName = dim.getLoadKey();
        return this;
    }

    public IrisWorldCreator name(String name)
    {
        this.name = name;
        return this;
    }

    public IrisWorldCreator seed(long seed)
    {
        this.seed = seed;
        return this;
    }

    public IrisWorldCreator studioMode()
    {
        this.studio = true;
        return this;
    }

    public IrisWorldCreator productionMode()
    {
        this.studio = false;
        return this;
    }

    public WorldCreator create()
    {
        ChunkGenerator g =  new EngineCompositeGenerator(dimensionName, !studio);

        return new WorldCreator(name)
                .environment(findEnvironment())
                .generateStructures(true)
                .generator(g).seed(seed);
    }

    private World.Environment findEnvironment() {
        if(dimensionName == null)
        {
            return World.Environment.NORMAL;
        }

        IrisDimension dim = Iris.globaldata.getDimensionLoader().load(dimensionName);

        if(dim != null)
        {
            return dim.getEnvironment();
        }

        return World.Environment.NORMAL;
    }
}
