/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;

public class CommandLocate extends MortarCommand implements Listener {
    final CommandLocate instance;

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (IrisToolbelt.isIrisWorld(event.getPlayer().getWorld())) {

            // Make sure the command starts with /locate and does not locate stronghold
            if (event.getMessage().contains("/locate") && event.getMessage().contains("stronghold")) {
                return;
            }
            if (event.getMessage().contains("/locate")) {
                event.setCancelled(true); // Cancel the vanilla command process
                String command = event.getMessage().replace("/locate", "ir std goto");
                Bukkit.dispatchCommand(event.getPlayer(), command);
            }
        }
    }

    public CommandLocate() {
        super("locate");
        requiresPermission(Iris.perm);
        this.instance = this;
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        Bukkit.dispatchCommand(sender, "/ir std goto " + Arrays.toString(args));
        return true;
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "[biome/region/structure]";
    }
}
