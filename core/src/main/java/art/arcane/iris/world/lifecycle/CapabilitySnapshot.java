package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;

public final class CapabilitySnapshot {
    public enum PaperLikeFlavor {
        CURRENT_INFO_AND_DATA,
        LEGACY_STORAGE_ACCESS,
        UNSUPPORTED
    }

    private final ServerFamily serverFamily;
    private final boolean regionizedRuntime;
    private final Object worldsProvider;
    private final Class<?> worldsLevelStemClass;
    private final Class<?> worldsGeneratorTypeClass;
    private final String worldsProviderResolution;
    private final Object bukkitServer;
    private final Object minecraftServer;
    private final Method createLevelMethod;
    private final PaperLikeFlavor paperLikeFlavor;
    private final Class<?> paperWorldLoaderClass;
    private final Method paperWorldDataMethod;
    private final Constructor<?> worldLoadingInfoConstructor;
    private final Constructor<?> worldLoadingInfoAndDataConstructor;
    private final Method createNewWorldDataMethod;
    private final Method levelStorageAccessMethod;
    private final Field worldLoaderContextField;
    private final Method serverRegistryAccessMethod;
    private final Field settingsField;
    private final Field optionsField;
    private final Method isDemoMethod;
    private final Method unloadWorldAsyncMethod;
    private final Method chunkAtAsyncMethod;
    private final Method removeLevelMethod;
    private final String paperLikeResolution;

    CapabilitySnapshot(
            ServerFamily serverFamily,
            boolean regionizedRuntime,
            Object worldsProvider,
            Class<?> worldsLevelStemClass,
            Class<?> worldsGeneratorTypeClass,
            String worldsProviderResolution,
            Object bukkitServer,
            Object minecraftServer,
            Method createLevelMethod,
            PaperLikeFlavor paperLikeFlavor,
            Class<?> paperWorldLoaderClass,
            Method paperWorldDataMethod,
            Constructor<?> worldLoadingInfoConstructor,
            Constructor<?> worldLoadingInfoAndDataConstructor,
            Method createNewWorldDataMethod,
            Method levelStorageAccessMethod,
            Field worldLoaderContextField,
            Method serverRegistryAccessMethod,
            Field settingsField,
            Field optionsField,
            Method isDemoMethod,
            Method unloadWorldAsyncMethod,
            Method chunkAtAsyncMethod,
            Method removeLevelMethod,
            String paperLikeResolution
    ) {
        this.serverFamily = serverFamily;
        this.regionizedRuntime = regionizedRuntime;
        this.worldsProvider = worldsProvider;
        this.worldsLevelStemClass = worldsLevelStemClass;
        this.worldsGeneratorTypeClass = worldsGeneratorTypeClass;
        this.worldsProviderResolution = worldsProviderResolution;
        this.bukkitServer = bukkitServer;
        this.minecraftServer = minecraftServer;
        this.createLevelMethod = createLevelMethod;
        this.paperLikeFlavor = paperLikeFlavor;
        this.paperWorldLoaderClass = paperWorldLoaderClass;
        this.paperWorldDataMethod = paperWorldDataMethod;
        this.worldLoadingInfoConstructor = worldLoadingInfoConstructor;
        this.worldLoadingInfoAndDataConstructor = worldLoadingInfoAndDataConstructor;
        this.createNewWorldDataMethod = createNewWorldDataMethod;
        this.levelStorageAccessMethod = levelStorageAccessMethod;
        this.worldLoaderContextField = worldLoaderContextField;
        this.serverRegistryAccessMethod = serverRegistryAccessMethod;
        this.settingsField = settingsField;
        this.optionsField = optionsField;
        this.isDemoMethod = isDemoMethod;
        this.unloadWorldAsyncMethod = unloadWorldAsyncMethod;
        this.chunkAtAsyncMethod = chunkAtAsyncMethod;
        this.removeLevelMethod = removeLevelMethod;
        this.paperLikeResolution = paperLikeResolution;
    }

