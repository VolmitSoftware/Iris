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

import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.iris.generation.runtime.IrisEngineEffects;
import art.arcane.iris.generation.runtime.IrisWorldManager;

import art.arcane.iris.generation.runtime.EngineComponentCleanup;
import art.arcane.iris.generation.runtime.EngineEffectsProvider;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineWorldManagerProvider;
import art.arcane.iris.diagnostics.splash.IrisSplashComposer;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.world.IrisStartupAdmissionListener;
import art.arcane.iris.world.BukkitWorldReconciler;
import art.arcane.iris.world.IrisWorldGeneratorResolver;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.PendingWorldDeleteQueue;
import art.arcane.iris.world.PendingWorldReplacementManager;
import art.arcane.iris.configuration.SettingsHotloadWatch;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.iris.pack.datapack.DatapackIngestService.StartupValidationOutcome;
import art.arcane.iris.world.lifecycle.IrisRuntimeStatics;
import art.arcane.iris.world.lifecycle.ManagedWorldLoader;
import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import art.arcane.iris.world.lifecycle.PaperLibBootstrap;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.iris.world.runtime.BukkitEnginePlatformHooks;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.integration.IrisPapiInstaller;
import art.arcane.iris.integration.IrisPapiListener;
import art.arcane.iris.integration.IrisPapiState;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.iris.integration.MultiverseCoreLink;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.ServerShutdownBoundary;
import art.arcane.iris.studio.view.BukkitGuiHost;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.platform.bukkit.BoardSVC;
import art.arcane.iris.command.CommandSVC;
import art.arcane.iris.pack.datapack.DatapackStructureScopeSVC;
import art.arcane.iris.studio.edit.EditSVC;
import art.arcane.iris.world.entity.EntityRiseSVC;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.generation.runtime.GlobalCacheSVC;
import art.arcane.iris.platform.bukkit.api.IrisApiEventSVC;
import art.arcane.iris.generation.runtime.IrisEngineSVC;
import art.arcane.iris.integration.IrisIntegrationService;
import art.arcane.iris.platform.protocol.IrisProtocolService;
import art.arcane.iris.platform.bukkit.api.IrisTerrainSVC;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.iris.diagnostics.LogFilterSVC;
import art.arcane.iris.integration.MultiverseSVC;
import art.arcane.iris.studio.object.ObjectSVC;
import art.arcane.iris.studio.object.ObjectStudioSaveService;
import art.arcane.iris.generation.runtime.PreservationSVC;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.world.tree.TreeFellerSVC;
import art.arcane.iris.world.tree.TreeSVC;
import art.arcane.iris.studio.wand.WandSVC;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.EnginePanic;
import art.arcane.iris.generation.runtime.BlockEditAccess;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.pack.validation.IrisCompat;
import art.arcane.iris.world.safeguard.IrisSafeguard;
import art.arcane.iris.world.safeguard.RuntimeLockNotice;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.LogLevel;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.io.InstanceState;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.iris.platform.bootstrap.Bindings;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.iris.platform.bootstrap.SlimJar;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.platform.bukkit.chunk.ChunkTickets;
import art.arcane.iris.world.task.J;
import art.arcane.iris.generation.simd.SimdSupport;
import art.arcane.volmlib.util.scheduling.Queue;
import art.arcane.volmlib.util.scheduling.ShurikenQueue;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("CanBeFinal")
public class Iris extends VolmitPlugin implements Listener, ReloadAware {
    private static final long SERVER_SHUTDOWN_BOUNDARY_TIMEOUT_SECONDS = 300L;
    private static final PrintStream SHUTDOWN_ERRORS = new PrintStream(new FileOutputStream(FileDescriptor.err), true);
    private static final long SERVER_STOP_PREGEN_TIMEOUT_MILLIS = 30000L;
    private static final Queue<Runnable> syncJobs = new ShurikenQueue<>();

    static {
        System.setProperty("iris.cache.fast", "true");
    }

    public static Iris instance;
    public static MultiverseCoreLink linkMultiverseCore;
    public static IrisCompat compat;
    public static ChunkTickets tickets;
    private static VolmitSender sender;
    private static Thread shutdownHook;
    private static final StackWalker DEBUG_STACK_WALKER = StackWalker.getInstance();
    static {
        try {
            InstanceState.updateInstanceId();
        } catch (Throwable ex) {
            IrisLogging.reportError("Failed to update the Iris instance id.", ex);
        }
    }

    private static final Object TEARDOWN_LOCK = new Object();
    private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
    private final AtomicBoolean postStopFinisherStarted = new AtomicBoolean(false);
    private final AtomicBoolean serverStopTeardownDeferred = new AtomicBoolean(false);
    private final AtomicBoolean servicesDisabled = new AtomicBoolean(false);
    private final AtomicBoolean sharedRuntimeClosed = new AtomicBoolean(false);
    private final AtomicBoolean startupBoundaryRestart = new AtomicBoolean(false);
    private final AtomicBoolean terminalCleanupCompleted = new AtomicBoolean(false);
    private final AtomicBoolean runtimeTeardownFailed = new AtomicBoolean(false);
    private volatile boolean generatorDrainCompleted;
    private volatile PlaceholderRegistration papiRegistration;
    private volatile IrisPapiListener papiListener;
    private volatile IrisPapiState papiState;
    private KMap<Class<? extends IrisService>, IrisService> services;
    // Copy-on-write: mutated on the main thread during enable() and iterated by the JVM
    // shutdown-hook thread during teardown; a plain list would CME and abort the teardown.
    private final List<IrisService> enabledServices = new CopyOnWriteArrayList<>();
    private final List<PlatformChunkGenerator> deferredShutdownGenerators = new CopyOnWriteArrayList<>();
    private final IrisWorldGeneratorResolver generatorResolver = new IrisWorldGeneratorResolver(this);
    private final BukkitWorldReconciler worldReconciler = new BukkitWorldReconciler(this);
    private final PendingWorldDeleteQueue pendingWorldDeletes = new PendingWorldDeleteQueue(this);
    private final PendingWorldReplacementManager pendingWorldReplacements = new PendingWorldReplacementManager(this);
    private BukkitLanguageSwitcher languageSwitcher;
    private BukkitDebugDump debugDump;
    private volatile SettingsHotloadWatch settingsHotloadWatch;
    private volatile Thread serverLifecycleThread;
    private volatile ServerShutdownBoundary serverShutdownBoundary;

