package com.volmit.iris.util.mobs;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.service.EngineMobHandlerSVC;
import org.bukkit.entity.EntityType;

import java.util.function.Function;

public interface IrisMobDataHandler {

    Function<EntityType, EngineMobHandlerSVC.Types> getMobType();

    Engine getEngine();

    double getEnergy();


}
