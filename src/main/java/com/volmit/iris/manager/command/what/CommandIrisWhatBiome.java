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

package com.volmit.iris.manager.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

public class CommandIrisWhatBiome extends MortarCommand {
    public CommandIrisWhatBiome() {
        super("biome", "bi", "b");
        setDescription("Get the biome data you are in.");
        requiresPermission(Iris.perm.studio);
        setCategory("Wut");
        setDescription("What biome am I in");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (sender.isPlayer()) {
            Player p = sender.player();
            World w = p.getWorld();

            try {

                IrisAccess g = IrisWorlds.access(w);
                assert g != null;
                IrisBiome b = g.getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                sender.sendMessage("IBiome: " + b.getLoadKey() + " (" + b.getDerivative().name() + ")");

            } catch (Throwable e) {
                sender.sendMessage("Non-Iris Biome: " + p.getLocation().getBlock().getBiome().name());

                if (p.getLocation().getBlock().getBiome().equals(Biome.CUSTOM)) {
                    try {
                        sender.sendMessage("Data Pack Biome: " + INMS.get().getTrueBiomeBaseKey(p.getLocation()) + " (ID: " + INMS.get().getTrueBiomeBaseId(INMS.get().getTrueBiomeBase(p.getLocation())) + ")");
                    } catch (Throwable ex) {

                    }
                }
            }
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
