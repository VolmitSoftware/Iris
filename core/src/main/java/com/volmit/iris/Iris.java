/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.link.IrisPapiExpansion;
import com.volmit.iris.core.link.MultiverseCoreLink;
import com.volmit.iris.core.link.MythicMobsLink;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import com.volmit.iris.core.pregenerator.LazyPregenerator;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.EnginePanic;
import com.volmit.iris.engine.object.IrisCompat;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.engine.platform.DummyChunkGenerator;
import com.volmit.iris.core.safeguard.IrisSafeguard;
import com.volmit.iris.core.safeguard.UtilsSFG;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.NastyRunnable;
import com.volmit.iris.util.io.FileWatcher;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.io.InstanceState;
import com.volmit.iris.util.io.JarScanner;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.Metrics;
import com.volmit.iris.util.plugin.VolmitPlugin;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.reflect.ShadeFix;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Queue;
import com.volmit.iris.util.scheduling.ShurikenQueue;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.volmit.iris.core.safeguard.IrisSafeguard.*;
import static com.volmit.iris.core.safeguard.ServerBootSFG.passedserversoftware;
import static com.volmit.iris.util.misc.getHardware.getCPUModel;

@SuppressWarnings("CanBeFinal")
public class Iris extends VolmitPlugin implements Listener {
    public static final String OVERWORLD_TAG = "3800";

    private static final Queue<Runnable> syncJobs = new ShurikenQueue<>();

    public static Iris instance;
    public static BukkitAudiences audiences;
    public static MultiverseCoreLink linkMultiverseCore;
    public static MythicMobsLink linkMythicMobs;
    public static IrisCompat compat;
    public static FileWatcher configWatcher;
    private static VolmitSender sender;

    static {
        try {
            fixShading();
            InstanceState.updateInstanceId();
        } catch (Throwable ignored) {

        }
    }

    private final KList<Runnable> postShutdown = new KList<>();
    private KMap<Class<? extends IrisService>, IrisService> services;

    public static VolmitSender getSender() {
        return sender;
    }

    @SuppressWarnings("unchecked")
    public static <T> T service(Class<T> c) {
        return (T) instance.services.get(c);
    }

    public static void callEvent(Event e) {
        if (!e.isAsynchronous()) {
            J.s(() -> Bukkit.getPluginManager().callEvent(e));
        } else {
            Bukkit.getPluginManager().callEvent(e);
        }
    }

    public static KList<Object> initialize(String s, Class<? extends Annotation> slicedClass) {
        JarScanner js = new JarScanner(instance.getJarFile(), s);
        KList<Object> v = new KList<>();
        J.attempt(js::scan);
        for (Class<?> i : js.getClasses()) {
            if (slicedClass == null || i.isAnnotationPresent(slicedClass)) {
                try {
                    v.add(i.getDeclaredConstructor().newInstance());
                } catch (Throwable ignored) {

                }
            }
        }

        return v;
    }

    public static KList<Class<?>> getClasses(String s, Class<? extends Annotation> slicedClass) {
        JarScanner js = new JarScanner(instance.getJarFile(), s);
        KList<Class<?>> v = new KList<>();
        J.attempt(js::scan);
        for (Class<?> i : js.getClasses()) {
            if (slicedClass == null || i.isAnnotationPresent(slicedClass)) {
                try {
                    v.add(i);
                } catch (Throwable ignored) {

                }
            }
        }

        return v;
    }

    public static KList<Object> initialize(String s) {
        return initialize(s, null);
    }

    public static void sq(Runnable r) {
        synchronized (syncJobs) {
            syncJobs.queue(r);
        }
    }

    public static File getTemp() {
        return instance.getDataFolder("cache", "temp");
    }

    public static void msg(String string) {
        try {
            sender.sendMessage(string);
        } catch (Throwable e) {
            try {
                System.out.println(instance.getTag() + string.replaceAll("(<([^>]+)>)", ""));
            } catch (Throwable ignored1) {

            }
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
            } catch (IOException e) {
                Iris.reportError(e);
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
        } catch (IOException e) {
            Iris.reportError(e);
        }

        try {
            return IO.readAll(f);
        } catch (IOException e) {
            Iris.reportError(e);
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

            fileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Iris.reportError(e);
        }

        return f;
    }

