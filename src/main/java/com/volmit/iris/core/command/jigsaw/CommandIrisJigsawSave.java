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
import com.volmit.iris.IrisSettings;
import com.volmit.iris.core.edit.JigsawEditor;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisJigsawSave extends MortarCommand {
    public CommandIrisJigsawSave() {
        super("save", "s");
        requiresPermission(Iris.perm);
        setCategory("Jigsaw");
        setDescription("Save a currently open piece");
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

        JigsawEditor editor = JigsawEditor.editors.get(sender.player());

        if (editor == null) {
            sender.sendMessage("You don't have any pieces open to save!");
            return true;
        }

        editor.close();
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
