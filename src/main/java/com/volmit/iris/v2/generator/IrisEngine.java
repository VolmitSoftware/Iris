package com.volmit.iris.v2.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.util.J;
import com.volmit.iris.util.M;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineFramework;
import com.volmit.iris.v2.scaffold.engine.EngineTarget;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_16_R2.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.Iterator;

public class IrisEngine implements Engine
{
    @Getter
    private final EngineTarget target;

    @Getter
    private final EngineFramework framework;

    @Setter
    @Getter
    private volatile int parallelism;

    public IrisEngine(EngineTarget target)
    {
        Iris.info("Initializing Engine: " + target.getWorld().getName() + "/" + target.getDimension().getLoadKey() + " (" + target.getHeight() + " height)");
        this.target = target;
        this.framework = new IrisEngineFramework(this);
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes) {
        MultiBurst.burst.burst(
            () -> getFramework().getEngineParallax().generateParallaxArea(x, z),
            () -> Hunk.computeDual2D(getParallelism(), blocks, biomes, (xx,yy,zz,ha,hb) -> {
                getFramework().getTerrainActuator().actuate(x+xx, z+zz, ha);
                getFramework().getDecorantActuator().actuate(x+xx, z+zz, ha);
                getFramework().getBiomeActuator().actuate(x+xx, z+zz, hb);
            })
        );

        blocks.compute2D(getParallelism(), (xx,yy,zz,ha) -> {
            getFramework().getEngineParallax().insertParallax(x, z, ha);
        });

        if(M.r(0.1))
        {
            MultiBurst.burst.lazy(() -> {
                getParallax().cleanup();
                getData().getObjectLoader().clean();
            });
        }
    }
}
