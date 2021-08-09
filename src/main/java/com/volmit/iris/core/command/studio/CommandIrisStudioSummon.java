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
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.entity.IrisEntity;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisStudioSummon extends MortarCommand {
    public CommandIrisStudioSummon() {
        super("summon", "spawnmob");
        setDescription("Spawn an Iris entity");
        requiresPermission(Iris.perm.studio);
        setCategory("Summon");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisToolbelt.isIrisWorld(sender.player().getWorld())) {
            IrisData data = IrisToolbelt.access(sender.player().getWorld()).getEngine().getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for summons while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getEntityLoader().getPossibleKeys());
            } else if (args.length == 1) {
                list.add(data.getEntityLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (sender.isPlayer()) {
            Player p = sender.player();
            World world = p.getWorld();
            if (!IrisToolbelt.isIrisWorld(world)) {
                sender.sendMessage("You must be in an iris world.");
                return true;
            }

            Engine g = IrisToolbelt.access(world).getEngine();
            if (args.length == 0) {
                for (String i : g.getData().getEntityLoader().getPossibleKeys()) {
                    sender.sendMessage("- " + i);
                }
            } else {
                IrisEntity e = g.getData().getEntityLoader().load(args[0]);

                if (e == null) {
                    sender.sendMessage("Couldnt find entity " + args[0] + ". Use '/iris std summon' to see a list of iris entities.");
                    return true;
                }

                Location vl = sender.player().getLocation().clone().add(0, 3, 0);
                e.spawn(g, vl);
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
