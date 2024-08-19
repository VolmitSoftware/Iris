package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.stream.ProceduralStream;
import org.bukkit.Bukkit;

import java.util.function.Function;

@Desc("Represents a stream from the engine")
public enum IrisBukkitStreamType {

    @Desc("Gets the online player count.")
    MAX_ONLINE_PLAYERS((f) -> Bukkit.getMaxPlayers()),

    @Desc("Gets the online player count.")
    ONLINE_PLAYERS((f) -> Bukkit.getOnlinePlayers().size()),

    @Desc("Gets the online player count on the running world.")
    WORLD_PLAYERS((f) -> f.getWorld().getPlayers().size()),;

    private final Function<Engine, Integer> getter;

    IrisBukkitStreamType(Function<Engine, Integer> getter) {
        this.getter = getter;
    }

    public Integer get(Engine engine) {
        return getter.apply(engine);
    }
}
