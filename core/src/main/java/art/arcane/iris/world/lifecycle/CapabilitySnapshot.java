package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.server.NativeServerDiagnostics;
import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;

public final class CapabilitySnapshot {
    private final ServerFamily serverFamily;
    private final boolean regionizedRuntime;
    private final Object worldsProvider;
    private final Class<?> worldsLevelStemClass;
    private final Class<?> worldsGeneratorTypeClass;
    private final String worldsProviderResolution;
    private final NativeWorldRuntime nativeRuntime;
    private final Method chunkAtAsyncMethod;

    CapabilitySnapshot(
            ServerFamily serverFamily,
            boolean regionizedRuntime,
            Object worldsProvider,
            Class<?> worldsLevelStemClass,
            Class<?> worldsGeneratorTypeClass,
            String worldsProviderResolution,
            NativeWorldRuntime nativeRuntime,
            Method chunkAtAsyncMethod
    ) {
        this.serverFamily = serverFamily;
        this.regionizedRuntime = regionizedRuntime;
        this.worldsProvider = worldsProvider;
        this.worldsLevelStemClass = worldsLevelStemClass;
        this.worldsGeneratorTypeClass = worldsGeneratorTypeClass;
        this.worldsProviderResolution = worldsProviderResolution;
        this.nativeRuntime = nativeRuntime;
        this.chunkAtAsyncMethod = chunkAtAsyncMethod;
    }

    public static CapabilitySnapshot probe() {
        Server server = Bukkit.getServer();
        boolean regionizedRuntime = FoliaScheduler.isRegionizedRuntime(server);
        ServerFamily serverFamily = detectServerFamily(server, regionizedRuntime);

        Object worldsProvider = null;
        Class<?> worldsLevelStemClass = null;
        Class<?> worldsGeneratorTypeClass = null;
        String worldsProviderResolution = "inactive";
        try {
            Object[] worldsProviderData = resolveWorldsProvider();
            worldsProvider = worldsProviderData[0];
            worldsLevelStemClass = (Class<?>) worldsProviderData[1];
            worldsGeneratorTypeClass = (Class<?>) worldsProviderData[2];
            worldsProviderResolution = (String) worldsProviderData[3];
        } catch (Throwable e) {
            worldsProviderResolution = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
        }

        NativeWorldRuntime nativeRuntime = NativeAdapters.find(NativeWorldRuntime.class).orElse(null);

        Method chunkAtAsyncMethod = CapabilityProbe.attempt(
                "world#getChunkAtAsync",
                () -> CapabilityResolution.resolveMethod(World.class, "getChunkAtAsync", method -> {
                    Class<?>[] params = method.getParameterTypes();
                    return params.length == 3
                            && int.class.equals(params[0])
                            && int.class.equals(params[1])
                            && boolean.class.equals(params[2]);
                }),
                null);

        return new CapabilitySnapshot(
                serverFamily,
                regionizedRuntime,
                worldsProvider,
                worldsLevelStemClass,
                worldsGeneratorTypeClass,
                worldsProviderResolution,
                nativeRuntime,
                chunkAtAsyncMethod
        );
    }

    public ServerFamily serverFamily() {
        return serverFamily;
    }

    public boolean regionizedRuntime() {
        return regionizedRuntime;
    }

    public Object worldsProvider() {
        return worldsProvider;
    }

    public Class<?> worldsLevelStemClass() {
        return worldsLevelStemClass;
    }

    public Class<?> worldsGeneratorTypeClass() {
        return worldsGeneratorTypeClass;
    }

    public Method chunkAtAsyncMethod() {
        return chunkAtAsyncMethod;
    }

    public NativeWorldRuntime nativeRuntime() {
        return nativeRuntime;
    }

    public boolean hasWorldsProvider() {
        return worldsProvider != null && worldsLevelStemClass != null && worldsGeneratorTypeClass != null;
    }

    public boolean hasPaperLikeRuntime() {
        return nativeRuntime != null && nativeRuntime.available();
    }

    public String worldsProviderResolution() {
        return worldsProviderResolution;
    }

    public String paperLikeResolution() {
        return nativeRuntime == null ? "unsupported" : nativeRuntime.description();
    }

    public String describe() {
        return "family=" + serverFamily.id()
                + ", regionizedRuntime=" + regionizedRuntime
                + ", worldsProvider=" + worldsProviderResolution
                + ", paperLike=" + paperLikeResolution()
                + ", serverRegistryAccess=" + (nativeRuntime != null && nativeRuntime.supportsRegistryAccess())
                + ", unloadAsync=" + (nativeRuntime != null && nativeRuntime.supportsUnloadAsync())
                + ", chunkAsync=" + (chunkAtAsyncMethod != null);
    }

