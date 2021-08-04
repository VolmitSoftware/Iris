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
import com.volmit.iris.util.math.Direction;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class CommandIrisObjectShift extends MortarCommand {
    public CommandIrisObjectShift() {
        super(">");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Shift selection based on direction");
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

        int amt = args.length == 1 ? Integer.parseInt(args[0]) : 1;
        Location[] b = WandManager.getCuboid(p.getInventory().getItemInMainHand());
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Direction d = Direction.closest(p.getLocation().getDirection()).reverse();
        a1.add(d.toVector().multiply(amt));
        a2.add(d.toVector().multiply(amt));
        Cuboid cursor = new Cuboid(a1, a2);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        p.getInventory().setItemInMainHand(WandManager.createWand(b[0], b[1]));
        p.updateInventory();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[amt]";
    }
}
