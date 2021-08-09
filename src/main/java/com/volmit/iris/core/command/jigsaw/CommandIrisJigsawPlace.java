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

package com.volmit.iris.core.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructure;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

public class CommandIrisJigsawPlace extends MortarCommand {
    public CommandIrisJigsawPlace() {
        super("place", "paste", "p");
        requiresPermission(Iris.perm);
        setCategory("Jigsaw");
        setDescription("Place a jigsaw structure");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisToolbelt.isIrisWorld(sender.player().getWorld())) {
            IrisData data = IrisToolbelt.access(sender.player().getWorld()).getEngine().getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for jigsaw structures while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getJigsawStructureLoader().getPossibleKeys());
            } else if (args.length == 1) {
                list.add(data.getJigsawStructureLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Jigsaw, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("Ingame only");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("You have to specify a jigsaw structure!");
            return true;
        }

        IrisJigsawStructure str = IrisData.loadAnyJigsawStructure(args[0]);

        if (str != null) {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            PlannedStructure ps = new PlannedStructure(str, new IrisPosition(sender.player().getLocation()), new RNG());
            sender.sendMessage("Generated " + ps.getPieces().size() + " pieces in " + Form.duration(p.getMilliseconds(), 2));
            ps.place(sender.player().getWorld());
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name>";
    }
}
