package com.volmit.iris.scaffold;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class IrisWorldCreator
{
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private long seed = 1337;
    private boolean asyncPrepare = false;

    public IrisWorldCreator()
    {

    }

    public IrisWorldCreator dimension(String loadKey)
    {
        this.dimensionName = loadKey;
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

    public IrisWorldCreator asyncPrepare()
    {
        this.asyncPrepare = true;
        return this;
    }

    public IrisWorldCreator productionMode()
    {
        this.studio = false;
        return this;
    }

    public WorldCreator create()
    {
        EngineCompositeGenerator g =  new EngineCompositeGenerator(dimensionName, !studio);

        return new WorldCreator(name)
                .environment(findEnvironment())
                .generateStructures(true)
                .generator(g).seed(seed);
    }

    public void createAsync(Consumer<WorldCreator> result)
    {
        EngineCompositeGenerator g =  new EngineCompositeGenerator(dimensionName, !studio);
        Environment env = findEnvironment();
        g.prepareSpawnAsync(seed, name, env, 16, (progresss) -> {
            for(Player i : Bukkit.getOnlinePlayers())
            {
                i.sendMessage("Async Prepare 32x32: " + Form.pc(progresss, 2));
            }

        }, () -> {
            J.s(() -> result.accept(new WorldCreator(name)
                    .environment(env)
                    .generateStructures(true)
                    .generator(g).seed(seed)));
        });
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
