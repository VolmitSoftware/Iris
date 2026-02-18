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

package art.arcane.iris;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.link.IrisPapiExpansion;
import art.arcane.iris.core.link.MultiverseCoreLink;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.pregenerator.LazyPregenerator;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.EnginePanic;
import art.arcane.iris.engine.object.IrisCompat;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.core.safeguard.IrisSafeguard;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.io.InstanceState;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.common.misc.Bindings;
import art.arcane.iris.util.common.misc.SlimJar;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.plugin.chunk.ChunkTickets;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.common.misc.ServerProperties;
import art.arcane.volmlib.util.scheduling.Queue;
import art.arcane.volmlib.util.scheduling.ShurikenQueue;
import lombok.NonNull;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("CanBeFinal")
public class Iris extends VolmitPlugin implements Listener {
    private static final Queue<Runnable> syncJobs = new ShurikenQueue<>();

    public static Iris instance;
    public static Bindings.Adventure audiences;
    public static MultiverseCoreLink linkMultiverseCore;
    public static IrisCompat compat;
    public static ConfigHotloadEngine configHotloadEngine;
    public static ChunkTickets tickets;
    private static VolmitSender sender;
    private static Thread shutdownHook;
    private static File settingsFile;
    private static final String PENDING_WORLD_DELETE_FILE = "pending-world-deletes.txt";
    private static final Map<String, ChunkGenerator> stagedRuntimeGenerators = new ConcurrentHashMap<>();
    private static final Map<String, BiomeProvider> stagedRuntimeBiomeProviders = new ConcurrentHashMap<>();

