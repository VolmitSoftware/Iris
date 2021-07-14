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

package com.volmit.iris.manager.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.edit.JigsawEditor;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

import java.io.File;

public class CommandIrisJigsawNew extends MortarCommand {
    public CommandIrisJigsawNew() {
        super("create", "new", "+", "n");
        requiresPermission(Iris.perm);
        setCategory("Jigsaw");
        setDescription("Create a new jigsaw piece");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
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

        IrisObject object = IrisDataManager.loadAnyObject(args[2]);

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
