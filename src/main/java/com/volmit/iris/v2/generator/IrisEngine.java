package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineFramework;
import com.volmit.iris.v2.scaffold.engine.EngineTarget;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

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
        this.target = target;
        this.framework = new IrisEngineFramework(this);
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes) {
        getFramework().getTerrainActuator().actuate(x, z, blocks);
        getFramework().getDecorantActuator().actuate(x, z, blocks);
        getFramework().getBiomeActuator().actuate(x, z, biomes);
    }
}
