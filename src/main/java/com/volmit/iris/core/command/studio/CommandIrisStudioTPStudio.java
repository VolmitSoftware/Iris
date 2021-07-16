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
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.GameMode;

public class CommandIrisStudioTPStudio extends MortarCommand {
    public CommandIrisStudioTPStudio() {
        super("tps", "stp", "tpstudio", "tp");
        requiresPermission(Iris.perm.studio);
        setDescription("Go to the spawn of the currently open studio world.");
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

        if (!sender.isPlayer()) {
            sender.sendMessage("Cannot be ran by console.");
            return true;
        }

        if (!Iris.proj.isProjectOpen()) {
            sender.sendMessage("There is not a studio currently loaded.");
            return true;
        }

        try {
            sender.sendMessage("Teleporting you to the active studio world.");
            sender.player().teleport(Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getSpawnLocation());
            sender.player().setGameMode(GameMode.SPECTATOR);
        } catch (Throwable e) {
            Iris.reportError(e);
            sender.sendMessage("Failed to teleport to the studio world. Try re-opening the project.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
