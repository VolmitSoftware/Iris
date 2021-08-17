package com.volmit.iris.core.service;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LocateSVC implements IrisService {

    @EventHandler
    public void on(final PlayerCommandPreprocessEvent event) {
        if (IrisToolbelt.isIrisWorld(event.getPlayer().getWorld())) {
            VolmitSender sender = new VolmitSender(event.getPlayer());
            sender.sendMessage(C.YELLOW + "You cannot locate structures in Iris worlds through vanilla commands");
            sender.sendMessage("You can use:");
            // TODO: Convert this to have the correct command prefix
            Bukkit.dispatchCommand(event.getPlayer(), "/ird studio find");
        }
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }
}