    public static void warn(String format, Object... objs) {
        msg(C.YELLOW + String.format(format, objs));
    }

    public static void error(String format, Object... objs) {
        msg(C.RED + String.format(format, objs));
    }

    public static void debug(String string) {
        if (!IrisSettings.get().getGeneral().isDebug()) {
            return;
        }

        try {
            throw new RuntimeException();
        } catch (Throwable e) {
            try {
                String[] cc = e.getStackTrace()[1].getClassName().split("\\Q.\\E");

                if (cc.length > 5) {
                    debug(cc[3] + "/" + cc[4] + "/" + cc[cc.length - 1], e.getStackTrace()[1].getLineNumber(), string);
                } else {
                    debug(cc[3] + "/" + cc[4], e.getStackTrace()[1].getLineNumber(), string);
                }
            } catch (Throwable ex) {
                debug("Origin", -1, string);
            }
        }
    }

    public static void debug(String category, int line, String string) {
        if (!IrisSettings.get().getGeneral().isDebug()) {
            return;
        }
        if (IrisSettings.get().getGeneral().isUseConsoleCustomColors()) {
            msg("<gradient:#095fe0:#a848db>" + category + " <#bf3b76>" + line + "<reset> " + C.LIGHT_PURPLE + string.replaceAll("\\Q<\\E", "[").replaceAll("\\Q>\\E", "]"));
        } else {
            msg(C.BLUE + category + ":" + C.AQUA + line + C.RESET + C.LIGHT_PURPLE + " " + string.replaceAll("\\Q<\\E", "[").replaceAll("\\Q>\\E", "]"));

        }
    }

    public static void verbose(String string) {
        debug(string);
    }

    public static void success(String string) {
        msg(C.IRIS + string);
    }

    public static void info(String format, Object... args) {
        msg(C.WHITE + String.format(format, args));
    }
    public static void safeguard(String format, Object... args) {
        msg(C.RESET + String.format(format, args));
    }

