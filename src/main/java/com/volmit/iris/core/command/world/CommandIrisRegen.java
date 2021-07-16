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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.MortarSender;

public class CommandIrisRegen extends MortarCommand {
    public CommandIrisRegen() {
        super("regen");
        setDescription("Regenerate chunks around you (iris worlds only)");
        requiresPermission(Iris.perm.studio);
        setCategory("Regen");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        sender.sendMessage("Iris' /regen command is currently disabled due to maintenance. Apologies.");
        return true;
		/* This is commented yes
		try
		{
			if(args.length == 0)
			{
				IrisWorlds.access(sender.player().getWorld()).regenerate(
						sender.player().getLocation().getChunk().getX(),
						sender.player().getLocation().getChunk().getZ());
				sender.sendMessage("Regenerated your current chunk");
			}

			else
			{
				try
				{
					int vx = sender.player().getLocation().getChunk().getX();
					int vz = sender.player().getLocation().getChunk().getZ();
					int rad = Integer.valueOf(args[0]);
					int m = (int) Math.pow(rad, 2);
					new Spiraler(rad, rad*2, (x,z) -> {
						IrisWorlds.access(sender.player().getWorld()).regenerate(
								vx + x,
								vz + z);
					}).drain();

					sender.sendMessage("Regenerated " + m + " chunks");
				}

				catch(NumberFormatException e)
				{
					sender.sendMessage(args[0] + " is not a number.");
				}
			}
		}

		catch(Throwable e1)
		{
			sender.sendMessage("You must be in a regen-capable iris world!");
		}

		return true;
		*/
    }

    @Override
    protected String getArgsUsage() {
        return "[size]";
    }
}
