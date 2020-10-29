package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.generator.IrisEngine;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineFramework
{
    public IrisEngine getEngine();

    public IrisComplex getComplex();

    public EngineActuator<BlockData> getTerrainActuator();

    public EngineActuator<BlockData> getDecorantActuator();

    public EngineActuator<Biome> getBiomeActuator();
}