    public static VolmitSender getSender() {
        VolmitSender current = sender;
        if (current == null) {
            Iris plugin = instance;
            current = new VolmitSender(Bukkit.getConsoleSender());
            current.setTag(plugin == null ? IrisSafeguard.mode().tag("") : plugin.getTag());
            sender = current;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public static <T> T service(Class<T> c) {
        Iris plugin = instance;
        if (plugin == null || plugin.services == null) {
            throw new IllegalStateException("Iris is disabled; " + c.getSimpleName() + " is unavailable");
        }
        return (T) plugin.services.get(c);
    }

    public static void callEvent(Event e) {
        Runnable dispatcher = () -> {
            try {
                Bukkit.getPluginManager().callEvent(e);
            } catch (Throwable ex) {
                reportError("Event dispatch failed for \"" + e.getEventName() + "\".", ex);
                if (ex instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (ex instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(ex);
            }
        };
        if (!e.isAsynchronous()) {
            J.s(dispatcher);
        } else {
            dispatcher.run();
        }
    }

    static boolean isConcreteImplementation(Class<?> candidate, Class<?> requiredType) {
        int modifiers = candidate.getModifiers();
        return requiredType.isAssignableFrom(candidate)
                && !candidate.isInterface()
                && !Modifier.isAbstract(modifiers);
    }

    public static void sq(Runnable r) {
        synchronized (syncJobs) {
            syncJobs.queue(r);
        }
    }

    public static File getTemp() {
        Iris plugin = instance;
        if (plugin == null) {
            throw new IllegalStateException("Iris is disabled; the temp folder is unavailable");
        }
        return plugin.getDataFolder("cache", "temp");
    }

    public static void msg(String string) {
        try {
            Iris plugin = instance;
            ComponentLog.logMarkup(
                    plugin,
                    Logger.getLogger("Iris"),
                    logPrefix(plugin),
                    Level.INFO,
                    string,
                    null);
        } catch (Throwable e) {
            try {
                Iris plugin = instance;
                String plainPrefix = ComponentText.legacy(logPrefix(plugin)).plain();
                Logger.getLogger("Iris").log(Level.INFO, plainPrefix + IrisLogging.clean(string));
            } catch (Throwable inner) {
                System.err.println("[Iris] Failed to emit log message: " + inner.getMessage());
                inner.printStackTrace(System.err);
            }
        }
    }

    /**
     * A warning raised here is the same kind of thing as one raised in core, so it takes the same route: the
     * plugin logger at WARNING, where a log scan of logs/latest.log finds it.
     */
    public static void warn(String format, Object... objs) {
        diagnostic(Level.WARNING, safeFormat(format, objs));
    }

    public static void error(String format, Object... objs) {
        diagnostic(Level.SEVERE, safeFormat(format, objs));
    }

    public static void debug(String string) {
        if (!IrisSettings.get().getGeneral().isDebug()) {
            return;
        }

        StackWalker.StackFrame frame;
        try {
            frame = DEBUG_STACK_WALKER.walk(stream -> stream.skip(1).findFirst().orElse(null));
        } catch (Throwable unavailable) {
            frame = null;
        }

        if (frame == null) {
            debug("Origin", -1, string);
            return;
        }

        String className = frame.getClassName();
        String[] cc = className == null ? new String[0] : className.split("\\Q.\\E");
        int line = frame.getLineNumber();

        if (cc.length > 5) {
            debug(cc[3] + "/" + cc[4] + "/" + cc[cc.length - 1], line, string);
            return;
        }

        if (cc.length > 4) {
            debug(cc[3] + "/" + cc[4], line, string);
            return;
        }

        if (cc.length > 0) {
            debug(cc[cc.length - 1], line, string);
            return;
        }

        debug("Origin", line, string);
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
        return IrisLogging.format(format, args);
    }

    public static void later(NastyRunnable object) {
        try {
            J.a(() -> {
                try {
                    object.run();
                } catch (Throwable e) {
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
        if (e == null) {
            return;
        }
        if (instance != null && instance.serverStopTeardownDeferred.get()) {
            e.printStackTrace(SHUTDOWN_ERRORS);
            return;
        }

        boolean debug = false;
        if (instance != null) {
            try {
                IrisSettings currentSettings = IrisSettings.settings != null ? IrisSettings.settings : IrisSettings.get();
                debug = currentSettings != null && currentSettings.getGeneral().isDebug();
            } catch (Throwable unreadable) {
                debug = false;
            }
        }

        if (debug) {
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

        e.printStackTrace(System.err);
    }

    public static void reportError(String context, Throwable e) {
        Throwable error = e == null ? new IllegalStateException("Unknown Iris failure") : e;
        String message = context == null || context.isBlank() ? "Unhandled Iris failure." : context;
        if (instance != null && instance.serverStopTeardownDeferred.get()) {
            SHUTDOWN_ERRORS.println("[Iris] " + message);
            error.printStackTrace(SHUTDOWN_ERRORS);
            return;
        }

        try {
            if (instance != null) {
                Iris.error(message);
            } else {
                System.err.println("[Iris] " + message);
            }
        } catch (Throwable inner) {
            System.err.println("[Iris] " + message);
            inner.printStackTrace(System.err);
        }

        reportError(error);
    }

    public static void dump() {
        try {
            File fi = Iris.instance.getDataFile("dump", "td-" + new java.sql.Date(M.ms()) + ".txt");
            FileOutputStream fos = new FileOutputStream(fi);
            Map<Thread, StackTraceElement[]> f = Thread.getAllStackTraces();
            PrintWriter pw = new PrintWriter(fos);
            for (Thread i : f.keySet()) {
                pw.println("========================================");
                pw.println("Thread: '" + i.getName() + "' ID: " + i.threadId() + " STATUS: " + i.getState().name());

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
            Iris.reportError("Failed to write the Iris thread dump.", e);
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
        BukkitPlatform.hostPlugin(this);
        BukkitPlatform.hostConsoleSender(Iris::getSender);
        BukkitPlatform.hostBridge(new BukkitPlatform.HostBridge(
                Iris::bridgeLog,
                Iris::msg,
                Iris::reportError,
                (event) -> Iris.callEvent((org.bukkit.event.Event) event),
                this::getDataFolder,
                this::getDataFile,
                this::getJarFile,
                this::getIrisVersion,
                this::getMCVersion));
        SlimJar.load();
    }

    private static void bridgeLog(LogLevel level, String message) {
        LogLevel target = level == null ? LogLevel.INFO : level;
        Level diagnostic = diagnosticLevel(target);
        if (diagnostic != null) {
            diagnostic(diagnostic, message);
            return;
        }
        if (target == LogLevel.DEBUG) {
            Iris.debug(message);
            return;
        }
        Iris.info(message);
    }

    /**
     * The java.util.logging level a message keeps, or null when it belongs on the console sender path.
     * <p>
     * Core states a severity and every other adapter honours it; on Bukkit a coloured line through the
     * console sender reaches the terminal but not the instance's logs/latest.log, so a WARN-level scan of
     * that file never saw a single core warning. Diagnostics go to the plugin logger at their own level
     * instead, and NOTICE carries the handful of lifecycle lines that have to land there too. Player-facing
     * text still goes through {@code IrisLogging.msg}.
     */
    static Level diagnosticLevel(LogLevel level) {
        return switch (level) {
            case NOTICE -> Level.INFO;
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
            case DEBUG, INFO -> null;
        };
    }

    private static void diagnostic(Level level, String message) {
        String line = IrisLogging.clean(message);
        Iris plugin = instance;
        ComponentLog.log(
                plugin,
                Logger.getLogger("Iris"),
                logPrefix(plugin),
                level,
                ComponentText.literal(line),
                null);
    }

    private static String logPrefix(Iris plugin) {
        return plugin == null ? ComponentLog.discriminator("Iris", "&a") : plugin.getTag();
    }

    /**
     * @return false when the bootstrap was aborted (unsupported server version); the caller
     * must bail out of onEnable without touching any further setup.
     */
    private boolean enable() {
        if (!INMS.isBound()) {
            Throwable bindFailure = INMS.bindFailure();
            Iris.error("Iris cannot start: " + (bindFailure == null
                    ? "no NMS binding is available for this server version."
                    : bindFailure.getMessage()));
            // Deferred one tick: disablePlugin from inside onEnable re-enters onDisable
            // synchronously and the loader then continues registering the half-enabled plugin.
            J.s(() -> Bukkit.getPluginManager().disablePlugin(this), 1);
            return false;
        }
        EnableTimings timings = new EnableTimings();
        alreadyDrained.set(false);
        postStopFinisherStarted.set(false);
        serverStopTeardownDeferred.set(false);
        servicesDisabled.set(false);
        sharedRuntimeClosed.set(false);
        startupBoundaryRestart.set(false);
        terminalCleanupCompleted.set(false);
        runtimeTeardownFailed.set(false);
        generatorDrainCompleted = false;
        deferredShutdownGenerators.clear();
        MultiBurst.burst.reopen();
        MultiBurst.ioBurst.reopen();
        IrisLanguage.initialize();
        debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(
                () -> true,
                () -> IrisSafeguard::debugReport));
        languageSwitcher = BukkitLanguageSwitcher.register(this, IrisLanguage.selections(),
                new BukkitLanguageSwitcher.Options("iris", "iris.all",
                        DirectorMiniMenu.Theme.irisGreen(), IrisLanguage.directorResolver(), IrisLanguage.editorOptions()));
        PaperLibBootstrap.install();
        SimdSupport.install();
        timings.mark("bootstrap");
        services = new KMap<>();
        BukkitPlatform.hostHud(new HudActionBar(this), new HudBossBarLane());
        // Explicit, ordered service list: the previous reflective jar scan gave hash-ordered
        // enable/disable and paid a full-jar class sweep at boot. Infrastructure first,
        // engine/world services next, commands last.
        List<IrisService> orderedServices = List.of(
                new PreservationSVC(),
                new GlobalCacheSVC(),
                new LogFilterSVC(),
                new ExternalDataSVC(),
                new EditSVC(),
                new ObjectSVC(),
                new ObjectStudioSaveService(),
                new JigsawStudioService(),
                new StudioSVC(),
                new DatapackStructureScopeSVC(),
                new IrisEngineSVC(),
                new IrisTerrainSVC(),
                new TreeSVC(),
                new TreeFellerSVC(),
                new EntityRiseSVC(),
                new WandSVC(),
                new BoardSVC(),
                new IrisIntegrationService(),
                new MultiverseSVC(),
                new IrisProtocolService(),
                new IrisApiEventSVC(),
                new CommandSVC()
        );
        for (IrisService i : orderedServices) {
            Class<? extends IrisService> serviceType = i.getClass().asSubclass(IrisService.class);
            services.put(serviceType, i);
            IrisServices.register(serviceType, i);
        }
        IrisServices.register(BlockEditAccess.class, services.get(EditSVC.class));
        IrisServices.register(PreservationRegistry.class, services.get(PreservationSVC.class));
        compat = IrisCompat.configured(getDataFile("compat.json"));
        IrisServices.register(IrisCompat.class, compat);
        timings.mark("services");
        ServerConfigurator.configure();
        timings.mark("serverConfig");
        StartupValidationOutcome datapackValidation = DatapackIngestService.validateOnStartup();
        timings.mark("datapacks");
        IrisSafeguard.execute();
        timings.mark("safeguard");
        getSender().setTag(getTag());
        // A cosmetic banner must never abort the bootstrap.
        J.attempt(this::splash);
        IrisSafeguard.printReports();
        IrisSafeguard.printFooter();
        // Paper's bootstrap runs before any plugin logger exists, so orphan-storage warnings raised there
        // never reach logs/latest.log. Replay them once now that the platform log is up.
        MissingWorldStorageLog.replayToPlatformLog();
        timings.mark("splash");
        tickets = new ChunkTickets();
        linkMultiverseCore = new MultiverseCoreLink();
        IrisServices.register(MultiverseCoreLink.class, linkMultiverseCore);
        IrisServices.register(EngineComponentCleanup.class, (EngineComponentCleanup) BukkitPlatform::unregisterListener);
        IrisServices.register(EngineEffectsProvider.class, (EngineEffectsProvider) IrisEngineEffects::new);
        IrisServices.register(EnginePlatformHooks.class, new BukkitEnginePlatformHooks());
        IrisServices.register(EngineWorldManagerProvider.class,
                (EngineWorldManagerProvider) IrisWorldManager::new);
        IrisServices.register(WorldDeletionQueue.class, pendingWorldDeletes);
        IrisServices.register(ManagedWorldLoader.class, (ManagedWorldLoader) this::loadManagedWorld);
        SettingsHotloadWatch watch = new SettingsHotloadWatch(getDataFile("iris.json"));
        settingsHotloadWatch = watch;
        StudioSVC.gateDownloadsOnStaleTempCleanup(MultiBurst.ioBurst.completeValueAsync(() -> {
            IO.delete(getTemp());
            return null;
        }));
        timings.mark("tempSweep");
        // One throwing service must not abort the bootstrap: the steps after this loop
        // (listeners, shutdown hook, replacement journals) are the safety-critical ones.
        // Only services that actually enabled get listeners and a later onDisable.
        enabledServices.clear();
        for (IrisService service : orderedServices) {
            long serviceStartedAt = System.nanoTime();
            try {
                service.onEnable();
                enabledServices.add(service);
                timings.markService(service.getClass().getSimpleName(), serviceStartedAt);
            } catch (Throwable e) {
                // A service failure is NOT a datapack validation failure: the admission gate
                // must never lock every login over a broken cosmetic service. Log loudly,
                // continue degraded, and clean up whatever the partial onEnable started
                // (a failed service is excluded from the teardown loop).
                Iris.reportError("Failed to enable " + service.getClass().getSimpleName() + "; continuing with a degraded runtime.", e);
                try {
                    service.onDisable();
                } catch (Throwable cleanup) {
                    Iris.reportError("Failed to clean up partially enabled " + service.getClass().getSimpleName() + ".", cleanup);
                }
            }
        }
        for (IrisService service : enabledServices) {
            try {
                registerListener(service);
            } catch (Throwable e) {
                Iris.reportError("Failed to register listener for " + service.getClass().getSimpleName() + ".", e);
            }
        }
        timings.mark("serviceEnable");
        if (datapackValidation == StartupValidationOutcome.READY) {
            IrisServices.get(ExternalDataSVC.class).setContentChangeListener(generatorResolver::requestExternalContentRefresh);
            generatorResolver.validateAllPacks();
        }
        timings.mark("packValidation");
        addShutdownHook();
        pendingWorldReplacements.processPendingStartupReplacements();
        pendingWorldDeletes.processPendingStartupWorldDeletes();

        if (J.isFolia() && IrisStartupValidation.isReady()) {
            J.s(this::reconcileStartupWorlds, 1);
        }

        J.s(() -> {
            pendingWorldReplacements.captureVanillaLevelContext();
            pendingWorldReplacements.verifyLoadedPublishedWorlds();
            J.a(this::bstats);
            J.ar(watch::checkConfigHotload, 10);
            J.sr(this::tickQueue, 0);
            J.s(this::setupPapi);
            if (IrisStartupValidation.isReady()) {
                J.a(DatapackIngestService::runPostStartupTasks, 60);
                autoStartStudio();
            }
            if (!J.isFolia() && IrisStartupValidation.isReady()) {
                reconcileStartupWorlds();
            }
            IrisToolbelt.retainMantleDataForSlice(String.class.getCanonicalName());
            // The mantle stores block values as PlatformBlockState, so a BlockData retention can never
            // match a slice type; the block-state slice is deliberately never retainable (regenerable, huge).
            IrisToolbelt.retainMantleDataForSlice(TreeBlockMaterial.class.getCanonicalName());
        });
        timings.mark("startupTasks");
        timings.report();
        return true;
    }

    private static final class EnableTimings {
        private static final int REPORTED_SERVICES = 3;

        private final long startedAt = System.nanoTime();
        private final StringBuilder phases = new StringBuilder();
        private final Map<String, Long> serviceMillis = new LinkedHashMap<>();
        private long lastMark = startedAt;

        private void mark(String phase) {
            long now = System.nanoTime();
            if (!phases.isEmpty()) {
                phases.append(' ');
            }
            phases.append(phase).append('=').append(TimeUnit.NANOSECONDS.toMillis(now - lastMark)).append("ms");
            lastMark = now;
        }

        private void markService(String service, long serviceStartedAt) {
            serviceMillis.put(service, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - serviceStartedAt));
        }

        private void report() {
            IrisLogging.notice("Enabled in %dms (%s)",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), phases);
            String slowest = serviceMillis.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(REPORTED_SERVICES)
                    .map(entry -> entry.getKey() + "=" + entry.getValue() + "ms")
                    .collect(Collectors.joining(" "));
            if (!slowest.isBlank()) {
                IrisLogging.notice("Slowest services: %s", slowest);
            }
        }
    }

    private void reconcileStartupWorlds() {
        generatorResolver.startupWorldsReady().whenComplete((ignored, failure) -> {
            if (failure != null) {
                Iris.reportError("Could not resume Iris startup world reconciliation.", failure);
                return;
            }
            J.s(() -> worldReconciler.checkForBukkitWorlds(s -> true));
        });
    }

    public void addShutdownHook() {
        removeShutdownHook();
        serverLifecycleThread = Thread.currentThread();
        serverShutdownBoundary = INMS.isBound() ? INMS.get().createServerShutdownBoundary() : null;
        shutdownHook = new Thread(this::runShutdownHook, "Iris-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ex) {
            Iris.debug("Skipping shutdown hook registration because JVM shutdown is already in progress.");
        }
    }

    /**
     * The static-field guard in addShutdownHook is dead across a plugin reload (fresh
     * classloader, fresh static), so onDisable must deregister the hook explicitly or each
     * reload stacks another hook pinning the previous plugin classloader for the JVM's life.
     */
    public void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            Iris.debug("Skipping shutdown hook removal because JVM shutdown is already in progress.");
        }
    }

    public BukkitWorldReconciler worldReconciler() {
        return worldReconciler;
    }

    /**
     * The load every integration goes through. {@code /iris load} and the Multiverse guard both land here,
     * so a world loaded from outside Iris still gets the keyed creator, the pack's environment and the
     * bukkit.yml reconciliation that make it an Iris world rather than a vanilla one under the same name.
     */
    private CompletableFuture<ManagedWorldLoader.ManagedWorldLoad> loadManagedWorld(String configuredWorldName) {
        return worldReconciler.loadWorld(ServerProperties.BUKKIT_YML, configuredWorldName)
                .thenApply(result -> new ManagedWorldLoader.ManagedWorldLoad(result.succeeded(), result.message()));
    }

    public PendingWorldReplacementManager pendingWorldReplacements() {
        return pendingWorldReplacements;
    }

    private void autoStartStudio() {
        if (IrisSettings.get().getStudio().isAutoStartDefaultStudio()) {
            Iris.debug("Starting up auto Studio!");
            try {
                Player r = new KList<>(getServer().getOnlinePlayers()).getRandom();
                Iris.service(StudioSVC.class).open(r != null ? new VolmitSender(r) : getSender(), 1337, IrisSettings.get().getGenerator().getDefaultWorldType(), (w) -> {
                    J.s(() -> {
                        final Location spawn = w.getSpawnLocation();
                        for (Player i : getServer().getOnlinePlayers()) {
                            final Runnable playerTask = () -> {
                                i.setGameMode(GameMode.SPECTATOR);
                                BukkitPlatform.teleportAsync(i, spawn);
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

    public void onEnable() {
        IrisPlatforms.bind(new BukkitPlatform());
        IrisStartupValidation.begin();
        Bukkit.getPluginManager().registerEvents(new IrisStartupAdmissionListener(), this);
        Bukkit.getPluginManager().registerEvents(pendingWorldReplacements, this);
        pendingWorldReplacements.registerPlatformEntryListener();
        boolean enabled;
        try {
            enabled = enable();
        } catch (Throwable failure) {
            refuseVanillaFallback(failure);
            throw failure;
        }
        if (!enabled) {
            refuseVanillaFallback(null);
            return;
        }
        BukkitGuiHost.install();
        // super.onEnable() already registers this instance as a listener.
        super.onEnable();
        if (IrisStartupValidation.isRestartRequired()) {
            String restartReason = IrisStartupValidation.denialReason()
                    .orElse("Iris startup validation requires a restart.");
            startupBoundaryRestart.set(true);
            ServerConfigurator.restartAtStartupBoundary(restartReason);
            return;
        }
        reportLockedRuntime();
    }

    private static void reportLockedRuntime() {
        String denial = IrisStartupValidation.denialReason().orElse(null);
        if (denial == null) {
            return;
        }
        boolean managedStorage;
        try {
            managedStorage = IrisWorldStorage.hasManagedWorldStorage(IrisWorldStorage.levelRoot());
        } catch (Throwable unavailable) {
            Iris.reportError("Could not inspect Iris world storage while reporting the locked runtime.", unavailable);
            managedStorage = false;
        }
        for (String line : RuntimeLockNotice.compose(denial, managedStorage)) {
            Iris.error(line);
        }
    }

    /**
     * Stops a server whose Iris worlds would otherwise be generated by the vanilla generator.
     * <p>
     * A disabled Iris gets no {@code getDefaultWorldGenerator} call at all, so the server falls back to
     * vanilla for every world bukkit.yml points at Iris and writes vanilla terrain into their region files.
     * There is no Bukkit API that refuses a world at that point, so the server is stopped instead. This is
     * damage control, not prevention: level creation runs in the same startup step that enables plugins, so
     * spawn chunks of the affected worlds can still be written before the stop takes effect. The prevention
     * lives in IrisBootstrap, which refuses startup before any level is created.
     */
    private static void refuseVanillaFallback(Throwable failure) {
        File levelRoot;
        try {
            levelRoot = IrisWorldStorage.levelRoot();
        } catch (Throwable unavailable) {
            return;
        }
        if (!IrisWorldStorage.hasManagedWorldStorage(levelRoot)) {
            return;
        }
        Iris.error("Iris did not enable and this server has Iris worlds; stopping the server before they generate vanilla terrain.");
        if (failure != null) {
            Iris.reportError("Iris enable failed", failure);
        }
        try {
            Bukkit.shutdown();
        } catch (Throwable unavailable) {
            Iris.error("Could not stop the server: " + unavailable.getClass().getSimpleName());
        }
    }

    public BukkitDebugDump debugDump() {
        return debugDump;
    }

    public void selectLanguage(CommandSender sender, String[] arguments) {
        BukkitLanguageSwitcher current = languageSwitcher;
        if (current != null) {
            current.command(sender, arguments);
        }
    }

    public List<String> completeLanguage(CommandSender sender, String[] arguments) {
        BukkitLanguageSwitcher current = languageSwitcher;
        return current == null ? List.of() : current.complete(sender, arguments);
    }

    public void onDisable() {
        if (debugDump != null) {
            debugDump.close();
            debugDump = null;
        }
        if (languageSwitcher != null) {
            languageSwitcher.close();
            languageSwitcher = null;
        }
        IrisLanguage.shutdown();
        teardownPapi();
        boolean serverStopping = IrisToolbelt.isServerStopping();
        boolean restartingAtStartupBoundary = startupBoundaryRestart.get();
        if (restartingAtStartupBoundary) {
            teardownRuntime("startup-boundary-restart", 30L);
        } else if (serverStopping) {
            quiesceRuntimeForServerShutdown("onDisable");
            startPostStopFinisher();
        } else {
            teardownRuntime("onDisable", 30L);
            removeShutdownHook();
        }
        if (BukkitPlatform.hasHud()) {
            BukkitPlatform.hudBar().shutdown();
            BukkitPlatform.hudLanes().shutdown();
        }
        SettingsHotloadWatch activeSettingsHotloadWatch = settingsHotloadWatch;
        settingsHotloadWatch = null;
        if (activeSettingsHotloadWatch != null) {
            activeSettingsHotloadWatch.close();
        }
        // super.onDisable() cancels plugin tasks and unregisters every listener.
        super.onDisable();
        if (!serverStopping || restartingAtStartupBoundary) {
            finishTerminalCleanup();
        }
    }

    @Override
    public void onPreUnload(ReloadAware.PreUnloadReason reason) {
        teardownPapi();
        if (IrisToolbelt.isServerStopping()) {
            quiesceRuntimeForServerShutdown("pre-unload:" + reason);
            startPostStopFinisher();
            Iris.debug("Pre-unload hook deferred generator teardown until Paper closes its chunk schedulers.");
            return;
        }
        if (alreadyDrained.get()) {
            Iris.debug("Pre-unload hook skipped; Iris already drained.");
            return;
        }
        Iris.debug("BileTools pre-unload hook fired (" + reason + "). Freezing all Iris worlds.");
        drainOnce("pre-unload:" + reason, 45L);
    }

    /**
     * Drains the world generators exactly once. Serialized against the JVM shutdown hook so a
     * second caller cannot rip the pools or services out from under an in-flight drain.
     */
    private void drainOnce(String reason, long timeoutSeconds) {
        synchronized (TEARDOWN_LOCK) {
            if (alreadyDrained.compareAndSet(false, true)) {
                drainWorldGenerators(reason, timeoutSeconds);
            }
        }
    }

    /**
     * Full teardown: generators, then services, then the shared pools and the service map.
     * Both onDisable and the JVM shutdown hook route through here; whichever runs second is a no-op.
     */
    private void teardownRuntime(String reason, long timeoutSeconds) {
        synchronized (TEARDOWN_LOCK) {
            if (alreadyDrained.compareAndSet(false, true)) {
                drainWorldGenerators(reason, timeoutSeconds);
            }

            if (serverStopTeardownDeferred.get() && !generatorDrainCompleted) {
                return;
            }

            if (services != null && servicesDisabled.compareAndSet(false, true)) {
                // Only services whose onEnable actually completed; disabling a service that
                // never initialized runs teardown against uninitialized state.
                for (IrisService service : enabledServices) {
                    try {
                        service.onDisable();
                    } catch (Throwable e) {
                        runtimeTeardownFailed.set(true);
                        Iris.reportError("Failed to disable " + service.getClass().getSimpleName() + ".", e);
                    }
                }
            }

            if (!sharedRuntimeClosed.compareAndSet(false, true)) {
                return;
            }

            try {
                MultiBurst.burst.close();
            } catch (Throwable failure) {
                runtimeTeardownFailed.set(true);
                Iris.reportError("Failed to close Iris generation workers.", failure);
            }
            try {
                MultiBurst.ioBurst.close();
            } catch (Throwable failure) {
                runtimeTeardownFailed.set(true);
                Iris.reportError("Failed to close Iris I/O workers.", failure);
            }
            clearQueues();
            IrisRuntimeStatics.reset();
            releaseStatics();
        }
    }

    private static void releaseStatics() {
        linkMultiverseCore = null;
        compat = null;
        tickets = null;
        sender = null;
        instance = null;
    }

    private void quiesceRuntimeForServerShutdown(String reason) {
        serverStopTeardownDeferred.set(true);
        INMS.get().deferPluginClassLoaderClose();
        JigsawStudioService jigsawStudioService = IrisServices.getOrNull(JigsawStudioService.class);
        if (jigsawStudioService != null) {
            try {
                jigsawStudioService.quiesceForServerShutdown();
            } catch (Throwable e) {
                Iris.reportError("Failed to quiesce Jigsaw Studio before server shutdown.", e);
            }
        }
        StudioSVC studioService = IrisServices.getOrNull(StudioSVC.class);
        if (studioService != null) {
            studioService.quiesceDownloadsForShutdown();
        }

        try {
            if (J.isFolia()) {
                PregeneratorJob.shutdownAndWait(SERVER_STOP_PREGEN_TIMEOUT_MILLIS);
            } else {
                List<World> worlds = Bukkit.getWorlds();
                PregeneratorJob.shutdownAndWait(SERVER_STOP_PREGEN_TIMEOUT_MILLIS, () -> {
                    for (World world : worlds) {
                        INMS.get().pollChunkTask(world);
                    }
                });
            }
        } catch (Throwable e) {
            Iris.reportError("Failed to quiesce the Iris pregenerator before server shutdown.", e);
        }

        for (World world : Bukkit.getWorlds()) {
            PlatformChunkGenerator generator = IrisToolbelt.access(world);
            if (generator == null) {
                continue;
            }
            IrisToolbelt.beginWorldMaintenance(world, reason, true);
            if (!deferredShutdownGenerators.contains(generator)) {
                deferredShutdownGenerators.add(generator);
            }
            generator.quiesceForServerShutdown();
        }
    }

    private void startPostStopFinisher() {
        if (!postStopFinisherStarted.compareAndSet(false, true)) {
            return;
        }
        Thread activeServerThread = serverLifecycleThread;
        if (activeServerThread == null) {
            Iris.warn("Iris could not start its post-stop runtime finisher because the server lifecycle thread is unavailable.");
            return;
        }

        Thread finisher = new Thread(() -> {
            if (!awaitServerThreadTermination(activeServerThread)) {
                return;
            }
            finishDeferredRuntimeTeardown("post-server-stop", 30L);
        }, "Iris-PostStop-Finisher");
        finisher.setDaemon(false);
        finisher.start();
    }

    static boolean awaitServerThreadTermination(Thread serverThread) {
        if (serverThread == null || serverThread == Thread.currentThread()) {
            return false;
        }
        try {
            serverThread.join();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Iris.reportError("Iris post-stop runtime finisher was interrupted.", e);
            return false;
        }
    }

    private void runShutdownHook() {
        if (startupBoundaryRestart.get()) {
            finishDeferredRuntimeTeardown("startup-boundary-restart-hook", 30L);
            return;
        }
        if (!awaitServerShutdownBoundary()) {
            SHUTDOWN_ERRORS.println("[Iris] Paper did not reach its post-world-close boundary; retaining the runtime and plugin class loader until JVM exit.");
            return;
        }
        finishDeferredRuntimeTeardown("shutdown-hook", 30L);
    }

    private boolean awaitServerShutdownBoundary() {
        ServerShutdownBoundary boundary = serverShutdownBoundary;
        if (boundary == null) {
            return true;
        }
        try {
            return boundary.await(
                    SERVER_SHUTDOWN_BOUNDARY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Throwable e) {
            SHUTDOWN_ERRORS.println("[Iris] Failed to await Paper's post-world-close shutdown boundary.");
            e.printStackTrace(SHUTDOWN_ERRORS);
            return false;
        }
    }

    private void finishDeferredRuntimeTeardown(String reason, long timeoutSeconds) {
        teardownRuntime(reason, timeoutSeconds);
        if (serverStopTeardownDeferred.get() && !generatorDrainCompleted) {
            SHUTDOWN_ERRORS.println("[Iris] Runtime cleanup is incomplete; retaining services, pools and the plugin class loader until JVM exit.");
            return;
        }
        finishTerminalCleanup();
    }

    private void finishTerminalCleanup() {
        if (!terminalCleanupCompleted.compareAndSet(false, true)) {
            return;
        }
        J.attempt(() -> INMS.get().uninjectBukkit());
        try {
            runPostShutdown();
        } catch (Throwable e) {
            runtimeTeardownFailed.set(true);
            Iris.reportError("Failed to run Iris post-shutdown cleanup.", e);
        } finally {
            if (serverStopTeardownDeferred.get() && hasActiveShutdownResources()) {
                runtimeTeardownFailed.set(true);
                SHUTDOWN_ERRORS.println("[Iris] Owned background work remains active after cleanup; retaining the plugin class loader until JVM exit.");
            }
            IrisPlatforms.unbind();
            if (serverStopTeardownDeferred.get() && runtimeTeardownFailed.get()) {
                SHUTDOWN_ERRORS.println("[Iris] Runtime cleanup failed; retaining the plugin class loader until JVM exit.");
            } else {
                try {
                    INMS.get().releasePluginClassLoaderClose();
                } catch (Throwable failure) {
                    SHUTDOWN_ERRORS.println("[Iris] Failed to release the plugin class loader after runtime cleanup.");
                    failure.printStackTrace(SHUTDOWN_ERRORS);
                }
            }
            BukkitPlatform.releaseHost();
        }
    }

    private boolean hasActiveShutdownResources() {
        if (!MultiBurst.burst.isTerminated() || !MultiBurst.ioBurst.isTerminated()) {
            return true;
        }
        return services != null
                && services.get(PreservationSVC.class) instanceof PreservationSVC preservation
                && preservation.hasActiveResources();
    }

    private void drainWorldGenerators(String reason, long timeoutSeconds) {
        List<World> irisWorlds = new ArrayList<>();
        List<PlatformChunkGenerator> generators = new ArrayList<>();
        if (serverStopTeardownDeferred.get()) {
            generators.addAll(deferredShutdownGenerators);
        } else {
            for (World world : Bukkit.getWorlds()) {
                PlatformChunkGenerator generator = IrisToolbelt.access(world);
                if (generator != null) {
                    irisWorlds.add(world);
                    generators.add(generator);
                }
            }
        }
        if (generators.isEmpty()) {
            generatorDrainCompleted = true;
            Iris.debug("No Iris worlds to freeze.");
            return;
        }

        for (World world : irisWorlds) {
            IrisToolbelt.beginWorldMaintenance(world, reason, true);
        }

        J.attempt(PregeneratorJob::shutdownInstance);

        List<CompletableFuture<Void>> closes = new ArrayList<>();
        for (PlatformChunkGenerator generator : generators) {
            try {
                closes.add(generator.closeAsync());
            } catch (Throwable t) {
                Iris.reportError(t);
            }
        }

        if (closes.isEmpty()) return;

        try {
            CompletableFuture.allOf(closes.toArray(new CompletableFuture<?>[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            generatorDrainCompleted = closes.size() == generators.size();
            if (generatorDrainCompleted) {
                Iris.debug("All Iris chunk generators parked. Safe to unload.");
            }
        } catch (TimeoutException e) {
            Iris.reportError("Iris generator drain timed out after " + timeoutSeconds + "s.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Iris.reportError("Iris generator drain was interrupted.", e);
        } catch (ExecutionException e) {
            Iris.reportError(e.getCause() == null ? e : e.getCause());
        }
    }

    private void setupPapi() {
        if (!PlaceholderRegistration.isPlaceholderApiEnabled()) {
            return;
        }

        IrisPapiState state = new IrisPapiState(() -> IrisServices.getOrNull(IrisTerrainService.class));
        PlaceholderRegistration registration = new PlaceholderRegistration(getLogger());

        if (!IrisPapiInstaller.install(registration, state, getLogger())) {
            return;
        }

        IrisPapiListener listener = new IrisPapiListener(state);

        try {
            Bukkit.getPluginManager().registerEvents(listener, this);
        } catch (Throwable failure) {
            registration.unregister();
            Iris.warn("Failed to attach the Iris PlaceholderAPI listener: "
                    + failure.getClass().getName() + ": " + failure.getMessage());
            return;
        }

        papiState = state;
        papiListener = listener;
        papiRegistration = registration;
    }

    private void teardownPapi() {
        IrisPapiListener listener = papiListener;
        papiListener = null;

        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }

        PlaceholderRegistration registration = papiRegistration;
        papiRegistration = null;

        if (registration != null) {
            registration.unregister();
        }

        IrisPapiState state = papiState;
        papiState = null;

        if (state != null) {
            state.clear();
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
                    Iris.reportError(e);
                }
            }
        }
    }

    private void bstats() {
        if (IrisSettings.get().getGeneral().isMetrics()) {
            Bindings.setupBstats(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return super.onCommand(sender, command, label, args);
    }

    public void imsg(CommandSender s, String msg) {
        ComponentMessenger.sendSection(
                s,
                C.IRIS + "[" + C.DARK_GRAY + "Iris" + C.IRIS + "]" + C.GRAY + ": " + msg);
    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        return generatorResolver.resolveDefaultBiomeProvider(worldName, id, () -> super.getDefaultBiomeProvider(worldName, id));
    }

    @Nullable
    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return generatorResolver.resolveDefaultWorldGenerator(worldName, id);
    }

    public void splash() {
        Iris.info("Custom Biomes: " + INMS.get().countCustomBiomes());
        printPacks();

        IrisSafeguard.mode().trySplash();
    }

    private void printPacks() {
        File packFolder = Iris.service(StudioSVC.class).getWorkspaceFolder();
        for (String line : IrisSplashComposer.composePackLines(packFolder, Iris::reportError)) {
            Iris.info(line);
        }
    }

    public int getIrisVersion() {
        String input = getDescription().getVersion();
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
