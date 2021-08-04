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
import com.volmit.iris.core.edit.JigsawEditor;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

import java.io.File;

public class CommandIrisJigsawNew extends MortarCommand {
    public CommandIrisJigsawNew() {
        super("create", "new", "+", "n");
        requiresPermission(Iris.perm);
        setCategory("Jigsaw");
        setDescription("Create a new jigsaw piece");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

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

        if (args.length != 3) {
            sender.sendMessage(getArgsUsage());
            return true;
        }

        IrisObject object = IrisData.loadAnyObject(args[2]);

        if (object == null) {
            sender.sendMessage("Failed to find existing object: " + args[2]);
            return true;
        }

        File dest = Iris.instance.getDataFile("packs", args[1], "jigsaw-pieces", args[0] + ".json");
        new JigsawEditor(sender.player(), null, object, dest);
        sender.sendMessage("* Right Click blocks to make them connectors");
        sender.sendMessage("* Right Click connectors to orient them");
        sender.sendMessage("* Shift + Right Click connectors to remove them");
        sender.sendMessage("Remember to use /iris jigsaw save");
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name> <project> <object>";
    }
}
