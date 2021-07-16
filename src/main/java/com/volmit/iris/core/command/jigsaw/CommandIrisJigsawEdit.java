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
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.edit.JigsawEditor;
import com.volmit.iris.engine.object.IrisJigsawPiece;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

import java.io.File;

public class CommandIrisJigsawEdit extends MortarCommand {
    public CommandIrisJigsawEdit() {
        super("edit", "e", "*");
        requiresPermission(Iris.perm);
        setCategory("Jigsaw");
        setDescription("Edit an existing Jigsaw piece");
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

        if (args.length < 1) {
            sender.sendMessage(getArgsUsage());
            return true;
        }

        IrisJigsawPiece piece = IrisDataManager.loadAnyJigsawPiece(args[0]);

        if (piece != null) {
            File dest = piece.getLoadFile();
            new JigsawEditor(sender.player(), piece, IrisDataManager.loadAnyObject(piece.getObject()), dest);
            return true;
        }

        sender.sendMessage("Failed to find existing jigsaw piece: " + args[0]);

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name>";
    }
}
