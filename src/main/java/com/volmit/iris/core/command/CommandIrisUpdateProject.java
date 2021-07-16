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
import com.volmit.iris.util.*;

public class CommandIrisUpdateProject extends MortarCommand {
    public CommandIrisUpdateProject() {
        super("update-project", "^project");
        requiresPermission(Iris.perm.studio);
        setDescription("Update a project from git.");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/iris update-project " + C.BOLD + "<PROJECT>");
            return true;
        }

        J.a(() -> Iris.proj.downloadSearch(sender, args[0], false, true));

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<project>";
    }
}
