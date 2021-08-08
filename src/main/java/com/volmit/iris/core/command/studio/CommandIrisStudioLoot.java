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
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.loot.IrisLootTable;
import com.volmit.iris.engine.object.meta.InventorySlotType;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.O;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class CommandIrisStudioLoot extends MortarCommand {
    public CommandIrisStudioLoot() {
        super("loot");
        setDescription("Show loot if a chest were right here");
        requiresPermission(Iris.perm.studio);
        setCategory("Loot");
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

        if (sender.isPlayer()) {
            Player p = sender.player();
            Engine prov = IrisToolbelt.access(sender.player().getWorld()).getEngine();

            if (!Iris.proj.isProjectOpen()) {
                sender.sendMessage("You can only use /iris studio loot in a studio world of iris.");
                return true;
            }

            KList<IrisLootTable> tables = prov.getLootTables(RNG.r, p.getLocation().getBlock());
            Inventory inv = Bukkit.createInventory(null, 27 * 2);

            try {
                Iris.proj.getActiveProject().getActiveProvider().getEngine().addItems(true, inv, RNG.r, tables, InventorySlotType.STORAGE, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ(), 1);
            } catch (Throwable e) {
                Iris.reportError(e);
                sender.sendMessage("You can only use /iris loot in a studio world of iris.");
                return true;
            }

            p.openInventory(inv);

            for (IrisLootTable i : tables) {
                sender.sendMessage("- " + i.getName());
            }

            boolean ffast = false;
            boolean fadd = false;

            for (String i : args) {
                if (i.equals("--fast")) {
                    ffast = true;
                }

                if (i.equals("--add")) {
                    fadd = true;
                }
            }

            boolean fast = ffast;
            boolean add = fadd;
            O<Integer> ta = new O<>();
            ta.set(-1);

            ta.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
            {
                if (!p.getOpenInventory().getType().equals(InventoryType.CHEST)) {
                    Bukkit.getScheduler().cancelTask(ta.get());
                    return;
                }

                if (!add) {
                    inv.clear();
                }

                Iris.proj.getActiveProject().getActiveProvider().getEngine().addItems(true, inv, new RNG(RNG.r.imax()), tables, InventorySlotType.STORAGE, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ(), 1);
            }, 0, fast ? 5 : 35));

            return true;
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[width]";
    }
}
