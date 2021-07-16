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

package com.volmit.iris.core.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisStudioCreate extends MortarCommand {
    public CommandIrisStudioCreate() {
        super("create", "new", "+");
        requiresPermission(Iris.perm.studio);
        setDescription("Create a new project & open it.");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("Please use a lowercase name with hyphens (-) for spaces.");
            sender.sendMessage("I.e. /iris std new " + C.BOLD + "aether");
            return true;
        }

        String template = null;

        for (String i : args) {
            if (i.startsWith("template=")) {
                template = i.split("\\Q=\\E")[1];
            }
        }

        if (template != null) {
            Iris.proj.create(sender, args[0], template);
        } else {
            Iris.proj.create(sender, args[0]);
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[dimension] [template=<project>]";
    }
}
