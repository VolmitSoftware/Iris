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
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.MortarSender;

import java.io.File;

public class CommandIrisStudioBeautify extends MortarCommand {
    public CommandIrisStudioBeautify() {
        super("beautify", "prettify");
        requiresPermission(Iris.perm.studio);
        setDescription("Prettify the project by cleaning up json.");
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

        File clean = null;

        if (args.length == 0) {
            if (!Iris.proj.isProjectOpen()) {
                sender.sendMessage("No open project. Either use /iris std beautify <project> or have a project open.");
                return true;
            }

            clean = Iris.proj.getActiveProject().getPath();
        } else {
            clean = Iris.instance.getDataFolder("packs", args[0]);

            if (!clean.exists()) {
                sender.sendMessage("Not a valid project.");
                return true;
            }
        }

        sender.sendMessage("Cleaned " + Form.f(clean(sender, clean)) + " JSON Files");

        return true;
    }

    private int clean(MortarSender s, File clean) {
        int c = 0;
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                c += clean(s, i);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                IO.writeAll(clean, new JSONObject(IO.readAll(clean)).toString(4));
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
            }

            c++;
        }

        return c;
    }

    @Override
    protected String getArgsUsage() {
        return "[project]";
    }
}
