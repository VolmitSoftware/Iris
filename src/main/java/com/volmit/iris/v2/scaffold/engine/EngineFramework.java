package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.generator.IrisEngine;
import com.volmit.iris.v2.generator.actuator.IrisRavineModifier;
import com.volmit.iris.v2.generator.modifier.IrisCaveModifier;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.scaffold.data.DataProvider;

public interface EngineFramework extends DataProvider
{
    public Engine getEngine();

    public IrisComplex getComplex();
    
    public EngineParallax getEngineParallax();

    default IrisDataManager getData() {
        return getComplex().getData();
    }

    public EngineActuator<BlockData> getTerrainActuator();

    public EngineActuator<BlockData> getDecorantActuator();

    public EngineActuator<Biome> getBiomeActuator();

    public IrisCaveModifier getCaveModifier();

    public EngineModifier<BlockData> getRavineModifier();

    public EngineModifier<BlockData> getDepositModifier();
}
