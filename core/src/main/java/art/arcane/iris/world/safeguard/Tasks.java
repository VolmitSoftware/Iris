package art.arcane.iris.world.safeguard;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.IrisWorlds;
import art.arcane.iris.world.runtime.RuntimeInjection;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.v1X.NMSBinding1X;
import art.arcane.iris.diagnostics.splash.IrisSplashComposer;
import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.bootstrap.getHardware;
import art.arcane.iris.platform.agent.Agent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Tasks {
    static final String NMS_LOCK_REASON =
            "Iris cannot use this server's NMS runtime. Resolve the Server Version or NMS Disabled error above and restart the server.";
    static final String INJECTION_LOCK_REASON =
            "Iris runtime injection failed. Resolve the startup errors and restart the server.";
    static final String DIMENSION_TYPE_LOCK_REASON =
            "Iris dimension types were not registered. Restart the server so the registries reload.";

    private static final Set<String> INCOMPATIBLE_PLUGINS = Set.of("dynmap", "stratos");

    private static final Task MEMORY = Task.advisory("memory", () -> {
        long mem = getHardware.getProcessMemory();
        if (mem >= 3072L) {
            return CheckResult.stable();
        }

        if (mem > 2048L) {
            return CheckResult.stable(
                    Diagnostic.Logger.INFO.create("Memory Recommendation"),
                    Diagnostic.Logger.INFO.create("- A 3GB+ JVM maximum heap is recommended for Iris."),
                    Diagnostic.Logger.INFO.create("- JVM maximum heap: " + mem + " MB"));
        }

        return CheckResult.warning(
                Diagnostic.Logger.WARN.create("Low Memory"),
                Diagnostic.Logger.WARN.create("- Iris is running with a 2GB or smaller JVM maximum heap."),
                Diagnostic.Logger.WARN.create("- A 3GB+ JVM maximum heap is recommended for Iris."),
                Diagnostic.Logger.WARN.create("- JVM maximum heap: " + mem + " MB"));
    });

    private static final Task INCOMPATIBILITIES = Task.advisory("incompatibilities", () -> {
        Set<String> plugins = installedIncompatiblePlugins();

        if (plugins.isEmpty()) {
            return CheckResult.stable();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        if (plugins.contains("dynmap")) {
            addAllDiagnostics(diagnostics,
                    Diagnostic.Logger.ERROR.create("Dynmap"),
                    Diagnostic.Logger.ERROR.create("- The plugin Dynmap is not compatible with the server."),
                    Diagnostic.Logger.ERROR.create("- If you want to have a map plugin like Dynmap, consider Bluemap."));
        }
        if (plugins.contains("stratos")) {
            addAllDiagnostics(diagnostics,
                    Diagnostic.Logger.ERROR.create("Stratos"),
                    Diagnostic.Logger.ERROR.create("- Iris is not compatible with other worldgen plugins."));
        }
        return CheckResult.warning(diagnostics);
    });

    private static final Task SOFTWARE = Task.advisory("software", () -> {
        Set<String> supported = Set.of("canvas", "folia", "purpur", "pufferfish", "leaf", "paper", "spigot", "bukkit");
        String serverName = server().getName().toLowerCase(Locale.ROOT);
        boolean supportedServer = isCanvasServer();
        if (!supportedServer) {
            for (String candidate : supported) {
                if (serverName.contains(candidate)) {
                    supportedServer = true;
                    break;
                }
            }
        }

        if (supportedServer) {
            return CheckResult.stable();
        }

        return CheckResult.warning(
                Diagnostic.Logger.WARN.create("Unsupported Server Software"),
                Diagnostic.Logger.WARN.create("- Please consider using Canvas, Folia, Leaf, Paper, or Purpur instead."));
    });

    private static final Task VERSION = Task.critical("version", NMS_LOCK_REASON, () -> {
        String[] parts = BukkitPlatform.plugin().getDescription().getVersion().split("-");
        String supportedVersions;
        if (parts.length >= 3) {
            String minVersion = parts[1];
            String maxVersion = parts[2];
            supportedVersions = minVersion.equals(maxVersion) ? minVersion : minVersion + " - " + maxVersion;
        } else if (parts.length >= 2) {
            supportedVersions = parts[1];
        } else {
            supportedVersions = BuildConstants.MINECRAFT_VERSION;
        }

        if (!INMS.isBound()) {
            String cause = INMS.bindFailure() == null ? "Unknown NMS bind failure" : INMS.bindFailure().getMessage();
            return CheckResult.danger(NMS_LOCK_REASON,
                    Diagnostic.Logger.ERROR.create("Server Version"),
                    Diagnostic.Logger.ERROR.create("- " + cause),
                    Diagnostic.Logger.ERROR.create("- Iris only supports " + supportedVersions));
        }

        if (!(INMS.get() instanceof NMSBinding1X)) {
            return CheckResult.stable();
        }

        return CheckResult.danger(NMS_LOCK_REASON,
                Diagnostic.Logger.ERROR.create("NMS Disabled"),
                Diagnostic.Logger.ERROR.create("- NMS support is disabled (general.disableNMS); Iris world creation is unavailable."));
    });

    private static final Task INJECTION = Task.critical("injection", INJECTION_LOCK_REASON, () -> {
        if (!IrisSettings.get().getGeneral().isEagerRuntimeInjection()) {
            return CheckResult.stable(
                    Diagnostic.Logger.INFO.create("Runtime Injection"),
                    Diagnostic.Logger.INFO.create("- Deferred to the first world load. Set general.eagerRuntimeInjection to verify it during startup."));
        }

        RuntimeInjection.Outcome outcome = RuntimeInjection.install();
        if (outcome.installed()) {
            return CheckResult.stable();
        }

        return switch (outcome.failure()) {
            case NO_BINDING -> CheckResult.danger(outcome.lockReason(),
                    Diagnostic.Logger.ERROR.create("Code Injection"),
                    Diagnostic.Logger.ERROR.create("- Runtime injection was skipped because Iris has no usable NMS binding."));
            case AGENT -> CheckResult.danger(outcome.lockReason(),
                    Diagnostic.Logger.ERROR.create("Java Agent"),
                    Diagnostic.Logger.ERROR.create("- Add -javaagent:" + Agent.AGENT_JAR.getPath() + " before -jar in your startup command and restart the server."),
                    Diagnostic.Logger.ERROR.create("- Dynamic attachment requires -XX:+EnableDynamicAgentLoading and a host that permits JVM attachment."));
            case INJECTION, NONE -> CheckResult.danger(INJECTION_LOCK_REASON,
                    Diagnostic.Logger.ERROR.create("Code Injection"),
                    Diagnostic.Logger.ERROR.create("- Failed to inject code. Please contact support"));
        };
    });

    private static final Task DIMENSION_TYPES = Task.critical("dimensionTypes", DIMENSION_TYPE_LOCK_REASON, () -> {
        if (!INMS.isBound()) {
            return CheckResult.danger(NMS_LOCK_REASON,
                    Diagnostic.Logger.ERROR.create("Dimension Types"),
                    Diagnostic.Logger.ERROR.create("- Dimension types could not be checked because Iris has no NMS binding."));
        }

        Set<String> keys = IrisWorlds.get().getDimensions().map(IrisDimension::getDimensionTypeKey).collect(Collectors.toSet());
        if (!INMS.get().missingDimensionTypes(keys.toArray(String[]::new))) {
            return CheckResult.stable();
        }

        return CheckResult.danger(DIMENSION_TYPE_LOCK_REASON,
                Diagnostic.Logger.ERROR.create("Dimension Types"),
                Diagnostic.Logger.ERROR.create("- Required Iris dimension types were not loaded."),
                Diagnostic.Logger.ERROR.create("- If this still happens after a restart please contact support."));
    });

    private static final Task DISK_SPACE = Task.advisory("diskSpace", () -> {
        double freeGiB = IrisWorldStorage.levelRoot().getFreeSpace() / (double) 0x4000_0000;
        if (freeGiB > 3.0) {
            return CheckResult.stable();
        }

        return CheckResult.warning(
                Diagnostic.Logger.WARN.create("Insufficient Disk Space"),
                Diagnostic.Logger.WARN.create("- 3GB of free space is required for Iris to function."));
    });

    private static final Task JAVA = Task.advisory("java", () -> {
        int version = IrisSplashComposer.javaVersion();
        if (version < 0) {
            return CheckResult.warning(
                    Diagnostic.Logger.WARN.create("Java Runtime"),
                    Diagnostic.Logger.WARN.create("- Java version could not be determined (java.version="
                            + System.getProperty("java.version") + ")."));
        }
        if (version == 25) {
            return CheckResult.stable();
        }

        if (version > 25) {
            return CheckResult.stable(
                    Diagnostic.Logger.INFO.create("Java Runtime"),
                    Diagnostic.Logger.INFO.create("- Running Java " + version + ". Iris is tested primarily on Java 25."));
        }

        return CheckResult.warning(
                Diagnostic.Logger.WARN.create("Unsupported Java version"),
                Diagnostic.Logger.WARN.create("- Java 25+ is recommended. Current runtime: Java " + version));
    });

    private static final List<Task> TASKS = List.of(
            MEMORY,
            INCOMPATIBILITIES,
            SOFTWARE,
            VERSION,
            INJECTION,
            DIMENSION_TYPES,
            DISK_SPACE,
            JAVA
    );

    private Tasks() {
    }

    public static List<Task> getTasks() {
        return TASKS;
    }

    private static Server server() {
        return Bukkit.getServer();
    }

    private static Set<String> installedIncompatiblePlugins() {
        Set<String> found = new LinkedHashSet<>();
        Plugin[] plugins = server().getPluginManager().getPlugins();
        if (plugins == null) {
            return found;
        }
        for (Plugin plugin : plugins) {
            String name = plugin == null ? null : plugin.getName();
            if (name == null) {
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (INCOMPATIBLE_PLUGINS.contains(normalized)) {
                found.add(normalized);
            }
        }
        return found;
    }

    private static boolean isCanvasServer() {
        ClassLoader loader = server().getClass().getClassLoader();
        if (CapabilityProbe.succeeds("io.canvasmc.canvas.region.WorldRegionizer",
                () -> Class.forName("io.canvasmc.canvas.region.WorldRegionizer", false, loader))) {
            return true;
        }
        return server().getName().toLowerCase(Locale.ROOT).contains("canvas");
    }

    private static void addAllDiagnostics(List<Diagnostic> diagnostics, Diagnostic... values) {
        for (Diagnostic value : values) {
            diagnostics.add(value);
        }
    }
}
