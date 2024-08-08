/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
                    sender.sendMessage(C.DARK_GRAY + "[" + C.RED + "!" + C.DARK_GRAY + "]" + C.DARK_RED + "Iris is running unstably! Please resolve this.");
                }
            }
        }
    }
}
