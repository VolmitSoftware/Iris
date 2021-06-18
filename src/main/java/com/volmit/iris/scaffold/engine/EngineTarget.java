package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.parallax.ParallaxWorld;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import lombok.Data;
import org.bukkit.World;

import java.io.File;

@Data
public class EngineTarget
{
    private final MultiBurst burster;
    private final IrisDimension dimension;
    private final World world;
    private final int height;
    private final IrisDataManager data;
    private final ParallaxWorld parallaxWorld;
    private final boolean inverted;

    public EngineTarget(World world, IrisDimension dimension, IrisDataManager data, int height, boolean inverted, int threads)
    {
        this.world = world;
        this.height = height;
        this.dimension = dimension;
        this.data = data;
        // TODO: WARNING HEIGHT
        this.parallaxWorld = new ParallaxWorld(256, new File(world.getWorldFolder(), "iris/" + dimension.getLoadKey() + "/parallax"));
        this.inverted = inverted;
        this.burster = new MultiBurst(threads);
    }

    public EngineTarget(World world, IrisDimension dimension, IrisDataManager data, int height, int threads)
    {
        this(world, dimension, data, height, false, threads);
    }
}