    public static CapabilitySnapshot probe() {
        Server server = Bukkit.getServer();
        Object bukkitServer = server;
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

        Object minecraftServer = null;
        Method createLevelMethod = null;
        PaperLikeFlavor paperLikeFlavor = PaperLikeFlavor.UNSUPPORTED;
        Class<?> paperWorldLoaderClass = null;
        Method paperWorldDataMethod = null;
        Constructor<?> worldLoadingInfoConstructor = null;
        Constructor<?> worldLoadingInfoAndDataConstructor = null;
        Method createNewWorldDataMethod = null;
        Method levelStorageAccessMethod = null;
        Field worldLoaderContextField = null;
        Method serverRegistryAccessMethod = null;
        Field settingsField = null;
        Field optionsField = null;
        Method isDemoMethod = null;
        Method removeLevelMethod = null;
        String paperLikeResolution = "inactive";

        try {
            if (bukkitServer != null) {
                Method getServerMethod = CapabilityResolution.resolveMethod(bukkitServer.getClass(), "getServer", method -> method.getParameterCount() == 0);
                if (getServerMethod != null) {
                    minecraftServer = getServerMethod.invoke(bukkitServer);
                }
            }

            if (minecraftServer != null) {
                Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
                if (!minecraftServerClass.isInstance(minecraftServer)) {
                    throw new IllegalStateException("resolved server is not a MinecraftServer: " + minecraftServer.getClass().getName());
                }

                createLevelMethod = CapabilityResolution.resolveCreateLevelMethod(minecraftServer.getClass());
                removeLevelMethod = CapabilityResolution.resolveMethod(minecraftServer.getClass(), "removeLevel", method -> {
                    Class<?>[] params = method.getParameterTypes();
                    return params.length == 1 && "ServerLevel".equals(params[0].getSimpleName());
                });
                worldLoaderContextField = CapabilityResolution.resolveField(minecraftServer.getClass(), "worldLoaderContext");
                serverRegistryAccessMethod = CapabilityResolution.resolveServerRegistryAccessMethod(minecraftServer.getClass());
                settingsField = CapabilityResolution.resolveField(minecraftServer.getClass(), "settings");
                optionsField = CapabilityResolution.resolveField(minecraftServer.getClass(), "options");
                isDemoMethod = CapabilityResolution.resolveMethod(minecraftServer.getClass(), "isDemo", method -> method.getParameterCount() == 0 && boolean.class.equals(method.getReturnType()));

                Class<?> mainClass = Class.forName("net.minecraft.server.Main");
                createNewWorldDataMethod = CapabilityResolution.resolveCreateNewWorldDataMethod(mainClass);

                Class<?> paperLoaderCandidate = Class.forName("io.papermc.paper.world.PaperWorldLoader");
                paperWorldLoaderClass = paperLoaderCandidate;
                paperWorldDataMethod = CapabilityResolution.resolvePaperWorldDataMethod(paperLoaderCandidate);
                Class<?> worldLoadingInfoClass = Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfo");
                worldLoadingInfoConstructor = CapabilityResolution.resolveWorldLoadingInfoConstructor(worldLoadingInfoClass);

                if (createLevelMethod.getParameterCount() == 3) {
                    Class<?> worldLoadingInfoAndDataClass = Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfoAndData");
                    worldLoadingInfoAndDataConstructor = CapabilityResolution.resolveWorldLoadingInfoAndDataConstructor(worldLoadingInfoAndDataClass);
                    paperLikeFlavor = PaperLikeFlavor.CURRENT_INFO_AND_DATA;
                } else {
                    Class<?> levelStorageSourceClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource");
                    levelStorageAccessMethod = CapabilityResolution.resolveLevelStorageAccessMethod(levelStorageSourceClass);
                    paperLikeFlavor = PaperLikeFlavor.LEGACY_STORAGE_ACCESS;
                }

                paperLikeResolution = "available(flavor=" + paperLikeFlavor.name().toLowerCase(Locale.ROOT)
                        + ", createLevel=" + createLevelMethod.toGenericString() + ")";
            }
        } catch (Throwable e) {
            paperLikeResolution = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            createLevelMethod = null;
            paperLikeFlavor = PaperLikeFlavor.UNSUPPORTED;
            paperWorldLoaderClass = null;
            paperWorldDataMethod = null;
            worldLoadingInfoConstructor = null;
            worldLoadingInfoAndDataConstructor = null;
            createNewWorldDataMethod = null;
            levelStorageAccessMethod = null;
            worldLoaderContextField = null;
            serverRegistryAccessMethod = null;
            settingsField = null;
            optionsField = null;
            isDemoMethod = null;
            removeLevelMethod = null;
        }

        Object resolvedServer = bukkitServer;
        Method unloadWorldAsyncMethod = resolvedServer == null ? null : CapabilityProbe.attempt(
                "server#unloadWorldAsync",
                () -> CapabilityResolution.resolveMethod(resolvedServer.getClass(), "unloadWorldAsync", method -> {
                    Class<?>[] params = method.getParameterTypes();
                    return params.length == 3
                            && World.class.equals(params[0])
                            && boolean.class.equals(params[1])
                            && "Consumer".equals(params[2].getSimpleName());
                }),
                null);

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
                bukkitServer,
                minecraftServer,
                createLevelMethod,
                paperLikeFlavor,
                paperWorldLoaderClass,
                paperWorldDataMethod,
                worldLoadingInfoConstructor,
                worldLoadingInfoAndDataConstructor,
                createNewWorldDataMethod,
                levelStorageAccessMethod,
                worldLoaderContextField,
                serverRegistryAccessMethod,
                settingsField,
                optionsField,
                isDemoMethod,
                unloadWorldAsyncMethod,
                chunkAtAsyncMethod,
                removeLevelMethod,
                paperLikeResolution
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

    public Object bukkitServer() {
        return bukkitServer;
    }

    public Object minecraftServer() {
        return minecraftServer;
    }

    public Method createLevelMethod() {
        return createLevelMethod;
    }

    public PaperLikeFlavor paperLikeFlavor() {
        return paperLikeFlavor;
    }

    public Class<?> paperWorldLoaderClass() {
        return paperWorldLoaderClass;
    }

    public Method paperWorldDataMethod() {
        return paperWorldDataMethod;
    }

    public Constructor<?> worldLoadingInfoConstructor() {
        return worldLoadingInfoConstructor;
    }

    public Constructor<?> worldLoadingInfoAndDataConstructor() {
        return worldLoadingInfoAndDataConstructor;
    }

    public Method createNewWorldDataMethod() {
        return createNewWorldDataMethod;
    }

    public Method levelStorageAccessMethod() {
        return levelStorageAccessMethod;
    }

    public Field worldLoaderContextField() {
        return worldLoaderContextField;
    }

    public Method serverRegistryAccessMethod() {
        return serverRegistryAccessMethod;
    }

    public Field settingsField() {
        return settingsField;
    }

    public Field optionsField() {
        return optionsField;
    }

    public Method isDemoMethod() {
        return isDemoMethod;
    }

    public Method unloadWorldAsyncMethod() {
        return unloadWorldAsyncMethod;
    }

    public Method chunkAtAsyncMethod() {
        return chunkAtAsyncMethod;
    }

    public Method removeLevelMethod() {
        return removeLevelMethod;
    }

    public boolean hasWorldsProvider() {
        return worldsProvider != null && worldsLevelStemClass != null && worldsGeneratorTypeClass != null;
    }

    public boolean hasPaperLikeRuntime() {
        return minecraftServer != null
                && createLevelMethod != null
                && serverRegistryAccessMethod != null
                && paperLikeFlavor != PaperLikeFlavor.UNSUPPORTED;
    }

    public String worldsProviderResolution() {
        return worldsProviderResolution;
    }

    public String paperLikeResolution() {
        return paperLikeResolution;
    }

    public String describe() {
        return "family=" + serverFamily.id()
                + ", regionizedRuntime=" + regionizedRuntime
                + ", worldsProvider=" + worldsProviderResolution
                + ", paperLike=" + paperLikeResolution
                + ", serverRegistryAccess=" + (serverRegistryAccessMethod != null)
                + ", unloadAsync=" + (unloadWorldAsyncMethod != null)
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
        return CapabilityProbe.succeeds("io.canvasmc.canvas.region.WorldRegionizer",
                () -> Class.forName("io.canvasmc.canvas.region.WorldRegionizer"));
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
