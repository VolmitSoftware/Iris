package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.generator.actuator.IrisBiomeActuator;
import com.volmit.iris.v2.generator.actuator.IrisDecorantActuator;
import com.volmit.iris.v2.generator.actuator.IrisTerrainActuator;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineActuator;
import com.volmit.iris.v2.scaffold.engine.EngineFramework;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public class IrisEngineFramework implements EngineFramework {
    @Getter
    private final Engine engine;

    @Getter
    private final IrisComplex complex;

    @Getter
    private final EngineActuator<BlockData> terrainActuator;

    @Getter
    private final EngineActuator<BlockData> decorantActuator;

    @Getter
    private final EngineActuator<Biome> biomeActuator;

    public IrisEngineFramework(Engine engine)
    {
        this.engine = engine;
        this.complex = new IrisComplex(getEngine());
        terrainActuator = new IrisTerrainActuator(getEngine());
        decorantActuator = new IrisDecorantActuator(getEngine());
        biomeActuator = new IrisBiomeActuator(getEngine());
    }
}
