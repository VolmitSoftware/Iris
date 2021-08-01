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
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.data.Cuboid.CuboidDirection;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CommandIrisObjectXAY extends MortarCommand {
    public CommandIrisObjectXAY() {
        super("x&y");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Auto select up, down and out");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

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

        Location[] b = WandManager.getCuboid(p.getInventory().getItemInMainHand());
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Location a1x = b[0].clone();
        Location a2x = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);
        Cuboid cursorx = new Cuboid(a1, a2);

        while (!cursor.containsOnly(Material.AIR)) {
            a1.add(new Vector(0, 1, 0));
            a2.add(new Vector(0, 1, 0));
            cursor = new Cuboid(a1, a2);
        }

        a1.add(new Vector(0, -1, 0));
        a2.add(new Vector(0, -1, 0));

        while (!cursorx.containsOnly(Material.AIR)) {
            a1x.add(new Vector(0, -1, 0));
            a2x.add(new Vector(0, -1, 0));
            cursorx = new Cuboid(a1x, a2x);
        }

        a1x.add(new Vector(0, 1, 0));
        a2x.add(new Vector(0, 1, 0));
        b[0] = a1;
        b[1] = a2x;
        cursor = new Cuboid(b[0], b[1]);
        cursor = cursor.contract(CuboidDirection.North);
        cursor = cursor.contract(CuboidDirection.South);
        cursor = cursor.contract(CuboidDirection.East);
        cursor = cursor.contract(CuboidDirection.West);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        p.getInventory().setItemInMainHand(WandManager.createWand(b[0], b[1]));
        p.updateInventory();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[subcommand]";
    }
}
