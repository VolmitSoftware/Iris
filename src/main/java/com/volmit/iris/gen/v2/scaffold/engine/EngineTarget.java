package com.volmit.iris.gen.v2.scaffold.engine;

import com.volmit.iris.gen.v2.scaffold.parallax.ParallaxWorld;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import lombok.Data;
import org.bukkit.World;

import java.io.File;

@Data
public class EngineTarget
{
    private final IrisDimension dimension;
    private final World world;
    private final IrisDataManager data;
    private final ParallaxWorld parallaxWorld;

    public EngineTarget(World world, IrisDimension dimension, IrisDataManager data)
    {
        this.world = world;
        this.dimension = dimension;
        this.data = data;
        this.parallaxWorld = new ParallaxWorld(256, new File(world.getWorldFolder(), "iris/" + dimension.getLoadKey() + "/parallax"));
    }
}
