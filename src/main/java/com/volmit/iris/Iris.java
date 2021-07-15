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

package com.volmit.iris;

import com.volmit.iris.manager.*;
import com.volmit.iris.manager.command.CommandIris;
import com.volmit.iris.manager.command.PermissionIris;
import com.volmit.iris.manager.command.world.CommandLocate;
import com.volmit.iris.manager.link.BKLink;
import com.volmit.iris.manager.link.CitizensLink;
import com.volmit.iris.manager.link.MultiverseCoreLink;
import com.volmit.iris.manager.link.MythicMobsLink;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisCompat;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import com.volmit.iris.util.*;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

@SuppressWarnings("CanBeFinal")
public class Iris extends VolmitPlugin implements Listener {
    public static KList<GroupedExecutor> executors = new KList<>();
    public static Iris instance;
    public static ProjectManager proj;
    public static ConversionManager convert;
    public static WandManager wand;
    public static EditManager edit;
    public static IrisBoardManager board;
    public static BKLink linkBK;
    public static MultiverseCoreLink linkMultiverseCore;
    public static MythicMobsLink linkMythicMobs;
    public static CitizensLink linkCitizens;
    private static final Queue<Runnable> syncJobs = new ShurikenQueue<>();
    public static boolean customModels = doesSupportCustomModels();
    public static boolean awareEntities = doesSupportAwareness();
    public static boolean biome3d = doesSupport3DBiomes();
    public static boolean lowMemoryMode = false;
    public static IrisCompat compat;
    public static FileWatcher configWatcher;

    @Permission
    public static PermissionIris perm;

    @com.volmit.iris.util.Command
    public CommandIris commandIris;

    public Iris() {
        instance = this;
        INMS.get();
        IO.delete(new File("iris"));
        lowMemoryMode = Runtime.getRuntime().maxMemory() < 4000000000L; // 4 * 1000 * 1000 * 1000 // 4;
        installDataPacks();
    }

