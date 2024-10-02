package com.volmit.iris.util.mobs;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.service.EngineMobHandlerSVC;
import org.bukkit.Chunk;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;

public interface IrisMobDataHandler {

    HashMap<Types, Integer> bukkitLimits = null;

     enum Types {
        monsters,
        animals,
        water_animals,
        water_ambient,
        water_underground_creature,
        axolotls,
        ambient
    }

    enum DataType {
        ENERGY_MAX,
        ENERGY_CONSUMPTION,
        ENERGY_ADDITION

    }

    long getIteration();

    Function<EntityType, Types> getMobType();

    Engine getEngine();

    HashSet<Chunk> getChunks();

    HashMap<Types, Integer> bukkitLimits();

    double getEnergy();


}
