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
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisStudioExecute extends MortarCommand {
    public CommandIrisStudioExecute() {
        super("execute", "ex", "exec");
        requiresPermission(Iris.perm.studio);
        setDescription("Execute a script");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisToolbelt.isIrisWorld(sender.player().getWorld())) {
            IrisData data = IrisToolbelt.access(sender.player().getWorld()).getEngine().getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for summons while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getScriptLoader().getPossibleKeys());
            } else {
                list.add(data.getScriptLoader().getPossibleKeys(args[0]));
            }
        } else {
            sender.sendMessage("You must be in an Iris world as a player to run scripts!");
        }
    }

    @SuppressWarnings("null")
    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer() || !IrisToolbelt.isIrisWorld(sender.player().getWorld())) {
            sender.sendMessage("To execute scripts you must be in an Iris world as a player");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("You need to specify a script name (use auto-completions to see which are available)");
            return true;
        }

        Iris.info("Executing script: " + args[0] + ". See script output in console.");

        try {
            IrisToolbelt.access(sender.player().getWorld()).getEngine().getExecution().execute(args[0]);
        } catch (Throwable e) {
            Iris.reportError(e);
            sender.sendMessage("Failed to execute script " + args[0] + "!");
        }
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
