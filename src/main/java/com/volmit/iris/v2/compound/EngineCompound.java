package com.volmit.iris.v2.compound;

import com.volmit.iris.v2.scaffold.engine.Engine;
import org.bukkit.World;

public interface EngineCompound
{
    public World getWorld();

    public int getSize();

    public Engine getEngine(int index);
}
