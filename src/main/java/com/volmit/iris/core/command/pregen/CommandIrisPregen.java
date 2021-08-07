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

package com.volmit.iris.core.command.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.Command;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisPregen extends MortarCommand {
    @Command
    private CommandIrisPregenStart start;

    @Command
    private CommandIrisPregenStop stop;

    @Command
    private CommandIrisPregenPause toggle;

    public CommandIrisPregen() {
        super("pregen", "preg", "p");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Pregeneration Commands");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (sender.isPlayer()) {
            if (!IrisToolbelt.isIrisWorld(sender.player().getWorld())) {
                sender.sendMessage("Pregen only works in Iris worlds!");
            }
        } else {
            sender.sendMessage("Note that pregeneration only works in Iris worlds!");
        }

        sender.sendMessage("Iris Pregen Commands:");
        printHelp(sender);
        sender.sendMessage("Pregen wiki page: https://docs.volmit.com/iris/pregeneration");
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[subcommand]";
    }
}
