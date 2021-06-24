package com.volmit.iris.scaffold;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import com.volmit.iris.util.FakeWorld;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;

public class IrisWorldCreator
{
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private long seed = 1337;
    private int maxHeight = 256;
    private int minHeight = 0;

    public IrisWorldCreator()
    {

    }

    public IrisWorldCreator dimension(String loadKey)
    {
        this.dimensionName = loadKey;
        return this;
    }

    public IrisWorldCreator height(int maxHeight)
    {
        this.maxHeight = maxHeight;
        this.minHeight = 0;
        return this;
    }

    public IrisWorldCreator height(int minHeight, int maxHeight)
    {
        this.maxHeight = maxHeight;
        this.minHeight = minHeight;
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
        EngineCompositeGenerator g = new EngineCompositeGenerator(dimensionName, !studio);
        g.initialize(new FakeWorld(name, minHeight, maxHeight, seed, new File(name), findEnvironment()));

        return new WorldCreator(name)
                .environment(findEnvironment())
                .generateStructures(true)
                .generator(g).seed(seed);
    }

    private World.Environment findEnvironment() {
        IrisDimension dim = IrisDataManager.loadAnyDimension(dimensionName);
        if(dim == null || dim.getEnvironment() == null)
        {
            return World.Environment.NORMAL;
        }

        else
        {
            return dim.getEnvironment();
        }
    }
}
