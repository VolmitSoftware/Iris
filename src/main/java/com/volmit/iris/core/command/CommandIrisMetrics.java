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

package com.volmit.iris.core.command;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisMetrics extends MortarCommand {
    public CommandIrisMetrics() {
        super("metrics", "stats", "mt");
        setDescription("Get timings for this world");
        requiresPermission(Iris.perm.studio);
        setCategory("Metrics");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (sender.isPlayer()) {
            Player p = sender.player();
            World world = p.getWorld();
            if (!IrisToolbelt.isIrisWorld(world)) {
                sender.sendMessage("You must be in an iris world.");
                return true;
            }

            Engine g = IrisToolbelt.access(world).getEngine();

            try {
                g.printMetrics(sender);
            } catch (Throwable e) {
                Iris.reportError(e);
                sender.sendMessage("You must be in an iris world.");
            }

            return true;
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
