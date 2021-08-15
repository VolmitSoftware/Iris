package com.volmit.iris.util.decree.handlers.context;

import com.volmit.iris.util.decree.DecreeContextHandler;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;

public class WorldContextHandler implements DecreeContextHandler<World> {
    public Class<World> getType(){return World.class;}

    public World handle(VolmitSender sender)
    {
        return sender.isPlayer() ? sender.player().getWorld() : null;
    }
}
