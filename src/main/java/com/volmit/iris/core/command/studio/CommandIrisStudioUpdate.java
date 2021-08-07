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
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;

import java.io.File;
import java.io.IOException;

public class CommandIrisStudioUpdate extends MortarCommand {
    public CommandIrisStudioUpdate() {
        super("update", "upd", "u");
        requiresPermission(Iris.perm.studio);
        setDescription("Update your dimension project.");
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

        if (args.length == 0) {
            sender.sendMessage("/iris std package <DIMENSION>");
            return true;
        }

        for (String i : args) {
            if (i.equals("--rewrite-objects")) {
                IrisData data = new IrisData(Iris.proj.getWorkspaceFolder(args[0]));
                int t = data.getObjectLoader().getPossibleKeys().length;
                ChronoLatch cl = new ChronoLatch(250, false);
                MultiBurst bx = new MultiBurst("Object Rewriter", Thread.MIN_PRIORITY, Runtime.getRuntime().availableProcessors());
                BurstExecutor b = bx.burst();
                int g = 0;
                for (String f : data.getObjectLoader().getPossibleKeys()) {
                    int finalG1 = g;
                    b.queue(() -> {

                        if (cl.flip()) {
                            Iris.info("Rewriting: " + Form.f(t - finalG1) + " Objects Left");
                        }
                        File ff = data.getObjectLoader().findFile(f);
                        IrisObject oo = new IrisObject(0, 0, 0);
                        try {
                            oo.read(ff);
                        } catch (Throwable e) {
                            Iris.error("FAILER TO READ: " + f);
                            return;
                        }

                        if (oo == null) {
                            Iris.error("FAILER TO READ: " + f);
                            return;
                        }

                        try {
                            oo.write(ff);
                        } catch (IOException e) {
                            Iris.error("FAILURE TO WRITE: " + oo.getLoadFile());
                        }
                    });
                    g++;
                }

                int finalG = g;
                J.a(() -> {
                    b.complete();
                    bx.shutdownNow();
                    sender.sendMessage("Done! Rewrote " + Form.f(finalG) + " Objects!");
                });
            }
        }

        if (new IrisProject(Iris.proj.getWorkspaceFolder(args[0])).updateWorkspace()) {
            sender.sendMessage("Updated Code Workspace for " + args[0]);
        } else {
            sender.sendMessage("Invalid project: " + args[0] + ". Try deleting the code-workspace file and try again.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[dimension] [--rewrite-objects]";
    }
}
