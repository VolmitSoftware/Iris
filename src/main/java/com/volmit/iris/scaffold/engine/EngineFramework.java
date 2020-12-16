package com.volmit.iris.scaffold.engine;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.data.DataProvider;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineFramework extends DataProvider
{
    public Engine getEngine();

    public IrisComplex getComplex();
    
    public EngineParallaxManager getEngineParallax();

    default IrisDataManager getData() {
        return getComplex().getData();
    }

    default void recycle()
    {
        getEngine().getParallax().cleanup();
        getData().getObjectLoader().clean();
    }

    public EngineActuator<BlockData> getTerrainActuator();

    public EngineActuator<BlockData> getDecorantActuator();

    public EngineActuator<Biome> getBiomeActuator();

    public EngineModifier<BlockData> getCaveModifier();

    public EngineModifier<BlockData> getRavineModifier();

    public EngineModifier<BlockData> getDepositModifier();

    public EngineModifier<BlockData> getPostModifier();

    void close();
}
