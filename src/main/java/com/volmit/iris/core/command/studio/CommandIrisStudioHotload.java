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
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisStudioHotload extends MortarCommand {
    public CommandIrisStudioHotload() {
        super("hotload", "hot", "h", "reload");
        setDescription("Force a hotload");
        requiresPermission(Iris.perm.studio);
        setCategory("World");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player p = sender.player();
        World world = p.getWorld();

        if (!IrisWorlds.isIrisWorld(world)) {
            sender.sendMessage("You must be in an iris world.");
            return true;
        }

        IrisAccess worldAccess = IrisWorlds.access(world);
        if (worldAccess == null) {
            sender.sendMessage("Could not gain access to the world you are in");
        } else {
            worldAccess.hotload();
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
