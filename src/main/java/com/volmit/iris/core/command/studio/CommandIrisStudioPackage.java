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
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioPackage extends MortarCommand {
    public CommandIrisStudioPackage() {
        super("package", "pkg");
        requiresPermission(Iris.perm.studio);
        setDescription("Package your dimension into a compressed format.");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/iris std package <DIMENSION> [-o] [-m]");
            return true;
        }

        J.a(() ->
        {
            boolean o = false;
            boolean m = true;

            for (String i : args) {
                if (i.equalsIgnoreCase("-o")) {
                    o = true;
                    break;
                }
            }

            String dim = args[0];
            Iris.proj.compilePackage(sender, dim, o, m);
        });

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[dimension] [-o] [-m]";
    }
}
