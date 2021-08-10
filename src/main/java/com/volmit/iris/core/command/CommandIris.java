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

package com.volmit.iris.core.command;

import com.volmit.iris.Iris;
import com.volmit.iris.core.command.jigsaw.CommandIrisJigsaw;
import com.volmit.iris.core.command.object.CommandIrisObject;
import com.volmit.iris.core.command.pregen.CommandIrisPregen;
import com.volmit.iris.core.command.studio.CommandIrisStudio;
import com.volmit.iris.core.command.what.CommandIrisWhat;
import com.volmit.iris.core.command.world.CommandIrisCreate;
import com.volmit.iris.core.command.world.CommandIrisUpdateWorld;
import com.volmit.iris.core.command.world.CommandIrisVerify;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.Command;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIris extends MortarCommand {
    @Command
    private CommandIrisCreate create;

    @Command
    private CommandIrisVerify verify;

    @Command
    private CommandIrisDebug debug;

    @Command
    private CommandIrisStudio studio;

    @Command
    private CommandIrisJigsaw jigsaw;

    @Command
    private CommandIrisObject object;

    @Command
    private CommandIrisDownload download;

    @Command
    private CommandIrisUpdateProject updateProject;

    @Command
    private CommandIrisUpdateWorld updateWorld;

    @Command
    private CommandIrisBitwise bitwise;

    @Command
    private CommandIrisWhat what;

    @Command
    private CommandIrisMetrics metrics;

    @Command
    private CommandIrisPregen pregen;

    @Command
    private CommandIrisReload reload;

    @Command
    private CommandIrisAura aura;

    public CommandIris() {
        super("iris", "ir", "irs");
        requiresPermission(Iris.perm);
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        sender.sendMessage("Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
        printHelp(sender);

        return true;
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
