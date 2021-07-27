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
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.ProjectManager;
import com.volmit.iris.core.WandManager;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CommandIrisObjectPaste extends MortarCommand {
    public CommandIrisObjectPaste() {
        super("paste", "pasta", "place", "p");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Paste an object");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld())) {
            IrisDataManager data = IrisWorlds.access(sender.player().getWorld()).getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for objects while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getObjectLoader().getPossibleKeys());
            } else {
                list.add(data.getObjectLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("Only players can spawn objects with this command");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Please specify the name of of the object want to paste");
            return true;
        }

        Player p = sender.player();
        IrisObject obj = IrisDataManager.loadAnyObject(args[0]);

        if (obj == null || obj.getLoadFile() == null) {

            sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.WORKSPACE_NAME + " folder");
            return true;
        }

        boolean intoWand = false;

        for (String i : args) {
            if (i.equalsIgnoreCase("-edit")) {
                intoWand = true;
                break;
            }
        }

        ItemStack wand = sender.player().getInventory().getItemInMainHand();

        Iris.debug("Loaded object for placement: " + "objects/" + args[0] + ".iob");

        sender.player().getWorld().playSound(sender.player().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
        Location block = sender.player().getTargetBlock(null, 256).getLocation().clone().add(0, 1, 0);

        WandManager.pasteSchematic(obj, block);

        if (intoWand && WandManager.isWand(wand)) {
            wand = WandManager.createWand(block.clone().subtract(obj.getCenter()).add(obj.getW() - 1, obj.getH(), obj.getD() - 1), block.clone().subtract(obj.getCenter()));
            p.getInventory().setItemInMainHand(wand);
            sender.sendMessage("Updated wand for " + "objects/" + args[0] + ".iob");
        } else {
            sender.sendMessage("Placed " + "objects/" + args[0] + ".iob");
        }


        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[name] [-edit]";
    }
}
