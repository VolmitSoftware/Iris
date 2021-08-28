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

package com.volmit.iris.core.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public class CommandIrisWhatBlock extends MortarCommand {
    public CommandIrisWhatBlock() {
        super("block", "l", "bl");
        setDescription("Get the block data of the block you're looking at.");
        requiresPermission(Iris.perm.studio);
        setCategory("Wut");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (sender.isPlayer()) {
            BlockData bd;
            Player p = sender.player();

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
