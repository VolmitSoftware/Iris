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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeContext;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import org.bukkit.Chunk;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Decree(name = "iris", aliases = {"ir", "irs"}, description = "Basic Command")
public class CommandIris implements DecreeExecutor {
    private CommandStudio studio;
    private CommandPregen pregen;
    private CommandSettings settings;
    private CommandObject object;
    private CommandJigsaw jigsaw;
    private CommandWhat what;

    @Decree(description = "Create a new world", aliases = "+")
    public void create(
            @Param(aliases = "world-name", description = "The name of the world to create")
                    String name,
            @Param(aliases = "dimension", description = "The dimension type to create the world with", defaultValue = "overworld")
                    IrisDimension type,
            @Param(description = "The seed to generate the world with", defaultValue = "1337")
                    long seed
    ) {
        if (name.equals("iris")) {
            sender().sendMessage(C.RED + "You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds.");
            sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
            return;
        }

        if (new File(name).exists()) {
            sender().sendMessage(C.RED + "That folder already exists!");
            return;
        }

        try {
            IrisToolbelt.createWorld()
                    .dimension(type.getLoadKey())
                    .name(name)
                    .seed(seed)
                    .sender(sender())
                    .studio(false)
                    .create();
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Exception raised during creation. See the console for more details.");
            Iris.error("Exception raised during world creation: " + e.getMessage());
            Iris.reportError(e);
            return;
        }

        sender().sendMessage(C.GREEN + "Successfully created your world!");
    }

    @Decree(description = "Print version information")
    public void version() {
        sender().sendMessage(C.GREEN + "Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
    }

    @Decree(description = "Set aura spins")
    public void aura(
            @Param(description = "The h color value", defaultValue = "-20")
                    int h,
            @Param(description = "The s color value", defaultValue = "7")
                    int s,
            @Param(description = "The b color value", defaultValue = "8")
                    int b
    ) {
        IrisSettings.get().getGeneral().setSpinh(h);
        IrisSettings.get().getGeneral().setSpins(s);
        IrisSettings.get().getGeneral().setSpinb(b);
        IrisSettings.get().forceSave();
        sender().sendMessage("<rainbow>Aura Spins updated to " + h + " " + s + " " + b);
    }

    @Decree(description = "Bitwise calculations")
    public void bitwise(
            @Param(description = "The first value to run calculations on")
                    int value1,
            @Param(description = "The operator: | & ^ ≺≺ ≻≻ ％")
                    String operator,
            @Param(description = "The second value to run calculations on")
                    int value2
    ) {
        Integer v = null;
        switch (operator) {
            case "|" -> v = value1 | value2;
            case "&" -> v = value1 & value2;
            case "^" -> v = value1 ^ value2;
            case "%" -> v = value1 % value2;
            case ">>" -> v = value1 >> value2;
            case "<<" -> v = value1 << value2;
        }
        if (v == null) {
            sender().sendMessage(C.RED + "The operator you entered: (" + operator + ") is invalid!");
            return;
        }
        sender().sendMessage(C.GREEN + "" + value1 + " " + C.GREEN + operator.replaceAll("<", "≺").replaceAll(">", "≻").replaceAll("%", "％") + " " + C.GREEN + value2 + C.GREEN + " returns " + C.GREEN + v);
    }

    @Decree(description = "Toggle debug")
    public void debug(
            @Param(name = "on", description = "Whether or not debug should be on", defaultValue = "other")
                    Boolean on
    ) {
        boolean to = on == null ? !IrisSettings.get().getGeneral().isDebug() : on;
        IrisSettings.get().getGeneral().setDebug(to);
        sender().sendMessage(C.GREEN + "Set debug to: " + to);
    }

    @Decree(description = "Download a project.", aliases = "dl")
    public void download(
            @Param(name = "pack", description = "The pack to download", defaultValue = "overworld", aliases = "project")
                    String pack,
            @Param(name = "branch", description = "The branch to download from", defaultValue = "master")
                    String branch,
            @Param(name = "trim", description = "Whether or not to download a trimmed version (do not enable when editing)", defaultValue = "false")
                    boolean trim,
            @Param(name = "overwrite", description = "Whether or not to overwrite the pack with the downloaded one", aliases = "force", defaultValue = "false")
                    boolean overwrite
    ) {
        sender().sendMessage(C.GREEN + "Downloading pack: " + pack + "/" + branch + (trim ? " trimmed" : "") + (overwrite ? " overwriting" : ""));
        Iris.service(StudioSVC.class).downloadSearch(sender(), "IrisDimensions/" + pack + "/" + branch, trim, overwrite);
    }

    @Decree(description = "Reload configuration file (this is also done automatically)")
    public void reload() {
        IrisSettings.invalidate();
        IrisSettings.get();
        sender().sendMessage(C.GREEN + "Hotloaded settings");
    }

    @Decree(name = "regen", description = "Regenerate nearby chunks.", aliases = "rg", sync = true, origin = DecreeOrigin.PLAYER)
    public void regen(
            @Param(name = "radius", description = "The radius of nearby cunks", defaultValue = "5")
                    int radius
    ) {
        if (IrisToolbelt.isIrisWorld(player().getWorld())) {
            VolmitSender sender = sender();
            J.a(() -> {
                DecreeContext.touch(sender);
                PlatformChunkGenerator plat = IrisToolbelt.access(player().getWorld());
                Engine engine = plat.getEngine();
                try {
                    int vd = radius;
                    int rg = 0;
                    Chunk cx = player().getLocation().getChunk();
                    KList<Runnable> js = new KList<>();
                    BurstExecutor b = MultiBurst.burst.burst();
                    b.setMulticore(false);
                    int rad = engine.getMantle().getRealRadius();
                    for (int i = -(vd + rad); i <= vd + rad; i++) {
                        for (int j = -(vd + rad); j <= vd + rad; j++) {
                            engine.getMantle().getMantle().deleteChunk(i + cx.getX(), j + cx.getZ());
                        }
                    }

                    for (int i = -vd; i <= vd; i++) {
                        for (int j = -vd; j <= vd; j++) {
                            int finalJ = j;
                            int finalI = i;
                            b.queue(() -> plat.injectChunkReplacement(player().getWorld(), finalI + cx.getX(), finalJ + cx.getZ(), (f) -> {
                                synchronized (js) {
                                    js.add(f);
                                }
                            }));
                        }
                    }

                    b.complete();
                    sender().sendMessage(C.GREEN + "Regenerating " + Form.f(js.size()) + " Sections");
                    QueueJob<Runnable> r = new QueueJob<>() {
                        final KList<Future<?>> futures = new KList<>();

                        @Override
                        public void execute(Runnable runnable) {
                            futures.add(J.sfut(runnable));

                            if (futures.size() > 64) {
                                while (futures.isNotEmpty()) {
                                    try {
                                        futures.remove(0).get();
                                    } catch (InterruptedException | ExecutionException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                        @Override
                        public String getName() {
                            return "Regenerating";
                        }
                    };
                    r.queue(js);
                    r.execute(sender());
                } catch (Throwable e) {
                    sender().sendMessage("Unable to parse view-distance");
                }
            });
        } else {
            sender().sendMessage(C.RED + "You must be in an Iris World to use regen!");
        }
    }
}
