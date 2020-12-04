package com.volmit.iris.scaffold.engine;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.M;
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
        if(M.r(0.1))
        {
            synchronized (getEngine().getParallax())
            {
                getEngine().getParallax().cleanup();
            }

            getData().getObjectLoader().clean();
        }
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