    static {
        try {
            InstanceState.updateInstanceId();
        } catch (Throwable ex) {
            System.err.println("[Iris] Failed to update instance id: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

    private final KList<Runnable> postShutdown = new KList<>();
    private KMap<Class<? extends IrisService>, IrisService> services;

    public static VolmitSender getSender() {
        if (sender == null) {
            sender = new VolmitSender(Bukkit.getConsoleSender());
            sender.setTag(instance.getTag());
        }
        return sender;
    }

    public static void stageRuntimeWorldGenerator(@NotNull String worldName, @NotNull ChunkGenerator generator, @Nullable BiomeProvider biomeProvider) {
        stagedRuntimeGenerators.put(worldName, generator);
        if (biomeProvider != null) {
            stagedRuntimeBiomeProviders.put(worldName, biomeProvider);
        } else {
            stagedRuntimeBiomeProviders.remove(worldName);
        }
    }

    @Nullable
    private static ChunkGenerator consumeRuntimeWorldGenerator(@NotNull String worldName) {
        return stagedRuntimeGenerators.remove(worldName);
    }

    @Nullable
    private static BiomeProvider consumeRuntimeBiomeProvider(@NotNull String worldName) {
        return stagedRuntimeBiomeProviders.remove(worldName);
    }

    public static void clearStagedRuntimeWorldGenerator(@NotNull String worldName) {
        stagedRuntimeGenerators.remove(worldName);
        stagedRuntimeBiomeProviders.remove(worldName);
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
                } catch (Throwable ex) {
                    Iris.warn("Skipped class initialization for %s: %s%s",
                            i.getName(),
                            ex.getClass().getSimpleName(),
                            ex.getMessage() == null ? "" : " - " + ex.getMessage());
                    Iris.reportError(ex);
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
                } catch (Throwable ex) {
                    Iris.warn("Skipped class discovery entry for %s: %s%s",
                            i.getName(),
                            ex.getClass().getSimpleName(),
                            ex.getMessage() == null ? "" : " - " + ex.getMessage());
                    Iris.reportError(ex);
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
            getSender().sendMessage(string);
        } catch (Throwable e) {
            try {
                instance.getLogger().info(instance.getTag() + string.replaceAll("(<([^>]+)>)", ""));
            } catch (Throwable inner) {
                System.err.println("[Iris] Failed to emit log message: " + inner.getMessage());
                inner.printStackTrace(System.err);
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
        msg(C.YELLOW + safeFormat(format, objs));
    }

    public static void error(String format, Object... objs) {
        msg(C.RED + safeFormat(format, objs));
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
        msg(C.WHITE + safeFormat(format, args));
    }

    private static String safeFormat(String format, Object... args) {
        if (format == null) {
            return "null";
        }

        if (args == null || args.length == 0) {
            return format;
        }

        try {
            return String.format(format, args);
        } catch (IllegalFormatException ignored) {
            return format;
        }
    }

    @SuppressWarnings("deprecation")
    public static void later(NastyRunnable object) {
        try {
            J.a(() -> {
                try {
                    object.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }, RNG.r.i(100, 1200));
        } catch (IllegalPluginAccessException ex) {
            Iris.verbose("Skipping deferred task registration because plugin access is unavailable: "
                    + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
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

    public static String getJava() {
        String javaRuntimeName = System.getProperty("java.vm.name");
        String javaRuntimeVendor = System.getProperty("java.vendor");
        String javaRuntimeVersion = System.getProperty("java.vm.version");
        return String.format("%s %s (build %s)", javaRuntimeName, javaRuntimeVendor, javaRuntimeVersion);
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
        Bindings.capture(e);
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
            pw.println("[%%__USER__%%,%%__RESOURCE__%%,%%__PRODUCT__%%,%%__BUILTBYBIT__%%]");

            pw.close();
            Iris.info("DUMPED! See " + fi.getAbsolutePath());
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

    public Iris() {
        instance = this;
        SlimJar.load();
    }

    private void enable() {
        services = new KMap<>();
        setupAudience();
        Bindings.setupSentry();
        initialize("art.arcane.iris.core.service").forEach((i) -> services.put((Class<? extends IrisService>) i.getClass(), (IrisService) i));
        IO.delete(new File("iris"));
        compat = IrisCompat.configured(getDataFile("compat.json"));
        ServerConfigurator.configure();
        IrisSafeguard.execute();
        getSender().setTag(getTag());
        IrisSafeguard.splash();
        tickets = new ChunkTickets();
        linkMultiverseCore = new MultiverseCoreLink();
        settingsFile = getDataFile("settings.json");
        configHotloadEngine = new ConfigHotloadEngine(
                Iris::isSettingsFile,
                Iris::knownSettingsFiles,
                Iris::readSettingsContent,
                Iris::normalizeSettingsContent
        );
        configHotloadEngine.configure(3_000L, List.of(settingsFile), List.of());
        services.values().forEach(IrisService::onEnable);
        services.values().forEach(this::registerListener);
        addShutdownHook();
        processPendingStartupWorldDeletes();

        if (J.isFolia()) {
            checkForBukkitWorlds(s -> true);
        }

        J.s(() -> {
            J.a(() -> IO.delete(getTemp()));
            J.a(LazyPregenerator::loadLazyGenerators, 100);
            J.a(this::bstats);
            J.ar(this::checkConfigHotload, 60);
            J.sr(this::tickQueue, 0);
            J.s(this::setupPapi);
            J.a(ServerConfigurator::configure, 20);

            autoStartStudio();
            if (!J.isFolia()) {
                checkForBukkitWorlds(s -> true);
            }
            IrisToolbelt.retainMantleDataForSlice(String.class.getCanonicalName());
            IrisToolbelt.retainMantleDataForSlice(BlockData.class.getCanonicalName());
        });
    }

    public void addShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ex) {
                Iris.debug("Skipping shutdown hook replacement because JVM shutdown is already in progress.");
                return;
            }
        }
        shutdownHook = new Thread(() -> {
            Bukkit.getWorlds()
                    .stream()
                    .map(IrisToolbelt::access)
                    .filter(Objects::nonNull)
                    .forEach(PlatformChunkGenerator::close);

            MultiBurst.burst.close();
            MultiBurst.ioBurst.close();
            if (services != null) {
                services.clear();
            }
        }, "Iris-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ex) {
            Iris.debug("Skipping shutdown hook registration because JVM shutdown is already in progress.");
        }
    }

    public void checkForBukkitWorlds(Predicate<String> filter) {
        try {
            KList<String> deferredStartupWorlds = new KList<>();
            IrisWorlds.readBukkitWorlds().forEach((s, generator) -> {
                try {
                    if (Bukkit.getWorld(s) != null || !filter.test(s)) return;

                    Iris.info("Loading World: %s | Generator: %s", s, generator);
                    var gen = getDefaultWorldGenerator(s, generator);
                    var dim = loadDimension(s, generator);
                    assert dim != null && gen != null;

                    Iris.info(C.LIGHT_PURPLE + "Preparing Spawn for " + s + "' using Iris:" + generator + "...");
                    WorldCreator c = new WorldCreator(s)
                            .generator(gen)
                            .environment(dim.getEnvironment());
                    INMS.get().createWorld(c);
                    Iris.info(C.LIGHT_PURPLE + "Loaded " + s + "!");
                } catch (Throwable e) {
                    if (containsCreateWorldUnsupportedOperation(e)) {
                        if (J.isFolia()) {
                            if (!deferredStartupWorlds.contains(s)) {
                                deferredStartupWorlds.add(s);
                            }
                            return;
                        }
                        Iris.error("Failed to load world " + s + "!");
                        Iris.error("This server denied Bukkit.createWorld for \"" + s + "\" at the current startup phase.");
                        Iris.error("Ensure Iris is loaded at STARTUP and restart after staging worlds in bukkit.yml.");
                        reportError(e);
                        return;
                    }
                    Iris.error("Failed to load world " + s + "!");
                    e.printStackTrace();
                }
            });
            if (!deferredStartupWorlds.isEmpty()) {
                Iris.warn("World init delayed on Folia until server world-init phase for staged Iris worlds: %s", String.join(", ", deferredStartupWorlds));
                Iris.warn("Bukkit.createWorld is intentionally unavailable in this startup phase. Worlds remain staged in bukkit.yml.");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            reportError(e);
        }
    }

    private static boolean containsCreateWorldUnsupportedOperation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException || cursor instanceof IllegalStateException) {
                for (StackTraceElement element : cursor.getStackTrace()) {
                    if ("org.bukkit.craftbukkit.CraftServer".equals(element.getClassName())
                            && "createWorld".equals(element.getMethodName())) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    public static synchronized int queueWorldDeletionOnStartup(Collection<String> worldNames) throws IOException {
        if (instance == null || worldNames == null || worldNames.isEmpty()) {
            return 0;
        }

        LinkedHashMap<String, String> queue = loadPendingWorldDeleteMap();
        int before = queue.size();

        for (String worldName : worldNames) {
            String normalized = normalizeWorldName(worldName);
            if (normalized == null) {
                continue;
            }
            queue.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }

        if (queue.size() != before) {
            writePendingWorldDeleteMap(queue);
        }

        return queue.size() - before;
    }

    private void processPendingStartupWorldDeletes() {
        try {
            LinkedHashMap<String, String> queue = loadPendingWorldDeleteMap();
            if (queue.isEmpty()) {
                return;
            }

            LinkedHashMap<String, String> remaining = new LinkedHashMap<>();
            for (String worldName : queue.values()) {
                if (worldName.equalsIgnoreCase(ServerProperties.LEVEL_NAME)) {
                    Iris.warn("Skipping queued deletion for \"" + worldName + "\" because it is configured as level-name.");
                    continue;
                }

                if (Bukkit.getWorld(worldName) != null) {
                    Iris.warn("Skipping queued deletion for \"" + worldName + "\" because it is currently loaded.");
                    remaining.put(worldName.toLowerCase(Locale.ROOT), worldName);
                    continue;
                }

                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                if (!worldFolder.exists()) {
                    Iris.info("Queued world deletion skipped for \"" + worldName + "\" (folder missing).");
                    continue;
                }

                IO.delete(worldFolder);
                if (worldFolder.exists()) {
                    Iris.warn("Failed to delete queued world folder \"" + worldName + "\". Retrying on next startup.");
                    remaining.put(worldName.toLowerCase(Locale.ROOT), worldName);
                    continue;
                }

                Iris.info("Deleted queued world folder \"" + worldName + "\".");
            }

            writePendingWorldDeleteMap(remaining);
        } catch (Throwable e) {
            Iris.error("Failed to process queued startup world deletions.");
            reportError(e);
            e.printStackTrace();
        }
    }

    private static LinkedHashMap<String, String> loadPendingWorldDeleteMap() throws IOException {
        LinkedHashMap<String, String> queue = new LinkedHashMap<>();
        if (instance == null) {
            return queue;
        }

        File queueFile = instance.getDataFile(PENDING_WORLD_DELETE_FILE);
        if (!queueFile.exists()) {
            return queue;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(queueFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeWorldName(line);
                if (normalized == null) {
                    continue;
                }
                queue.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }

        return queue;
    }

    private static void writePendingWorldDeleteMap(Map<String, String> queue) throws IOException {
        if (instance == null) {
            return;
        }

        File queueFile = instance.getDataFile(PENDING_WORLD_DELETE_FILE);
        if (queue.isEmpty()) {
            if (queueFile.exists()) {
                IO.delete(queueFile);
            }
            return;
        }

        File parent = queueFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create queue directory: " + parent.getAbsolutePath());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(queueFile))) {
            for (String worldName : queue.values()) {
                writer.println(worldName);
            }
        }
    }

    @Nullable
    private static String normalizeWorldName(String worldName) {
        if (worldName == null) {
            return null;
        }

        String trimmed = worldName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }

    private void autoStartStudio() {
        if (IrisSettings.get().getStudio().isAutoStartDefaultStudio()) {
            Iris.info("Starting up auto Studio!");
            try {
                Player r = new KList<>(getServer().getOnlinePlayers()).getRandom();
                Iris.service(StudioSVC.class).open(r != null ? new VolmitSender(r) : getSender(), 1337, IrisSettings.get().getGenerator().getDefaultWorldType(), (w) -> {
                    J.s(() -> {
                        final Location spawn = w.getSpawnLocation();
                        for (Player i : getServer().getOnlinePlayers()) {
                            final Runnable playerTask = () -> {
                                i.setGameMode(GameMode.SPECTATOR);
                                i.teleport(spawn);
                            };
                            if (!J.runEntity(i, playerTask)) {
                                playerTask.run();
                            }
                        }
                    });
                });
            } catch (IrisException e) {
                reportError(e);
            }
        }
    }

    private void setupAudience() {
        try {
            audiences = new Bindings.Adventure(this);
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
    }

    public void onDisable() {
        if (IrisSafeguard.isForceShutdown()) return;
        if (services != null) {
            services.values().forEach(IrisService::onDisable);
        }
        if (configHotloadEngine != null) {
            configHotloadEngine.clear();
            configHotloadEngine = null;
        }
        J.cancelPluginTasks();
        HandlerList.unregisterAll((Plugin) this);
        postShutdown.forEach(Runnable::run);
        super.onDisable();

        J.attempt(new JarScanner(instance.getJarFile(), "", false)::scanAll);
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
        return IrisSafeguard.mode().tag(subTag);
    }

    private void checkConfigHotload() {
        if (configHotloadEngine == null) {
            return;
        }

        for (File file : configHotloadEngine.pollTouchedFiles()) {
            configHotloadEngine.processFileChange(file, ignored -> {
                IrisSettings.invalidate();
                IrisSettings.get();
                return true;
            }, ignored -> Iris.info("Hotloaded settings.json "));
        }
    }

    private static boolean isSettingsFile(File file) {
        if (file == null || settingsFile == null) {
            return false;
        }
        return settingsFile.getAbsoluteFile().equals(file.getAbsoluteFile());
    }

    private static List<File> knownSettingsFiles() {
        if (settingsFile == null) {
            return List.of();
        }
        return List.of(settingsFile);
    }

    private static String readSettingsContent(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        try {
            return IO.readAll(file);
        } catch (Throwable ex) {
            Iris.warn("Failed to read settings file %s: %s%s",
                    file.getAbsolutePath(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? "" : " - " + ex.getMessage());
            Iris.reportError(ex);
            return null;
        }
    }

    private static String normalizeSettingsContent(String text) {
        if (text == null) {
            return null;
        }

        return text.replace("\r\n", "\n").trim();
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
            Bindings.setupBstats(this);
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
        BiomeProvider stagedBiomeProvider = consumeRuntimeBiomeProvider(worldName);
        if (stagedBiomeProvider != null) {
            Iris.debug("Using staged runtime biome provider for " + worldName);
            return stagedBiomeProvider;
        }
        Iris.debug("Biome Provider Called for " + worldName + " using ID: " + id);
        return super.getDefaultBiomeProvider(worldName, id);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        ChunkGenerator stagedGenerator = consumeRuntimeWorldGenerator(worldName);
        if (stagedGenerator != null) {
            Iris.debug("Using staged runtime generator for " + worldName);
            return stagedGenerator;
        }
        Iris.debug("Default World Generator Called for " + worldName + " using ID: " + id);
        if (id == null || id.isEmpty()) id = IrisSettings.get().getGenerator().getDefaultWorldType();
        Iris.debug("Generator ID: " + id + " requested by bukkit/plugin");
        IrisDimension dim = loadDimension(worldName, id);
        if (dim == null) {
            throw new RuntimeException("Can't find dimension " + id + "!");
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
        var files = ff.listFiles();
        if (files == null || files.length == 0)
            IO.delete(ff);

        if (!ff.exists()) {
            ff.mkdirs();
            service(StudioSVC.class).installIntoWorld(getSender(), dim.getLoadKey(), w.worldFolder());
        }

        return new BukkitChunkGenerator(w, false, ff, dim.getLoadKey());
    }

    @Nullable
    public static IrisDimension loadDimension(@NonNull String worldName, @NonNull String id) {
        File pack = new File(Bukkit.getWorldContainer(), String.join(File.separator, worldName, "iris", "pack"));
        var dimension = pack.isDirectory() ? IrisData.get(pack).getDimensionLoader().load(id) : null;
        if (dimension == null) dimension = IrisData.loadAnyDimension(id, null);
        if (dimension == null) {
            Iris.warn("Unable to find dimension type " + id + " Looking for online packs...");
            Iris.service(StudioSVC.class).downloadSearch(new VolmitSender(Bukkit.getConsoleSender()), id, false);
            dimension = IrisData.loadAnyDimension(id, null);

            if (dimension != null) {
                Iris.info("Resolved missing dimension, proceeding.");
            }
        }

        return dimension;
    }

    public void splash() {
        Iris.info("Custom Biomes: " + INMS.get().countCustomBiomes());
        printPacks();

        IrisSafeguard.mode().trySplash();
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
        } catch (IOException | JsonParseException ex) {
            Iris.verbose("Failed to read dimension version metadata for " + dimName + ": "
                    + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
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
