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

package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.jobs.Job;
import com.volmit.iris.util.scheduling.jobs.JobCollection;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import com.volmit.iris.util.scheduling.jobs.SingleJob;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Decree(name = "studio", aliases = "std", description = "Studio Commands", studio = true)
public class CMDIrisStudio implements DecreeExecutor
{
    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(name = "dimension", defaultValue = "overworld", aliases = "dim", required = true)
                    IrisDimension dimension,
            @Param(name = "seed", defaultValue = "1337", aliases = "s")
                    long seed)
    {
        Iris.proj.open(sender(), dimension.getLoadKey());
    }

    @Decree(description = "Create a new studio project", aliases = "+", sync = true)
    public void create(
            @Param(name = "name", required = true, description = "The name of this new Iris Project.")
                    String name,
            @Param(name = "template", description = "Copy the contents of an existing project in your packs folder and use it as a template in this new project.")
                    IrisDimension template)
    {
        if (template != null) {
            Iris.proj.create(sender(), name, template.getLoadKey());
        } else {
            Iris.proj.create(sender(), name);
        }
    }

    @Decree(description = "Edit the biome at your current location", aliases = "eb", sync = true, origin = DecreeOrigin.PLAYER)
    public void editBiome()
    {
        if (!Iris.proj.isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }
        ;
        try {
            Desktop.getDesktop().open(Iris.proj.getActiveProject().getActiveProvider().getEngine().getBiome(sender().player().getLocation()).getLoadFile());
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage("Cant find the file. Are you in an Iris Studio world?");
        }

    }

    @Decree(description = "Close an open studio project", aliases = "x", sync = true)
    public void close()
    {
        if (!Iris.proj.isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }

        Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().evacuate();
        Iris.proj.close();
        sender().sendMessage(C.YELLOW + "Project Closed");
    }

    @Decree(description = "Clean an Iris Project, optionally beautifying JSON & fixing block ids with missing keys. Also rebuilds the vscode schemas. ")
    public void clean(
            @Param(name = "project", required = true, description = "The project to update")
            IrisDimension project,

            @Param(name = "beautify", defaultValue = "true", description = "Filters all valid JSON files with a beautifier (indentation: 4)")
            boolean beautify,

            @Param(name = "fix-ids", defaultValue = "true", description = "Fixes any block ids used such as \"dirt\" will be converted to \"minecraft:dirt\"")
            boolean fixIds,

            @Param(name = "rewriteObjects", defaultValue = "false", description = "Imports all objects and re-writes them cleaning up positions & block data in the process.")
            boolean rewriteObjects
    ) {
        KList<Job> jobs = new KList<>();
        KList<File> files = new KList<File>();
        files(Iris.instance.getDataFolder("packs", project.getLoadKey()), files);
        MultiBurst burst = new MultiBurst("Cleaner", Thread.MIN_PRIORITY, Runtime.getRuntime().availableProcessors() * 2);

        jobs.add(new SingleJob("Updating Workspace", () -> {
            if (!new IrisProject(Iris.proj.getWorkspaceFolder(project.getLoadKey())).updateWorkspace()) {
                sender().sendMessage(C.GOLD + "Invalid project: " + project.getLoadKey() + ". Try deleting the code-workspace file and try again.");
            }
            J.sleep(250);
        }));

        sender().sendMessage("Files: " + files.size());

        if(fixIds)
        {
            QueueJob<File> r = new QueueJob<>() {
                @Override
                public void execute(File f) {
                    try {
                        JSONObject p = new JSONObject(IO.readAll(f));
                        fixBlocks(p);
                        J.sleep(1);
                        IO.writeAll(f, p.toString(4));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getName() {
                    return "Fixing IDs";
                }
            };

            r.queue(files);
            jobs.add(r);
        }

        if(beautify)
        {
            QueueJob<File> r = new QueueJob<>() {
                @Override
                public void execute(File f) {
                    try {
                        JSONObject p = new JSONObject(IO.readAll(f));
                        IO.writeAll(f, p.toString(4));
                        J.sleep(1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getName() {
                    return "Beautify";
                }
            };

            r.queue(files);
            jobs.add(r);
        }

        if(rewriteObjects)
        {
            QueueJob<Runnable> q = new QueueJob<Runnable>() {
                @Override
                public void execute(Runnable runnable) {
                    runnable.run();
                    J.sleep(50);
                }

                @Override
                public String getName() {
                    return "Rewriting Objects";
                }
            };

            IrisData data = new IrisData(Iris.proj.getWorkspaceFolder(project.getLoadKey()));
            for (String f : data.getObjectLoader().getPossibleKeys()) {
                CompletableFuture<?> gg = burst.complete(() ->{
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

                q.queue(() -> {
                    try {
                        gg.get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            }

            jobs.add(q);
        }

        jobs.add(new SingleJob("Finishing Up", burst::shutdownNow));

        new JobCollection("Cleaning", jobs).execute(sender());
    }

    public void files(File clean, KList<File> files)
    {
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                files(i, files);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                files.add(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
            }
        }
    }

    private void fixBlocks(JSONObject obj) {
        for (String i : obj.keySet()) {
            Object o = obj.get(i);

            if (i.equals("block") && o instanceof String && !o.toString().trim().isEmpty() && !o.toString().contains(":")) {
                obj.put(i, "minecraft:" + o);
            }

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }

    private void fixBlocks(JSONArray obj) {
        for (int i = 0; i < obj.length(); i++) {
            Object o = obj.get(i);

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }
}
