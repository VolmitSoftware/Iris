package com.volmit.iris.core.safeguard.handler;

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.safeguard.IrisSafeguard;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class onCommandWarning implements Listener {
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (IrisSettings.get().getSafeguard().userUnstableWarning && IrisSafeguard.instance.unstablemode) {
            String command = event.getMessage();
            Player player = event.getPlayer();
            if (command.startsWith("/iris")) {
                VolmitSender sender = new VolmitSender(player);
                boolean perm = sender.hasPermission("iris.all") || sender.isOp();
                if (perm) {
                    sender.sendMessage(C.DARK_GRAY + "[" + C.RED + "!" + C.DARK_GRAY+ "]" + C.DARK_RED + "Iris is running unstably! Please resolve this.");
                }
            }
        }
    }
}
