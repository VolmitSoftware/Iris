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

package com.volmit.iris.core.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisStudioClose extends MortarCommand {
    public CommandIrisStudioClose() {
        super("close", "x");
        requiresPermission(Iris.perm.studio);
        setDescription("Close the existing dimension");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (!Iris.proj.isProjectOpen()) {
            sender.sendMessage("No open projects.");
            return true;
        }

        if (sender.isPlayer()) {
            World f = null;

            for (World i : Bukkit.getWorlds()) {
                if (i.getWorldFolder().getAbsolutePath().equals(Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getWorldFolder().getAbsolutePath())) {
                    continue;
                }

                f = i;
                break;
            }

            if (f == null) {
                for (Player i : Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getPlayers()) {
                    i.kickPlayer("Project Closing, No other world to put you in. Rejoin Please!");
                }
            } else {
                for (Player i : Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getPlayers()) {
                    i.teleport(f.getSpawnLocation());
                }
            }
        }

        Iris.proj.close();
        sender.sendMessage("Projects Closed & Caches Cleared!");
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
