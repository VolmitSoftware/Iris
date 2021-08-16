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
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisPregenPause extends MortarCommand {

    public CommandIrisPregenPause() {
        super("pause", "toggle", "t", "continue", "resume", "p", "c", "unpause", "up");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Toggle an ongoing pregeneration task");
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (PregeneratorJob.pauseResume()) {
            sender.sendMessage("Paused/unpaused pregeneration task, now: " + (PregeneratorJob.isPaused() ? "Paused" : "Running") + ".");
        } else {
            sender.sendMessage("No active pregeneration tasks to pause/unpause.");
        }
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
