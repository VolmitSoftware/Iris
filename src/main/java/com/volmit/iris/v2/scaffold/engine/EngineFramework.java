package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.util.M;
import com.volmit.iris.v2.generator.modifier.IrisCaveModifier;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
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

    default void recycle()
    {
        if(M.r(0.1))
        {
            MultiBurst.burst.lazy(() -> {
                getEngine().getParallax().cleanup();
                getData().getObjectLoader().clean();
            });
        }
    }

    public EngineActuator<BlockData> getTerrainActuator();

    public EngineActuator<BlockData> getDecorantActuator();

    public EngineActuator<Biome> getBiomeActuator();

    public EngineModifier<BlockData> getCaveModifier();

    public EngineModifier<BlockData> getRavineModifier();

    public EngineModifier<BlockData> getDepositModifier();

    public EngineModifier<BlockData> getPostModifier();
}
