package art.arcane.iris.core.link;

import art.arcane.iris.Iris;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaWorldsLink {
    private static volatile FoliaWorldsLink instance;
    private final Object provider;
    private final Class<?> levelStemClass;
    private final Class<?> generatorTypeClass;
    private final Object minecraftServer;
    private final Method minecraftServerCreateLevelMethod;

    private FoliaWorldsLink(
            Object provider,
            Class<?> levelStemClass,
            Class<?> generatorTypeClass,
            Object minecraftServer,
            Method minecraftServerCreateLevelMethod
    ) {
        this.provider = provider;
        this.levelStemClass = levelStemClass;
        this.generatorTypeClass = generatorTypeClass;
        this.minecraftServer = minecraftServer;
        this.minecraftServerCreateLevelMethod = minecraftServerCreateLevelMethod;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static FoliaWorldsLink get() {
        FoliaWorldsLink current = instance;
        if (current != null && current.isActive()) {
            return current;
        }

        synchronized (FoliaWorldsLink.class) {
            if (instance != null && instance.isActive()) {
                return instance;
            }

            Object loadedProvider = null;
            Class<?> loadedLevelStemClass = null;
            Class<?> loadedGeneratorTypeClass = null;
            Object loadedMinecraftServer = null;
            Method loadedMinecraftServerCreateLevelMethod = null;

            try {
                Server.class.getDeclaredMethod("isGlobalTickThread");
                try {
                    Class<?> worldsProviderClass = Class.forName("net.thenextlvl.worlds.api.WorldsProvider");
                    loadedLevelStemClass = Class.forName("net.thenextlvl.worlds.api.generator.LevelStem");
                    loadedGeneratorTypeClass = Class.forName("net.thenextlvl.worlds.api.generator.GeneratorType");
                    loadedProvider = Bukkit.getServicesManager().load((Class) worldsProviderClass);
                } catch (Throwable ignored) {
                    Object[] resolved = resolveProviderFromServices();
                    loadedProvider = resolved[0];
                    loadedLevelStemClass = (Class<?>) resolved[1];
                    loadedGeneratorTypeClass = (Class<?>) resolved[2];
                }
            } catch (Throwable ignored) {
            }

            try {
                Object bukkitServer = Bukkit.getServer();
                if (bukkitServer != null) {
                    Method getServerMethod = bukkitServer.getClass().getMethod("getServer");
                    Object candidateMinecraftServer = getServerMethod.invoke(bukkitServer);
                    Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
                    if (minecraftServerClass.isInstance(candidateMinecraftServer)) {
                        loadedMinecraftServerCreateLevelMethod = minecraftServerClass.getMethod(
                                "createLevel",
                                Class.forName("net.minecraft.world.level.dimension.LevelStem"),
                                Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfo"),
                                Class.forName("net.minecraft.world.level.storage.LevelStorageSource$LevelStorageAccess"),
                                Class.forName("net.minecraft.world.level.storage.PrimaryLevelData")
                        );
                        loadedMinecraftServer = candidateMinecraftServer;
                    }
                }
            } catch (Throwable ignored) {
            }

            instance = new FoliaWorldsLink(
                    loadedProvider,
                    loadedLevelStemClass,
                    loadedGeneratorTypeClass,
                    loadedMinecraftServer,
                    loadedMinecraftServerCreateLevelMethod
            );
            return instance;
        }
    }

    public boolean isActive() {
        return isWorldsProviderActive() || isPaperWorldLoaderActive();
    }

    public CompletableFuture<World> createWorld(WorldCreator creator) {
        if (isWorldsProviderActive()) {
            CompletableFuture<World> providerFuture = createWorldViaProvider(creator);
            if (providerFuture != null) {
                return providerFuture;
            }
        }

        if (isPaperWorldLoaderActive()) {
            return createWorldViaPaperWorldLoader(creator);
        }

        return null;
    }

    public boolean unloadWorld(World world, boolean save) {
        if (world == null) {
            return false;
        }

        CompletableFuture<Boolean> asyncWorldUnload = unloadWorldViaAsyncApi(world, save);
        if (asyncWorldUnload != null) {
            return resolveAsyncUnload(asyncWorldUnload);
        }

        try {
            return Bukkit.unloadWorld(world, save);
        } catch (UnsupportedOperationException unsupported) {
            if (minecraftServer == null) {
                throw unsupported;
            }
        }

        try {
            if (save) {
                world.save();
            }

            Object serverLevel = invoke(world, "getHandle");
            closeServerLevel(world, serverLevel);
            detachServerLevel(serverLevel, world.getName());
            return Bukkit.getWorld(world.getName()) == null;
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to unload world \"" + world.getName() + "\" via Folia runtime world-loader bridge.", unwrap(e));
        }
    }

    private boolean resolveAsyncUnload(CompletableFuture<Boolean> asyncWorldUnload) {
        if (J.isPrimaryThread()) {
            if (!asyncWorldUnload.isDone()) {
                return true;
            }

            try {
                return Boolean.TRUE.equals(asyncWorldUnload.join());
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to consume async world unload result.", unwrap(e));
            }
        }

        try {
            return Boolean.TRUE.equals(asyncWorldUnload.get(120, TimeUnit.SECONDS));
        } catch (Throwable e) {
            throw new IllegalStateException("Failed while waiting for async world unload result.", unwrap(e));
        }
    }

    private CompletableFuture<Boolean> unloadWorldViaAsyncApi(World world, boolean save) {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            return null;
        }

        Method unloadWorldAsyncMethod;
        try {
            unloadWorldAsyncMethod = bukkitServer.getClass().getMethod("unloadWorldAsync", World.class, boolean.class, Consumer.class);
        } catch (Throwable ignored) {
            return null;
        }

        CompletableFuture<Boolean> callbackFuture = new CompletableFuture<>();
        Runnable invokeTask = () -> {
            Consumer<Boolean> callback = result -> callbackFuture.complete(Boolean.TRUE.equals(result));
            try {
                unloadWorldAsyncMethod.invoke(bukkitServer, world, save, callback);
            } catch (Throwable e) {
                callbackFuture.completeExceptionally(unwrap(e));
            }
        };

        if (J.isFolia() && !isGlobalTickThread()) {
            CompletableFuture<Void> scheduled = J.sfut(invokeTask);
            if (scheduled == null) {
                callbackFuture.completeExceptionally(new IllegalStateException("Failed to schedule global world-unload task."));
                return callbackFuture;
            }
            scheduled.whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    callbackFuture.completeExceptionally(unwrap(throwable));
                }
            });
        } else {
            invokeTask.run();
        }

        return callbackFuture;
    }

    private boolean isWorldsProviderActive() {
        return provider != null && levelStemClass != null && generatorTypeClass != null;
    }

    private boolean isPaperWorldLoaderActive() {
        return minecraftServer != null && minecraftServerCreateLevelMethod != null;
    }

    private CompletableFuture<World> createWorldViaProvider(WorldCreator creator) {
        try {
            Path worldPath = new File(Bukkit.getWorldContainer(), creator.name()).toPath();
            Object builder = invoke(provider, "levelBuilder", worldPath);
            builder = invoke(builder, "name", creator.name());
            builder = invoke(builder, "seed", creator.seed());
            builder = invoke(builder, "levelStem", resolveLevelStem(creator.environment()));
            builder = invoke(builder, "chunkGenerator", creator.generator());
            builder = invoke(builder, "biomeProvider", creator.biomeProvider());
            builder = invoke(builder, "generatorType", resolveGeneratorType(creator.type()));
            builder = invoke(builder, "structures", creator.generateStructures());
            builder = invoke(builder, "hardcore", creator.hardcore());
            Object levelBuilder = invoke(builder, "build");
            Object async = invoke(levelBuilder, "createAsync");
            if (async instanceof CompletableFuture<?> future) {
                return future.thenApply(world -> (World) world);
            }

            return CompletableFuture.failedFuture(new IllegalStateException("Worlds provider createAsync did not return CompletableFuture."));
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<World> createWorldViaPaperWorldLoader(WorldCreator creator) {
        Object levelStorageAccess = null;
        try {
            if (creator.environment() != World.Environment.NORMAL) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException("PaperWorldLoader fallback only supports OVERWORLD worlds."));
            }

            World existing = Bukkit.getWorld(creator.name());
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }

            stageRuntimeGenerator(creator);
            levelStorageAccess = createRuntimeStorageAccess(creator.name());
            Object primaryLevelData = createPrimaryLevelData(levelStorageAccess, creator.name());
            Object runtimeStemKey = createRuntimeLevelStemKey(creator.name());
            Object worldLoadingInfo = createWorldLoadingInfo(creator.name(), runtimeStemKey);
            Object levelStem = resolveCreateLevelStem(creator);
            Object[] createLevelArgs = new Object[]{levelStem, worldLoadingInfo, levelStorageAccess, primaryLevelData};
            Method createLevelMethod = minecraftServerCreateLevelMethod;
            if (createLevelMethod == null || !matches(createLevelMethod.getParameterTypes(), createLevelArgs)) {
                createLevelMethod = resolveMethod(minecraftServer.getClass(), "createLevel", createLevelArgs);
            }

            try {
                createLevelMethod.invoke(minecraftServer, createLevelArgs);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("createLevel argument mismatch. Method=" + formatMethod(createLevelMethod) + " Args=" + formatArgs(createLevelArgs), exception);
            }

            World loaded = Bukkit.getWorld(creator.name());
            if (loaded == null) {
                Iris.clearStagedRuntimeWorldGenerator(creator.name());
                closeLevelStorageAccess(levelStorageAccess);
                return CompletableFuture.failedFuture(new IllegalStateException("PaperWorldLoader did not load world \"" + creator.name() + "\"."));
            }

            Iris.clearStagedRuntimeWorldGenerator(creator.name());
            return CompletableFuture.completedFuture(loaded);
        } catch (Throwable e) {
            Iris.clearStagedRuntimeWorldGenerator(creator.name());
            closeLevelStorageAccess(levelStorageAccess);
            return CompletableFuture.failedFuture(unwrap(e));
        }
    }

    private Object createRuntimeStorageAccess(String worldName) throws ReflectiveOperationException {
        Class<?> levelStorageSourceClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource");
        Object levelStorageSource = levelStorageSourceClass
                .getMethod("createDefault", Path.class)
                .invoke(null, Bukkit.getWorldContainer().toPath());

        Object overworldStemKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                .getField("OVERWORLD")
                .get(null);
        Method validateAndCreateAccess = resolveMethod(levelStorageSourceClass, "validateAndCreateAccess", worldName, overworldStemKey);
        return validateAndCreateAccess.invoke(levelStorageSource, worldName, overworldStemKey);
    }

    private Object createPrimaryLevelData(Object levelStorageAccess, String worldName) throws ReflectiveOperationException {
        Class<?> paperWorldLoaderClass = Class.forName("io.papermc.paper.world.PaperWorldLoader");
        Class<?> levelStorageAccessClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource$LevelStorageAccess");
        Object levelDataResult = paperWorldLoaderClass
                .getMethod("getLevelData", levelStorageAccessClass)
                .invoke(null, levelStorageAccess);
        boolean fatalError = (boolean) invoke(levelDataResult, "fatalError");
        if (fatalError) {
            throw new IllegalStateException("PaperWorldLoader reported a fatal world-data error for \"" + worldName + "\".");
        }

        Object dataTag = invoke(levelDataResult, "dataTag");
        if (dataTag != null) {
            throw new IllegalStateException("Runtime studio world folder \"" + worldName + "\" already contains level data.");
        }

        Object worldLoaderContext = getPublicField(minecraftServer, "worldLoaderContext");
        Object datapackDimensions = invoke(worldLoaderContext, "datapackDimensions");
        Object levelStemRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Object levelStemRegistry = invoke(datapackDimensions, "lookupOrThrow", levelStemRegistryKey);
        Object dedicatedSettings = getPublicField(minecraftServer, "settings");
        boolean demo = (boolean) invoke(minecraftServer, "isDemo");
        Object options = getPublicField(minecraftServer, "options");
        boolean bonusChest = (boolean) invoke(options, "has", "bonusChest");

        Class<?> mainClass = Class.forName("net.minecraft.server.Main");
        Method createNewWorldDataMethod = resolveMethod(mainClass, "createNewWorldData", dedicatedSettings, worldLoaderContext, levelStemRegistry, demo, bonusChest);
        Object dataLoadOutput = createNewWorldDataMethod.invoke(null, dedicatedSettings, worldLoaderContext, levelStemRegistry, demo, bonusChest);

        Object primaryLevelData = invoke(dataLoadOutput, "cookie");
        invoke(primaryLevelData, "checkName", worldName);
        Object modCheck = invoke(minecraftServer, "getModdedStatus");
        boolean modified = (boolean) invoke(modCheck, "shouldReportAsModified");
        String modName = (String) invoke(minecraftServer, "getServerModName");
        invoke(primaryLevelData, "setModdedInfo", modName, modified);
        return primaryLevelData;
    }

    private Object createWorldLoadingInfo(String worldName, Object runtimeStemKey) throws ReflectiveOperationException {
        Class<?> worldLoadingInfoClass = Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfo");
        Constructor<?> constructor = resolveConstructor(worldLoadingInfoClass, 0, worldName, "normal", runtimeStemKey, true);
        return constructor.newInstance(0, worldName, "normal", runtimeStemKey, true);
    }

    private Object createRuntimeLevelStemKey(String worldName) throws ReflectiveOperationException {
        String sanitized = worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]", "_");
        String path = "runtime/" + sanitized;
        Object identifier = Class.forName("net.minecraft.resources.Identifier")
                .getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "iris", path);
        Object levelStemRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
        Method createMethod = resolveMethod(resourceKeyClass, "create", levelStemRegistryKey, identifier);
        return createMethod.invoke(null, levelStemRegistryKey, identifier);
    }

    private Object resolveCreateLevelStem(WorldCreator creator) throws ReflectiveOperationException {
        Object irisLevelStem = resolveIrisLevelStem(creator);
        if (irisLevelStem != null) {
            return irisLevelStem;
        }

        return getOverworldLevelStem();
    }

    private Object resolveIrisLevelStem(WorldCreator creator) throws ReflectiveOperationException {
        ChunkGenerator generator = creator.generator();
        if (!(generator instanceof PlatformChunkGenerator)) {
            return null;
        }

        Object registryAccess = invoke(minecraftServer, "registryAccess");
        Object binding = INMS.get();
        Method levelStemMethod;
        try {
            levelStemMethod = resolveMethod(binding.getClass(), "levelStem", registryAccess, generator);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Iris NMS binding does not expose levelStem(RegistryAccess, ChunkGenerator) for runtime world \"" + creator.name() + "\".", e);
        }

        Object levelStem;
        try {
            levelStem = levelStemMethod.invoke(binding, registryAccess, generator);
        } catch (InvocationTargetException e) {
            Throwable cause = unwrap(e);
            throw new IllegalStateException("Iris failed to resolve runtime level stem for world \"" + creator.name() + "\".", cause);
        }

        if (levelStem == null) {
            throw new IllegalStateException("Iris resolved a null runtime level stem for world \"" + creator.name() + "\".");
        }
        return levelStem;
    }

    private Object getOverworldLevelStem() throws ReflectiveOperationException {
        Object levelStemRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Object registryAccess = invoke(minecraftServer, "registryAccess");
        Object levelStemRegistry = invoke(registryAccess, "lookupOrThrow", levelStemRegistryKey);
        Object overworldStemKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                .getField("OVERWORLD")
                .get(null);
        Object levelStem;
        try {
            levelStem = invoke(levelStemRegistry, "getValue", overworldStemKey);
        } catch (NoSuchMethodException ignored) {
            Object rawLevelStem = invoke(levelStemRegistry, "get", overworldStemKey);
            levelStem = extractRegistryValue(rawLevelStem);
        }
        if (levelStem == null) {
            throw new IllegalStateException("Unable to resolve OVERWORLD LevelStem from registry.");
        }
        return levelStem;
    }

    private static Object extractRegistryValue(Object rawValue) throws ReflectiveOperationException {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof java.util.Optional<?> optionalValue) {
            Object nestedValue = optionalValue.orElse(null);
            if (nestedValue == null) {
                return null;
            }
            return extractRegistryValue(nestedValue);
        }

        try {
            Method valueMethod = rawValue.getClass().getMethod("value");
            return valueMethod.invoke(rawValue);
        } catch (NoSuchMethodException ignored) {
            return rawValue;
        }
    }

    private static Object getPublicField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getField(fieldName);
        return field.get(target);
    }

    private static void closeLevelStorageAccess(Object levelStorageAccess) {
        if (levelStorageAccess == null) {
            return;
        }

        try {
            Method close = levelStorageAccess.getClass().getMethod("close");
            close.invoke(levelStorageAccess);
        } catch (Throwable ignored) {
        }
    }

    private static void stageRuntimeGenerator(WorldCreator creator) throws ReflectiveOperationException {
        ChunkGenerator generator = creator.generator();
        if (generator == null) {
            throw new IllegalStateException("Runtime world creation requires a non-null chunk generator.");
        }

        Iris.stageRuntimeWorldGenerator(creator.name(), generator, creator.biomeProvider());
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            throw new IllegalStateException("Bukkit server is unavailable.");
        }

        Field configurationField = bukkitServer.getClass().getDeclaredField("configuration");
        configurationField.setAccessible(true);
        Object rawConfiguration = configurationField.get(bukkitServer);
        if (!(rawConfiguration instanceof YamlConfiguration configuration)) {
            throw new IllegalStateException("CraftServer configuration field is unavailable.");
        }

        ConfigurationSection worldsSection = configuration.getConfigurationSection("worlds");
        if (worldsSection == null) {
            worldsSection = configuration.createSection("worlds");
        }

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(creator.name());
        if (worldSection == null) {
            worldSection = worldsSection.createSection(creator.name());
        }

        worldSection.set("generator", "Iris:runtime");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeWorldFromCraftServerMap(String worldName) throws ReflectiveOperationException {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            return;
        }

        Field worldsField = bukkitServer.getClass().getDeclaredField("worlds");
        worldsField.setAccessible(true);
        Object worldsRaw = worldsField.get(bukkitServer);
        if (worldsRaw instanceof Map worldsMap) {
            worldsMap.remove(worldName);
            worldsMap.remove(worldName.toLowerCase(Locale.ROOT));
        }
    }

    private static void closeServerLevel(World world, Object serverLevel) throws Throwable {
        Method closeLevelMethod = resolveMethod(serverLevel.getClass(), "close");
        if (!J.isFolia()) {
            closeLevelMethod.invoke(serverLevel);
            return;
        }

        Location spawn = world.getSpawnLocation();
        int chunkX = spawn == null ? 0 : spawn.getBlockX() >> 4;
        int chunkZ = spawn == null ? 0 : spawn.getBlockZ() >> 4;
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                closeLevelMethod.invoke(serverLevel);
                closeFuture.complete(null);
            } catch (Throwable e) {
                closeFuture.completeExceptionally(unwrap(e));
            }
        });
        if (!scheduled) {
            throw new IllegalStateException("Failed to schedule region close task for world \"" + world.getName() + "\".");
        }
        closeFuture.get(90, TimeUnit.SECONDS);
    }

    private void detachServerLevel(Object serverLevel, String worldName) throws Throwable {
        Runnable detachTask = () -> {
            try {
                Method removeLevelMethod = resolveMethod(minecraftServer.getClass(), "removeLevel", serverLevel);
                removeLevelMethod.invoke(minecraftServer, serverLevel);
                removeWorldFromCraftServerMap(worldName);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        if (!J.isFolia()) {
            detachTask.run();
            return;
        }

        if (isGlobalTickThread()) {
            detachTask.run();
            return;
        }

        CompletableFuture<Void> detachFuture = J.sfut(() -> detachTask.run());
        if (detachFuture == null) {
            throw new IllegalStateException("Failed to schedule global detach task for world \"" + worldName + "\".");
        }
        detachFuture.get(15, TimeUnit.SECONDS);
    }

    private static boolean isGlobalTickThread() {
        Server server = Bukkit.getServer();
        if (server == null) {
            return false;
        }

        try {
            Method isGlobalTickThreadMethod = server.getClass().getMethod("isGlobalTickThread");
            return Boolean.TRUE.equals(isGlobalTickThreadMethod.invoke(server));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException && invocationTargetException.getCause() != null) {
            return unwrap(invocationTargetException.getCause());
        }
        if (throwable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable instanceof java.util.concurrent.ExecutionException executionException && executionException.getCause() != null) {
            return unwrap(executionException.getCause());
        }
        return throwable;
    }

    private static Object[] resolveProviderFromServices() {
        Object provider = null;
        Class<?> levelStem = null;
        Class<?> generatorType = null;

        try {
            Collection<Class<?>> knownServices = Bukkit.getServicesManager().getKnownServices();
            for (Class<?> serviceClass : knownServices) {
                if (!"net.thenextlvl.worlds.api.WorldsProvider".equals(serviceClass.getName())) {
                    continue;
                }

                RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) serviceClass);
                if (registration == null) {
                    continue;
                }

                provider = registration.getProvider();
                ClassLoader loader = serviceClass.getClassLoader();
                if (loader == null && provider != null) {
                    loader = provider.getClass().getClassLoader();
                }
                if (loader != null) {
                    levelStem = Class.forName("net.thenextlvl.worlds.api.generator.LevelStem", false, loader);
                    generatorType = Class.forName("net.thenextlvl.worlds.api.generator.GeneratorType", false, loader);
                }
                break;
            }
        } catch (Throwable ignored) {
        }

        return new Object[]{provider, levelStem, generatorType};
    }

    private Object resolveLevelStem(World.Environment environment) {
        String key;
        if (environment == World.Environment.NETHER) {
            key = "NETHER";
        } else if (environment == World.Environment.THE_END) {
            key = "END";
        } else {
            key = "OVERWORLD";
        }

        return enumValue(levelStemClass, key);
    }

    private Object resolveGeneratorType(WorldType worldType) {
        String typeName = worldType == null ? "NORMAL" : worldType.getName();
        String key;
        if ("FLAT".equalsIgnoreCase(typeName)) {
            key = "FLAT";
        } else if ("AMPLIFIED".equalsIgnoreCase(typeName)) {
            key = "AMPLIFIED";
        } else if ("LARGE_BIOMES".equalsIgnoreCase(typeName) || "LARGEBIOMES".equalsIgnoreCase(typeName)) {
            key = "LARGE_BIOMES";
        } else {
            key = "NORMAL";
        }

        return enumValue(generatorTypeClass, key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String key) {
        Class<? extends Enum> typed = enumClass.asSubclass(Enum.class);
        return Enum.valueOf(typed, key);
    }

    private static Method resolveMethod(Class<?> owner, String methodName, Object... args) throws NoSuchMethodException {
        Method selected = findMatchingMethod(owner.getMethods(), methodName, args);
        if (selected != null) {
            return selected;
        }

        Class<?> current = owner;
        while (current != null) {
            selected = findMatchingMethod(current.getDeclaredMethods(), methodName, args);
            if (selected != null) {
                selected.setAccessible(true);
                return selected;
            }
            current = current.getSuperclass();
        }

        throw new NoSuchMethodException(owner.getName() + "#" + methodName);
    }

    private static Constructor<?> resolveConstructor(Class<?> owner, Object... args) throws NoSuchMethodException {
        Constructor<?> selected = findMatchingConstructor(owner.getConstructors(), args);
        if (selected != null) {
            return selected;
        }

        selected = findMatchingConstructor(owner.getDeclaredConstructors(), args);
        if (selected != null) {
            selected.setAccessible(true);
            return selected;
        }

        throw new NoSuchMethodException(owner.getName() + "#<init>");
    }

    private static Method findMatchingMethod(Method[] methods, String methodName, Object... args) {
        Method selected = null;
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            if (matches(params, args)) {
                selected = method;
                break;
            }
        }

        return selected;
    }

    private static String formatMethod(Method method) {
        if (method == null) {
            return "<null>";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getName())
                .append("#")
                .append(method.getName())
                .append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(")");
        return builder.toString();
    }

    private static String formatArgs(Object... args) {
        if (args == null) {
            return "<null>";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object argument = args[i];
            builder.append(argument == null ? "null" : argument.getClass().getName());
        }
        builder.append("]");
        return builder.toString();
    }

    private static Constructor<?> findMatchingConstructor(Constructor<?>[] constructors, Object... args) {
        Constructor<?> selected = null;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            if (matches(params, args)) {
                selected = constructor;
                break;
            }
        }

        return selected;
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method selected = resolveMethod(target.getClass(), methodName, args);
        return selected.invoke(target, args);
    }

    private static boolean matches(Class<?>[] params, Object[] args) {
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            Class<?> parameterType = params[i];
            if (arg == null) {
                if (parameterType.isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> boxedParameterType = box(parameterType);
            if (!boxedParameterType.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }

        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }
}
