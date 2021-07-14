package com.volmit.iris.scaffold.engine;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.data.DataProvider;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineFramework extends DataProvider {
    Engine getEngine();

    IrisComplex getComplex();

    EngineParallaxManager getEngineParallax();

    default IrisDataManager getData() {
        return getComplex().getData();
    }

    default void recycle() {
        getEngine().getParallax().cleanup();
        getData().getObjectLoader().clean();
    }

    EngineActuator<BlockData> getTerrainActuator();

    EngineActuator<BlockData> getDecorantActuator();

    EngineActuator<Biome> getBiomeActuator();

    EngineModifier<BlockData> getCaveModifier();

    EngineModifier<BlockData> getRavineModifier();

    EngineModifier<BlockData> getDepositModifier();

    EngineModifier<BlockData> getPostModifier();

    void close();
}
