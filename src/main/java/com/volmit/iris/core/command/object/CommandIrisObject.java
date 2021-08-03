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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.Command;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisObject extends MortarCommand {
    @Command
    private CommandIrisObjectWand wand;

    @Command
    private CommandIrisObjectDust dust;

    @Command
    private CommandIrisObjectXPY xpy;

    @Command
    private CommandIrisObjectXAY xay;

    @Command
    private CommandIrisObjectShift shift;

    @Command
    private CommandIrisObjectExpand expand;

    @Command
    private CommandIrisObjectContract contract;

    @Command
    private CommandIrisObjectP1 p1;

    @Command
    private CommandIrisObjectP2 p2;

    @Command
    private CommandIrisObjectSave save;

    @Command
    private CommandIrisObjectPaste paste;

    @Command
    private CommandIrisObjectUndo undo;

    @Command
    private CommandIrisObjectAnalyze analyze;

    public CommandIrisObject() {
        super("object", "iob", "o", "obj");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Object Commands");
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

        sender.sendMessage("Iris Object Commands:");
        printHelp(sender);
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[subcommand]";
    }
}
