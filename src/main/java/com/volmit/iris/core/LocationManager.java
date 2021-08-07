package com.volmit.iris.core;

import com.volmit.iris.core.tools.IrisToolbelt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LocationManager implements Listener {
    @EventHandler
    public void on(PlayerCommandPreprocessEvent e){

        if (!e.getMessage().contains("locate")){
            return;
        }

        if (!IrisToolbelt.isIrisWorld(e.getPlayer().getWorld())){
            return;
        }

        if (IrisToolbelt.access(e.getPlayer().getWorld()).getCompound().getRootDimension().getLocations().)
    }
}