    @SuppressWarnings("deprecation")
    public static void later(NastyRunnable object) {
        try {
            Bukkit.getScheduler().scheduleAsyncDelayedTask(instance, () ->
            {
                try {
                    object.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }, RNG.r.i(100, 1200));
        } catch (IllegalPluginAccessException ignored) {

        }
    }

    public static int jobCount() {
        return syncJobs.size();
    }

    public static void clearQueues() {
        synchronized (syncJobs) {
            syncJobs.clear();
        }
    }

    public static int getJavaVersion() {
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

    public static void reportErrorChunk(int x, int z, Throwable e, String extra) {
        if (IrisSettings.get().getGeneral().isDebug()) {
            File f = instance.getDataFile("debug", "chunk-errors", "chunk." + x + "." + z + ".txt");

            if (!f.exists()) {
                J.attempt(() -> {
                    PrintWriter pw = new PrintWriter(f);
                    pw.println("Thread: " + Thread.currentThread().getName());
                    pw.println("First: " + new Date(M.ms()));
                    e.printStackTrace(pw);
                    pw.close();
                });
            }

            Iris.debug("Chunk " + x + "," + z + " Exception Logged: " + e.getClass().getSimpleName() + ": " + C.RESET + "" + C.LIGHT_PURPLE + e.getMessage());
        }
    }

    public static void reportError(Throwable e) {
        if (IrisSettings.get().getGeneral().isDebug()) {
            String n = e.getClass().getCanonicalName() + "-" + e.getStackTrace()[0].getClassName() + "-" + e.getStackTrace()[0].getLineNumber();

            if (e.getCause() != null) {
                n += "-" + e.getCause().getStackTrace()[0].getClassName() + "-" + e.getCause().getStackTrace()[0].getLineNumber();
            }

            File f = instance.getDataFile("debug", "caught-exceptions", n + ".txt");

            if (!f.exists()) {
                J.attempt(() -> {
                    PrintWriter pw = new PrintWriter(f);
                    pw.println("Thread: " + Thread.currentThread().getName());
                    pw.println("First: " + new Date(M.ms()));
                    e.printStackTrace(pw);
                    pw.close();
                });
            }

            Iris.debug("Exception Logged: " + e.getClass().getSimpleName() + ": " + C.RESET + "" + C.LIGHT_PURPLE + e.getMessage());
        }
    }

    public static void dump() {
        try {
            File fi = Iris.instance.getDataFile("dump", "td-" + new java.sql.Date(M.ms()) + ".txt");
            FileOutputStream fos = new FileOutputStream(fi);
            Map<Thread, StackTraceElement[]> f = Thread.getAllStackTraces();
            PrintWriter pw = new PrintWriter(fos);
            for (Thread i : f.keySet()) {
                pw.println("========================================");
                pw.println("Thread: '" + i.getName() + "' ID: " + i.getId() + " STATUS: " + i.getState().name());

                for (StackTraceElement j : f.get(i)) {
                    pw.println("    @ " + j.toString());
                }

                pw.println("========================================");
                pw.println();
                pw.println();
            }

            pw.close();
            System.out.println("DUMPED! See " + fi.getAbsolutePath());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void panic() {
        EnginePanic.panic();
    }

    public static void addPanic(String s, String v) {
        EnginePanic.add(s, v);
    }

    private static void fixShading() {
        ShadeFix.fix(ComponentSerializer.class);
    }
    private void enable() {
        instance = this;
        services = new KMap<>();
        initialize("com.volmit.iris.core.service").forEach((i) -> services.put((Class<? extends IrisService>) i.getClass(), (IrisService) i));
        INMS.get();
        IO.delete(new File("iris"));
        setupAudience();
        IrisSafeguard.IrisSafeguardSystem();
        sender = new VolmitSender(Bukkit.getConsoleSender());
        sender.setTag(getTag());
        instance = this;
        compat = IrisCompat.configured(getDataFile("compat.json"));
        linkMultiverseCore = new MultiverseCoreLink();
        linkMythicMobs = new MythicMobsLink();
        configWatcher = new FileWatcher(getDataFile("settings.json"));
        services.values().forEach(IrisService::onEnable);
        services.values().forEach(this::registerListener);
        J.s(() -> {
            J.a(() -> PaperLib.suggestPaper(this));
            J.a(() -> IO.delete(getTemp()));
            J.a(LazyPregenerator::loadLazyGenerators, 100);
            J.a(this::bstats);
            J.ar(this::checkConfigHotload, 60);
            J.sr(this::tickQueue, 0);
            J.s(this::setupPapi);
            J.a(ServerConfigurator::configure, 20);
            splash();
            UtilsSFG.splash();

            autoStartStudio();
            checkForBukkitWorlds();
            IrisToolbelt.retainMantleDataForSlice(String.class.getCanonicalName());
            IrisToolbelt.retainMantleDataForSlice(BlockData.class.getCanonicalName());
        });
    }

    private void checkForBukkitWorlds() {
        FileConfiguration fc = new YamlConfiguration();
        try {
            fc.load(new File("bukkit.yml"));
            ConfigurationSection section = fc.getConfigurationSection("worlds");
            if (section == null) {
                return;
            }

            for (String s : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(s);
                if (!entry.contains("generator", true)) {
                    continue;
                }

                String generator = entry.getString("generator");
                if (generator.startsWith("Iris:")) {
                    generator = generator.split("\\Q:\\E")[1];
                } else if (generator.equalsIgnoreCase("Iris")) {
                    generator = IrisSettings.get().getGenerator().getDefaultWorldType();
                } else {
                    continue;
                }

                Iris.info("2 World: %s | Generator: %s", s, generator);

                if (Bukkit.getWorlds().stream().anyMatch(w -> w.getName().equals(s))) {
                    continue;
                }

                Iris.info(C.LIGHT_PURPLE + "Preparing Spawn for " + s + "' using Iris:" + generator + "...");
                new WorldCreator(s)
                        .generator(getDefaultWorldGenerator(s, generator))
                        .environment(IrisData.loadAnyDimension(generator).getEnvironment())
                        .createWorld();
                Iris.info(C.LIGHT_PURPLE + "Loaded " + s + "!");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void autoStartStudio() {
        if (IrisSettings.get().getStudio().isAutoStartDefaultStudio()) {
            Iris.info("Starting up auto Studio!");
            try {
                Player r = new KList<>(getServer().getOnlinePlayers()).getRandom();
                Iris.service(StudioSVC.class).open(r != null ? new VolmitSender(r) : sender, 1337, IrisSettings.get().getGenerator().getDefaultWorldType(), (w) -> {
                    J.s(() -> {
                        for (Player i : getServer().getOnlinePlayers()) {
                            i.setGameMode(GameMode.SPECTATOR);
                            i.teleport(new Location(w, 0, 200, 0));
                        }
                    });
                });
            } catch (IrisException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupAudience() {
        try {
            audiences = BukkitAudiences.create(this);
        } catch (Throwable e) {
            e.printStackTrace();
            IrisSettings.get().getGeneral().setUseConsoleCustomColors(false);
            IrisSettings.get().getGeneral().setUseCustomColorsIngame(false);
            Iris.error("Failed to setup Adventure API... No custom colors :(");
        }
    }

    public void postShutdown(Runnable r) {
        postShutdown.add(r);
    }

    public void onEnable() {
        enable();
        super.onEnable();
        Bukkit.getPluginManager().registerEvents(this, this);
        setupChecks();
    }

    public void onDisable() {
        services.values().forEach(IrisService::onDisable);
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin) this);
        postShutdown.forEach(Runnable::run);
        services.clear();
        MultiBurst.burst.close();
        super.onDisable();
    }

    private void setupPapi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new IrisPapiExpansion().register();
        }
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public String getTag(String subTag) {
        if (unstablemode) {
            return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.RED + "Iris" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
        }
        if (warningmode) {
            return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.GOLD + "Iris" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
        }
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.IRIS + "Iris" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";

    }

    private boolean setupChecks() {
        boolean passed = true;
        Iris.info("Version Information: " + instance.getServer().getVersion() + " | " + instance.getServer().getBukkitVersion());
        if (INMS.get() instanceof NMSBinding1X) {
            passed = false;
            Iris.warn("============================================");
            Iris.warn("=");
            Iris.warn("=");
            Iris.warn("=");
            Iris.warn("Iris is not compatible with this version of Minecraft.");
            Iris.warn("=");
            Iris.warn("=");
            Iris.warn("=");
            Iris.warn("============================================");
        }
        if (!instance.getServer().getVersion().contains("Purpur")) {
            passed = false;
            Iris.info("We recommend using Purpur for the best experience with Iris.");
            Iris.info("Purpur is a fork of Paper that is optimized for performance and stability.");
            Iris.info("Plugins that work on Spigot / Paper work on Purpur.");
            Iris.info("You can download it here: https://purpurmc.org");
        }
        return passed;
    }

    private void checkConfigHotload() {
        if (configWatcher.checkModified()) {
            IrisSettings.invalidate();
            IrisSettings.get();
            configWatcher.checkModified();
            Iris.info("Hotloaded settings.json ");
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
                    Iris.reportError(e);
                }
            }
        }
    }

    private void bstats() {
        if (IrisSettings.get().getGeneral().isPluginMetrics()) {
            J.s(() -> new Metrics(Iris.instance, 8757));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return super.onCommand(sender, command, label, args);
    }

    public void imsg(CommandSender s, String msg) {
        s.sendMessage(C.IRIS + "[" + C.DARK_GRAY + "Iris" + C.IRIS + "]" + C.GRAY + ": " + msg);
    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        Iris.debug("Biome Provider Called for " + worldName + " using ID: " + id);
        return super.getDefaultBiomeProvider(worldName, id);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        Iris.debug("Default World Generator Called for " + worldName + " using ID: " + id);
        if (worldName.equals("test")) {
            try {
                throw new RuntimeException();
            } catch (Throwable e) {
                Iris.info(e.getStackTrace()[1].getClassName());
                if (e.getStackTrace()[1].getClassName().contains("com.onarandombox.MultiverseCore")) {
                    Iris.debug("MVC Test detected, Quick! Send them the dummy!");
                    return new DummyChunkGenerator();
                }
            }
        }

        IrisDimension dim;
        if (id == null || id.isEmpty()) {
            dim = IrisData.loadAnyDimension(IrisSettings.get().getGenerator().getDefaultWorldType());
        } else {
            dim = IrisData.loadAnyDimension(id);
        }
        Iris.debug("Generator ID: " + id + " requested by bukkit/plugin");

        if (dim == null) {
            Iris.warn("Unable to find dimension type " + id + " Looking for online packs...");

            service(StudioSVC.class).downloadSearch(new VolmitSender(Bukkit.getConsoleSender()), id, true);
            dim = IrisData.loadAnyDimension(id);

            if (dim == null) {
                throw new RuntimeException("Can't find dimension " + id + "!");
            } else {
                Iris.info("Resolved missing dimension, proceeding with generation.");
            }
        }

        Iris.debug("Assuming IrisDimension: " + dim.getName());

        IrisWorld w = IrisWorld.builder()
                .name(worldName)
                .seed(1337)
                .environment(dim.getEnvironment())
                .worldFolder(new File(Bukkit.getWorldContainer(), worldName))
                .minHeight(dim.getMinHeight())
                .maxHeight(dim.getMaxHeight())
                .build();

        Iris.debug("Generator Config: " + w.toString());

        File ff = new File(w.worldFolder(), "iris/pack");
        if (!ff.exists() || ff.listFiles().length == 0) {
            ff.mkdirs();
            service(StudioSVC.class).installIntoWorld(sender, dim.getLoadKey(), w.worldFolder());
        }

        return new BukkitChunkGenerator(w, false, ff, dim.getLoadKey());
    }

    public void splash() {
        if (!IrisSettings.get().getGeneral().isSplashLogoStartup()) {
            return;
        }

        String padd = Form.repeat(" ", 8);
        String padd2 = Form.repeat(" ", 4);
        String[] info = {"", "", "", "", "", padd2 + C.IRIS + " Iris", padd2 + C.GRAY + " by " + "<rainbow>Volmit Software", padd2 + C.GRAY + " v" + C.IRIS + getDescription().getVersion()};
        if (unstablemode) {
             info = new String[]{"", "", "", "", "", padd2 + C.RED + " Iris", padd2 + C.GRAY + " by " + C.DARK_RED + "Volmit Software", padd2 + C.GRAY + " v" + C.RED + getDescription().getVersion()};
        }
        if (warningmode) {
            info = new String[]{"", "", "", "", "", padd2 + C.GOLD + " Iris", padd2 + C.GRAY + " by " + C.GOLD + "Volmit Software", padd2 + C.GRAY + " v" + C.GOLD + getDescription().getVersion()};
        }

        String[] splashstable = {
                padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@",
                padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + C.IRIS + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + C.IRIS + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + C.IRIS + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + C.IRIS + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + C.IRIS + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + C.IRIS + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@",
                padd + C.GRAY + "@" + C.IRIS + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@",
                padd + C.GRAY + "" + C.IRIS + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@",
                padd + C.GRAY + "" + C.IRIS + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@",
                padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };

        String[] splashunstable = {
                padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@",
                padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + C.RED + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + C.RED + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + C.RED + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + C.RED + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + C.RED + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + C.RED + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@",
                padd + C.GRAY + "@" + C.RED + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@",
                padd + C.GRAY + "" + C.RED + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@",
                padd + C.GRAY + "" + C.RED + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@",
                padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };
        String[] splashwarning = {
                padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@",
                padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + C.GOLD + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + C.GOLD + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + C.GOLD + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + C.GOLD + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + C.GOLD + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + C.GOLD + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@",
                padd + C.GRAY + "@" + C.GOLD + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@",
                padd + C.GRAY + "" + C.GOLD + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@",
                padd + C.GRAY + "" + C.GOLD + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@",
                padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };
        String[] splash;
        File freeSpace = new File(Bukkit.getWorldContainer() + ".");
        if (unstablemode) {
            splash = splashunstable;
        } else if (warningmode) {
            splash = splashwarning;
        } else {
            splash = splashstable;
        }
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        String osArch = osBean.getArch();
        String osName = osBean.getName();

        if (!passedserversoftware) {
            Iris.info("Server type & version: " + C.RED + Bukkit.getVersion());
        } else { Iris.info("Server type & version: " + Bukkit.getVersion()); }
        if (!instance.getServer().getVersion().contains("Purpur")) {
            if (instance.getServer().getVersion().contains("Spigot") && instance.getServer().getVersion().contains("Bukkit")) {
                 Iris.info(C.RED + " Iris requires paper or above to function properly..");
            } else {
                Iris.info(C.YELLOW + "Purpur is recommended to use with iris.");
            }
        }
        Iris.info("Server OS: " + osName + " (" + osArch + ")");

        try {
            if (warningmode){
                Iris.info("Server Cpu: " + C.GOLD + getCPUModel());
            } else {
            if(unstablemode){
                Iris.info("Server Cpu: " + C.DARK_RED + getCPUModel());
            } else {
                if (getCPUModel().contains("Intel")) {
                    Iris.info("Server Cpu: " + C.BLUE + getCPUModel());
                }
                if (getCPUModel().contains("Ryzen")) {
                    Iris.info("Server Cpu: " + C.RED + getCPUModel());
                }
                if (!getCPUModel().contains("Ryzen") && !getCPUModel().contains("Intel")) {
                    Iris.info("Server Cpu: " + C.GRAY + getCPUModel());
                }
            }
            }
        } catch (Exception e){
            Iris.info("Server Cpu: " + C.DARK_RED + "Failed");
        }

        Iris.info("Process Threads: " +  Runtime.getRuntime().availableProcessors());
        Iris.info("Process Memory: " + getHardware.getProcessMemory() + " MB");
        Iris.info("Free DiskSpace: " + Form.ofSize(freeSpace.getFreeSpace(), 1024));
        if (getHardware.getProcessMemory() < 5999) {
            Iris.warn("6GB+ Ram is recommended");
        }
        Iris.info("Bukkit version: " + Bukkit.getBukkitVersion());
        Iris.info("Java version: " + getJavaVersion());
        Iris.info("Custom Biomes: " + INMS.get().countCustomBiomes());
        setupChecks();
        printPacks();

        for (int i = 0; i < info.length; i++) {
            splash[i] += info[i];
        }

        Iris.info("\n\n " + new KList<>(splash).toString("\n") + "\n");
    }

    private void printPacks() {
        File packFolder = Iris.service(StudioSVC.class).getWorkspaceFolder();
        File[] packs = packFolder.listFiles(File::isDirectory);
        if (packs == null || packs.length == 0)
            return;
        Iris.info("Custom Dimensions: " + packs.length);
        for (File f : packs)
            printPack(f);
    }

    private void printPack(File pack) {
        String dimName = pack.getName();
        String version = "???";
        try (FileReader r = new FileReader(new File(pack, "dimensions/" + dimName + ".json"))) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            if (json.has("version"))
                version = json.get("version").getAsString();
        } catch (IOException | JsonParseException ignored) {
        }
        Iris.info("  " + dimName + " v" + version);
    }

    public int getIrisVersion() {
        String input = Iris.instance.getDescription().getVersion();
        int hyphenIndex = input.indexOf('-');
        if (hyphenIndex != -1) {
            String result = input.substring(0, hyphenIndex);
            result = result.replaceAll("\\.", "");
            return Integer.parseInt(result);
        }
        return -1;
    }

    public int getMCVersion() {
        try {
            String version = Bukkit.getVersion();
            Matcher matcher = Pattern.compile("\\(MC: ([\\d.]+)\\)").matcher(version);
            if (matcher.find()) {
                version = matcher.group(1).replaceAll("\\.", "");
                long versionNumber = Long.parseLong(version);
                if (versionNumber > Integer.MAX_VALUE) {
                    return -1;
                }
                return (int) versionNumber;
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