    private static ServerFamily detectServerFamily(Server server, boolean regionizedRuntime) {
        String bukkitName = server == null ? "" : server.getName();
        String bukkitVersion = server == null ? "" : server.getVersion();
        String serverClassName = server == null ? "" : server.getClass().getName();
        boolean canvasRuntime = hasCanvasRuntime();

        if (containsIgnoreCase(bukkitName, "folia")
                || containsIgnoreCase(bukkitVersion, "folia")
                || containsIgnoreCase(serverClassName, "folia")) {
            return ServerFamily.FOLIA;
        }

        if (canvasRuntime
                || containsIgnoreCase(bukkitName, "canvas")
                || containsIgnoreCase(bukkitVersion, "canvas")
                || containsIgnoreCase(serverClassName, "canvas")) {
            return regionizedRuntime ? ServerFamily.CANVAS : ServerFamily.CANVAS;
        }

        if (containsIgnoreCase(bukkitName, "purpur")
                || containsIgnoreCase(bukkitVersion, "purpur")
                || containsIgnoreCase(serverClassName, "purpur")) {
            return ServerFamily.PURPUR;
        }

        if (containsIgnoreCase(bukkitName, "paper")
                || containsIgnoreCase(bukkitVersion, "paper")
                || containsIgnoreCase(serverClassName, "paper")
                || containsIgnoreCase(bukkitName, "pufferfish")
                || containsIgnoreCase(bukkitVersion, "pufferfish")
                || containsIgnoreCase(serverClassName, "pufferfish")) {
            return ServerFamily.PAPER;
        }

        if (containsIgnoreCase(bukkitName, "spigot")
                || containsIgnoreCase(bukkitVersion, "spigot")
                || containsIgnoreCase(serverClassName, "spigot")) {
            return ServerFamily.SPIGOT;
        }

        if (containsIgnoreCase(bukkitName, "craftbukkit")
                || containsIgnoreCase(bukkitVersion, "craftbukkit")
                || containsIgnoreCase(serverClassName, "craftbukkit")
                || containsIgnoreCase(bukkitName, "bukkit")
                || containsIgnoreCase(bukkitVersion, "bukkit")) {
            return ServerFamily.BUKKIT;
        }

        if (regionizedRuntime || J.isFolia()) {
            return ServerFamily.FOLIA;
        }

        return ServerFamily.UNKNOWN;
    }

    private static boolean hasCanvasRuntime() {
        return NativeAdapters.find(NativeServerDiagnostics.class)
                .map(diagnostics -> diagnostics.isCanvas(CapabilitySnapshot.class.getClassLoader())).orElse(false);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Object[] resolveWorldsProvider() throws Throwable {
        Object[] direct = CapabilityProbe.attempt("net.thenextlvl.worlds.api.WorldsProvider",
                CapabilitySnapshot::loadWorldsProvider, null);
        if (direct != null) {
            return direct;
        }

        Collection<Class<?>> knownServices = Bukkit.getServicesManager().getKnownServices();
        for (Class<?> serviceClass : knownServices) {
            if (!"net.thenextlvl.worlds.api.WorldsProvider".equals(serviceClass.getName())) {
                continue;
            }

            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(serviceClass);
            if (registration == null) {
                continue;
            }

            Object provider = registration.getProvider();
            ClassLoader loader = serviceClass.getClassLoader();
            if (loader == null && provider != null) {
                loader = provider.getClass().getClassLoader();
            }
            if (loader == null) {
                continue;
            }

            Class<?> levelStemClass = Class.forName("net.thenextlvl.worlds.api.generator.LevelStem", false, loader);
            Class<?> generatorTypeClass = Class.forName("net.thenextlvl.worlds.api.generator.GeneratorType", false, loader);
            return new Object[]{provider, levelStemClass, generatorTypeClass, "active(service-scan=" + provider.getClass().getName() + ")"};
        }

        return new Object[]{null, null, null, "inactive(service scan found nothing)"};
    }

    private static Object[] loadWorldsProvider() throws ClassNotFoundException {
        Class<?> worldsProviderClass = Class.forName("net.thenextlvl.worlds.api.WorldsProvider");
        Class<?> levelStemClass = Class.forName("net.thenextlvl.worlds.api.generator.LevelStem");
        Class<?> generatorTypeClass = Class.forName("net.thenextlvl.worlds.api.generator.GeneratorType");
        Object provider = Bukkit.getServicesManager().load(worldsProviderClass);
        String resolution = provider == null ? "inactive(service not registered)" : "active(service=" + provider.getClass().getName() + ")";
        return new Object[]{provider, levelStemClass, generatorTypeClass, resolution};
    }

}
