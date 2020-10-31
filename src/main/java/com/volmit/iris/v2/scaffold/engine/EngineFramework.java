package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.generator.IrisEngine;
import com.volmit.iris.v2.scaffold.data.DataProvider;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.v2.scaffold.parallax.ParallaxWorld;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

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
}
