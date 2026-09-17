package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.core.IrisWorldStorage;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.v1X.NMSBinding1X;
import art.arcane.iris.core.safeguard.Mode;
import art.arcane.iris.core.splash.IrisSplashComposer;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.common.misc.getHardware;
import art.arcane.iris.util.project.agent.Agent;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Tasks {
    private static final Task MEMORY = Task.of("memory", () -> {
        long mem = getHardware.getProcessMemory();
        if (mem >= 3072L) {
            return withDiagnostics(Mode.STABLE);
        }

        if (mem > 2048L) {
            return withDiagnostics(Mode.STABLE,
                    Diagnostic.Logger.INFO.create("Memory Recommendation"),
                    Diagnostic.Logger.INFO.create("- 3GB+ process memory is recommended for Iris."),
                    Diagnostic.Logger.INFO.create("- Process Memory: " + mem + " MB"));
        }

        return withDiagnostics(Mode.WARNING,
                Diagnostic.Logger.WARN.create("Low Memory"),
                Diagnostic.Logger.WARN.create("- Iris is running with 2GB or less process memory."),
                Diagnostic.Logger.WARN.create("- 3GB+ process memory is recommended for Iris."),
                Diagnostic.Logger.WARN.create("- Process Memory: " + mem + " MB"));
    });

    private static final Task INCOMPATIBILITIES = Task.of("incompatibilities", () -> {
        Set<String> plugins = new HashSet<>(Set.of("dynmap", "Stratos"));
        plugins.removeIf(name -> server().getPluginManager().getPlugin(name) == null);

        if (plugins.isEmpty()) {
            return withDiagnostics(Mode.STABLE);
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        if (plugins.contains("dynmap")) {
            addAllDiagnostics(diagnostics,
                    Diagnostic.Logger.ERROR.create("Dynmap"),
                    Diagnostic.Logger.ERROR.create("- The plugin Dynmap is not compatible with the server."),
                    Diagnostic.Logger.ERROR.create("- If you want to have a map plugin like Dynmap, consider Bluemap."));
        }
        if (plugins.contains("Stratos")) {
            addAllDiagnostics(diagnostics,
                    Diagnostic.Logger.ERROR.create("Stratos"),
                    Diagnostic.Logger.ERROR.create("- Iris is not compatible with other worldgen plugins."));
        }
        return withDiagnostics(Mode.WARNING, diagnostics);
    });

    private static final Task SOFTWARE = Task.of("software", () -> {
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
            return withDiagnostics(Mode.STABLE);
        }

        return withDiagnostics(Mode.WARNING,
                Diagnostic.Logger.WARN.create("Unsupported Server Software"),
                Diagnostic.Logger.WARN.create("- Please consider using Canvas, Folia, Leaf, Paper, or Purpur instead."));
    });

    private static final Task VERSION = Task.of("version", () -> {
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
            return withDiagnostics(Mode.UNSTABLE,
                    Diagnostic.Logger.ERROR.create("Server Version"),
                    Diagnostic.Logger.ERROR.create("- " + cause),
                    Diagnostic.Logger.ERROR.create("- Iris only supports " + supportedVersions));
        }

        if (!(INMS.get() instanceof NMSBinding1X)) {
            return withDiagnostics(Mode.STABLE);
        }

        return withDiagnostics(Mode.UNSTABLE,
                Diagnostic.Logger.ERROR.create("NMS Disabled"),
                Diagnostic.Logger.ERROR.create("- NMS support is disabled (general.disableNMS); Iris world creation is unavailable."));
    });

    private static final Task INJECTION = Task.of("injection", () -> {
        if (!Agent.install()) {
            return withDiagnostics(Mode.UNSTABLE,
                    Diagnostic.Logger.ERROR.create("Java Agent"),
                    Diagnostic.Logger.ERROR.create("- Please enable dynamic agent loading by adding -XX:+EnableDynamicAgentLoading to your jvm arguments."),
                    Diagnostic.Logger.ERROR.create("- or add the jvm argument -javaagent:" + Agent.AGENT_JAR.getPath()));
        }

        if (!INMS.get().injectBukkit()) {
            return withDiagnostics(Mode.UNSTABLE,
                    Diagnostic.Logger.ERROR.create("Code Injection"),
                    Diagnostic.Logger.ERROR.create("- Failed to inject code. Please contact support"));
        }

        return withDiagnostics(Mode.STABLE);
    });

    private static final Task DIMENSION_TYPES = Task.of("dimensionTypes", () -> {
        Set<String> keys = IrisWorlds.get().getDimensions().map(IrisDimension::getDimensionTypeKey).collect(Collectors.toSet());
        if (!INMS.get().missingDimensionTypes(keys.toArray(String[]::new))) {
            return withDiagnostics(Mode.STABLE);
        }

        return withDiagnostics(Mode.UNSTABLE,
                Diagnostic.Logger.ERROR.create("Dimension Types"),
                Diagnostic.Logger.ERROR.create("- Required Iris dimension types were not loaded."),
                Diagnostic.Logger.ERROR.create("- If this still happens after a restart please contact support."));
    });

    private static final Task DISK_SPACE = Task.of("diskSpace", () -> {
        double freeGiB = IrisWorldStorage.levelRoot().getFreeSpace() / (double) 0x4000_0000;
        if (freeGiB > 3.0) {
            return withDiagnostics(Mode.STABLE);
        }

        return withDiagnostics(Mode.WARNING,
                Diagnostic.Logger.WARN.create("Insufficient Disk Space"),
                Diagnostic.Logger.WARN.create("- 3GB of free space is required for Iris to function."));
    });

    private static final Task JAVA = Task.of("java", () -> {
        int version = IrisSplashComposer.javaVersion();
        if (version < 0) {
            return withDiagnostics(Mode.WARNING,
                    Diagnostic.Logger.WARN.create("Java Runtime"),
                    Diagnostic.Logger.WARN.create("- Java version could not be determined (java.version="
                            + System.getProperty("java.version") + ")."));
        }
        if (version == 25) {
            return withDiagnostics(Mode.STABLE);
        }

        if (version > 25) {
            return withDiagnostics(Mode.STABLE,
                    Diagnostic.Logger.INFO.create("Java Runtime"),
                    Diagnostic.Logger.INFO.create("- Running Java " + version + ". Iris is tested primarily on Java 25."));
        }

        return withDiagnostics(Mode.WARNING,
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

    private static boolean isCanvasServer() {
        ClassLoader loader = server().getClass().getClassLoader();
        try {
            Class.forName("io.canvasmc.canvas.region.WorldRegionizer", false, loader);
            return true;
        } catch (Throwable ignored) {
            return server().getName().toLowerCase(Locale.ROOT).contains("canvas");
        }
    }

    private static void addAllDiagnostics(List<Diagnostic> diagnostics, Diagnostic... values) {
        for (Diagnostic value : values) {
            diagnostics.add(value);
        }
    }

    private static ValueWithDiagnostics<Mode> withDiagnostics(Mode mode, Diagnostic... diagnostics) {
        return new ValueWithDiagnostics<>(mode, diagnostics);
    }

    private static ValueWithDiagnostics<Mode> withDiagnostics(Mode mode, List<Diagnostic> diagnostics) {
        return new ValueWithDiagnostics<>(mode, diagnostics);
    }

}
