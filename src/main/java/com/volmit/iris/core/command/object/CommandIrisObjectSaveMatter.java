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
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;

public class CommandIrisObjectSaveMatter extends MortarCommand {
    public CommandIrisObjectSaveMatter() {
        super("msave");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Save an object");
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

        if (args.length < 2) {
            sender.sendMessage("/iris o save <project> <object>");
            sender.sendMessage("I.e. /iris o save overworld some-tree/tree1");
            return true;
        }

        try {
            boolean overwrite = false;

            for (String i : args) {
                if (i.equals("-o")) {
                    overwrite = true;
                    break;
                }
            }

            Player p = sender.player();
            ItemStack wand = p.getInventory().getItemInMainHand();
            Matter o = WandManager.createMatterSchem(p, wand);
            File file = Iris.proj.getWorkspaceFile(args[0], "objects", args[1] + ".iob");

            if (file.exists()) {
                if (!overwrite) {
                    sender.sendMessage("File Exists. Overwrite by adding -o");
                    return true;
                }
            }

            o.write(file);
            sender.sendMessage("Saved " + args[1]);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
        } catch (Throwable e) {
            Iris.reportError(e);
            sender.sendMessage("Failed to save " + args[1] + ". Are you holding your wand?");

            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[project] [name]";
    }
}
