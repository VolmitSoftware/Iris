package com.volmit.iris.util.mobs;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.service.EngineMobHandlerSVC;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
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

    Function<EntityType, Types> getMobType();

    Engine getEngine();

    HashMap<Types, Integer> bukkitLimits();

    double getEnergy();


}
