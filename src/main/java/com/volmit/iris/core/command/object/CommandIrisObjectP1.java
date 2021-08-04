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

package com.volmit.iris.core.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.WandManager;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CommandIrisObjectP1 extends MortarCommand {
    public CommandIrisObjectP1() {
        super("p1");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Set point 1 to pos (or look)");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("You don't have a wand");
            return true;
        }

        Player p = sender.player();

        if (!WandManager.isHoldingWand(p)) {
            sender.sendMessage("Ready your Wand.");
            return true;
        }

        ItemStack wand = p.getInventory().getItemInMainHand();

        if (WandManager.isWand(wand)) {
            Location[] g = WandManager.getCuboid(wand);
            g[0] = p.getLocation().getBlock().getLocation().clone().add(0, -1, 0);

            if (args.length == 1 && args[0].equals("-l")) {
                // TODO: WARNING HEIGHT
                g[0] = p.getTargetBlock(null, 256).getLocation().clone();
            }

            p.setItemInHand(WandManager.createWand(g[0], g[1]));
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[-l]";
    }
}