    private void installDataPacks() {
        Iris.info("Checking Data Packs...");
        boolean reboot = false;
        File packs = new File("plugins/Iris/packs");
        File dpacks = null;
        File props = new File("server.properties");

        if (props.exists()) {
            try {
                KList<String> m = new KList<>(IO.readAll(props).split("\\Q\n\\E"));

                for (String i : m) {
                    if (i.trim().startsWith("level-name=")) {
                        dpacks = new File(i.trim().split("\\Q=\\E")[1] + "/datapacks");
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (dpacks == null) {
            Iris.error("Cannot find the datapacks folder! Please try generating a default world first maybe? Is this a new server?");
            return;
        }


        if (packs.exists()) {
            for (File i : packs.listFiles()) {
                if (i.isDirectory()) {
                    Iris.verbose("Checking Pack: " + i.getPath());
                    IrisDataManager data = new IrisDataManager(i);
                    File dims = new File(i, "dimensions");

                    if (dims.exists()) {
                        for (File j : dims.listFiles()) {
                            if (j.getName().endsWith(".json")) {
                                IrisDimension dim = data.getDimensionLoader().load(j.getName().split("\\Q.\\E")[0]);
                                Iris.verbose("  Checking Dimension " + dim.getLoadFile().getPath());
                                if (dim.installDataPack(() -> data, dpacks)) {
                                    reboot = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        Iris.info("Data Packs Setup!");
    }

    public static int getThreadCount() {
        int tc = IrisSettings.get().getConcurrency().getThreadCount();

        if (tc <= 0) {
            int p = Runtime.getRuntime().availableProcessors();

            return p > 16 ? 16 : Math.max(p, 4);
        }

        return tc;
    }

    private static boolean doesSupport3DBiomes() {
        try {
            int v = Integer.parseInt(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

            return v >= 15;
        } catch (Throwable ignored) {

        }

        return false;
    }

    private static boolean doesSupportCustomModels() {
        try {
            int v = Integer.parseInt(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

            return v >= 14;
        } catch (Throwable ignored) {

        }

        return false;
    }

    private static boolean doesSupportAwareness() {
        try {
            int v = Integer.parseInt(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

            return v >= 15;
        } catch (Throwable ignored) {

        }

        return false;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.GREEN + "Iris" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public void onEnable() {
        instance = this;
        try {
            compat = IrisCompat.configured(getDataFile("compat.json"));
        } catch (IOException e) {
            // Do nothing. Everything continues properly but the exception is still there.
        }
        proj = new ProjectManager();
        convert = new ConversionManager();
        wand = new WandManager();
        board = new IrisBoardManager();
        linkMultiverseCore = new MultiverseCoreLink();
        linkBK = new BKLink();
        linkMythicMobs = new MythicMobsLink();
        edit = new EditManager();
        configWatcher = new FileWatcher(getDataFile("settings.json"));
        J.a(() -> IO.delete(getTemp()));
        J.a(this::bstats);
        J.s(this::splash, 20);
        J.sr(this::tickQueue, 0);
        J.ar(this::checkConfigHotload, 50);
        PaperLib.suggestPaper(this);
        getServer().getPluginManager().registerEvents(new CommandLocate(), this);
        getServer().getPluginManager().registerEvents(new WandManager(), this);
        super.onEnable();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void checkConfigHotload() {
        if (configWatcher.checkModified()) {
            IrisSettings.invalidate();
            IrisSettings.get();
            configWatcher.checkModified();
            Iris.info("Hotloaded settings.json");
        }
    }

    public void onDisable() {
        if (IrisSettings.get().isStudio()) {
            proj.close();

            for (World i : Bukkit.getWorlds()) {
                if (IrisWorlds.isIrisWorld(i)) {
                    IrisWorlds.access(i).close();
                }
            }

            for (GroupedExecutor i : executors) {
                i.close();
            }
        }

        executors.clear();
        board.disable();
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin) this);
        super.onDisable();
    }

    public static void sq(Runnable r) {
        synchronized (syncJobs) {
            syncJobs.queue(r);
        }
    }

    private void tickQueue() {
        synchronized (Iris.syncJobs) {
            if (!Iris.syncJobs.hasNext()) {
                return;
            }

            long ms = M.ms();

            while (Iris.syncJobs.hasNext() && M.ms() - ms < 25) {
                try {
                    Iris.syncJobs.next().run();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void bstats() {
        if (IrisSettings.get().getGeneral().isPluginMetrics()) {
            J.s(() -> {
                Metrics m = new Metrics(Iris.instance, 8757);

                m.addCustomChart(new Metrics.SingleLineChart("custom_dimensions", ProjectManager::countUniqueDimensions));

                m.addCustomChart(new Metrics.SimplePie("using_custom_dimensions", () -> ProjectManager.countUniqueDimensions() > 0 ? "Active Projects" : "No Projects"));
            });
        }
    }

    public static File getTemp() {
        return instance.getDataFolder("cache", "temp");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return super.onCommand(sender, command, label, args);
    }

    public void imsg(CommandSender s, String msg) {
        s.sendMessage(C.GREEN + "[" + C.DARK_GRAY + "Iris" + C.GREEN + "]" + C.GRAY + ": " + msg);
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        String dimension = IrisSettings.get().getGenerator().getDefaultWorldType();

        if (id != null && !id.isEmpty()) {
            dimension = id;
            Iris.info("Generator ID: " + id + " requested by bukkit/plugin. Assuming IrisDimension: " + id);
        }

        return new EngineCompositeGenerator(dimension, true);
    }

    public static void msg(String string) {
        try {
            if (instance == null) {
                System.out.println("[Iris]: " + string);
                return;
            }

            String msg = C.GRAY + "[" + C.GREEN + "Iris" + C.GRAY + "]: " + string;
            Bukkit.getConsoleSender().sendMessage(msg);
        } catch (Throwable e) {
            System.out.println("[Iris]: " + string);
        }
    }

    public static File getCached(String name, String url) {
        String h = IO.hash(name + "@" + url);
        File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

        if (!f.exists()) {
            try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                    Iris.verbose("Aquiring " + name);
                }
            } catch (IOException ignored) {

            }
        }

        return f.exists() ? f : null;
    }

    public static String getNonCached(String name, String url) {
        String h = IO.hash(name + "*" + url);
        File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException ignored) {

        }

        try {
            return IO.readAll(f);
        } catch (IOException ignored) {

        }

        return "";
    }

    public static File getNonCachedFile(String name, String url) {
        String h = IO.hash(name + "*" + url);
        File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);
        Iris.verbose("Download " + name + " -> " + url);
        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return f;
    }

    public static void warn(String string) {
        msg(C.YELLOW + string);
    }

    public static void error(String string) {
        msg(C.RED + string);
    }

    public static void verbose(String string) {
        try {
            if (IrisSettings.get().getGeneral().isVerbose()) {
                msg(C.GRAY + string);
            }
        } catch (Throwable e) {
            msg(C.GRAY + string);
        }
    }

    public static void success(String string) {
        msg(C.GREEN + string);
    }

    public static void info(String string) {
        msg(C.WHITE + string);
    }

    public void hit(long hits2) {
        board.hits.put(hits2);
    }

    public void splash() {
        if (!IrisSettings.get().getGeneral().isSplashLogoStartup()) {
            return;
        }

        // @NoArgsConstructor
        String padd = Form.repeat(" ", 8);
        String padd2 = Form.repeat(" ", 4);
        String[] info = {"", "", "", "", "", padd2 + C.GREEN + " Iris", padd2 + C.GRAY + " by " + C.randomColor() + "V" + C.randomColor() + "o" + C.randomColor() + "l" + C.randomColor() + "m" + C.randomColor() + "i" + C.randomColor() + "t" + C.randomColor() + "S" + C.randomColor() + "o" + C.randomColor() + "f" + C.randomColor() + "t" + C.randomColor() + "w" + C.randomColor() + "a" + C.randomColor() + "r" + C.randomColor() + "e", padd2 + C.GRAY + " v" + getDescription().getVersion(),
        };
        String[] splash = {padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@", padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + C.GREEN + "   .(((()))).                     ", padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + C.GREEN + "  .((((((())))))).                  ", padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + C.GREEN + "  ((((((((()))))))))               " + C.GRAY + " @", padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + C.GREEN + "    ((((((((-)))))))))              " + C.GRAY + " @@", padd + C.GRAY + "@@@&&" + C.GREEN + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@", padd + C.GRAY + "@@" + C.GREEN + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@", padd + C.GRAY + "@" + C.GREEN + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@", padd + C.GRAY + "" + C.GREEN + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@", padd + C.GRAY + "" + C.GREEN + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@", padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };
        //@done
        Iris.info("Server type & version: " + Bukkit.getVersion());
        Iris.info("Bukkit version: " + Bukkit.getBukkitVersion());
        Iris.info("Java version: " + getJavaVersion());
        Iris.info("Custom Biomes: " + INMS.get().countCustomBiomes());
        for (int i = 0; i < info.length; i++) {
            splash[i] += info[i];
        }

        Iris.info("\n\n " + new KList<>(splash).toString("\n") + "\n");

        if (lowMemoryMode) {
            Iris.verbose("* Low Memory mode Activated! For better performance, allocate 4gb or more to this server.");
        }

        if (!biome3d) {
            Iris.verbose("* This version of minecraft does not support 3D biomes (1.15 and up). Iris will generate as normal, but biome colors will not vary underground & in the sky.");
        }

        if (!customModels) {
            Iris.verbose("* This version of minecraft does not support custom model data in loot items (1.14 and up). Iris will generate as normal, but loot will not have custom models.");
        }

        if (!doesSupportAwareness()) {
            Iris.verbose("* This version of minecraft does not support entity awareness.");
        }
    }

    @SuppressWarnings("deprecation")
    public static void later(NastyRunnable object) {
        Bukkit.getScheduler().scheduleAsyncDelayedTask(instance, () ->
        {
            try {
                object.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, RNG.r.i(100, 1200));
    }

    public static int jobCount() {
        return syncJobs.size();
    }

    public static void clearQueues() {
        synchronized (syncJobs) {
            syncJobs.clear();
        }
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    public boolean isMCA() {
        return IrisSettings.get().getGenerator().isMcaPregenerator();
    }
}
