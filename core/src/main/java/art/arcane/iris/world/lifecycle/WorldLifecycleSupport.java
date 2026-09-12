package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class WorldLifecycleSupport {
    private WorldLifecycleSupport() {
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException && invocationTargetException.getCause() != null) {
            return unwrap(invocationTargetException.getCause());
        }
        if (throwable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return unwrap(executionException.getCause());
        }
        return throwable;
    }

    static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        return method.invoke(target, args);
    }

    static Object invokeNamed(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    static Object read(Field field, Object target) throws IllegalAccessException {
        return field.get(target);
    }

    static void stageRuntimeConfiguration(String worldName) throws ReflectiveOperationException {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            throw new IllegalStateException("Bukkit server is unavailable.");
        }

        Field configurationField = CapabilityResolution.resolveField(bukkitServer.getClass(), "configuration");
        Object rawConfiguration = configurationField.get(bukkitServer);
        if (!(rawConfiguration instanceof YamlConfiguration configuration)) {
            throw new IllegalStateException("CraftServer configuration field is unavailable.");
        }

        ConfigurationSection worldsSection = configuration.getConfigurationSection("worlds");
        if (worldsSection == null) {
            worldsSection = configuration.createSection("worlds");
        }

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
        if (worldSection == null) {
            worldSection = worldsSection.createSection(worldName);
        }

        worldSection.set("generator", "Iris:runtime");
    }

    static Object getRuntimeDatapackDimensions(CapabilitySnapshot capabilities) throws ReflectiveOperationException {
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Method datapackDimensionsMethod = CapabilityResolution.resolveMethod(worldLoaderContext.getClass(), "datapackDimensions", method -> method.getParameterCount() == 0);
        if (datapackDimensionsMethod == null) {
            throw new IllegalStateException("DataLoadContext does not expose datapackDimensions().");
        }
        Object datapackDimensions = datapackDimensionsMethod.invoke(worldLoaderContext);
        if (datapackDimensions == null) {
            throw new IllegalStateException("DataLoadContext.datapackDimensions() returned null.");
        }
        return datapackDimensions;
    }

    static Object getRuntimeServerRegistryAccess(CapabilitySnapshot capabilities) throws ReflectiveOperationException {
        Method registryAccessMethod = capabilities.serverRegistryAccessMethod();
        if (registryAccessMethod == null) {
            throw new IllegalStateException("MinecraftServer does not expose registryAccess().");
        }
        Object registryAccess = registryAccessMethod.invoke(capabilities.minecraftServer());
        if (registryAccess == null) {
            throw new IllegalStateException("MinecraftServer.registryAccess() returned null.");
        }
        return registryAccess;
    }

    static Object getRuntimeLevelStemRegistry(CapabilitySnapshot capabilities) throws ReflectiveOperationException {
        Object datapackDimensions = getRuntimeDatapackDimensions(capabilities);
        Object levelStemRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Method lookupMethod = CapabilityResolution.resolveMethod(datapackDimensions.getClass(), "lookupOrThrow", method -> method.getParameterCount() == 1);
        if (lookupMethod == null) {
            throw new IllegalStateException("Registry access does not expose lookupOrThrow(...).");
        }
        return lookupMethod.invoke(datapackDimensions, levelStemRegistryKey);
    }

    static Object createRuntimeLevelStemKey(NamespacedKey worldKey) throws ReflectiveOperationException {
        Identifier identifier = new Identifier(worldKey.getNamespace(), worldKey.getKey());
        Object rawIdentifier = Class.forName("net.minecraft.resources.Identifier")
                .getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, identifier.namespace(), identifier.key());
        Object registryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Method createMethod = Class.forName("net.minecraft.resources.ResourceKey")
                .getMethod("create", registryKey.getClass(), rawIdentifier.getClass());
        return createMethod.invoke(null, registryKey, rawIdentifier);
    }

    static Object createDimensionKey(Object stemKey) throws ReflectiveOperationException {
        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
        Method identifierMethod = CapabilityResolution.resolveMethod(resourceKeyClass, "identifier", method -> method.getParameterCount() == 0);
        Object identifier = identifierMethod.invoke(stemKey);
        Object dimensionRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("DIMENSION")
                .get(null);
        Method createMethod = resourceKeyClass.getMethod("create", dimensionRegistryKey.getClass(), identifier.getClass());
        return createMethod.invoke(null, dimensionRegistryKey, identifier);
    }

    static Object resolveRuntimeLevelStem(CapabilitySnapshot capabilities, WorldLifecycleRequest request) throws ReflectiveOperationException {
        return resolveRuntimeLevelStem(capabilities, request, INMS.get());
    }

    static Object resolveRuntimeLevelStem(CapabilitySnapshot capabilities, WorldLifecycleRequest request, INMSBinding binding) throws ReflectiveOperationException {
        ChunkGenerator generator = request.generator();
        if (generator instanceof PlatformChunkGenerator) {
            Object registryAccess = getRuntimeServerRegistryAccess(capabilities);
            try {
                Object levelStem = binding.createRuntimeLevelStem(registryAccess, generator);
                if (levelStem == null) {
                    throw new IllegalStateException("Iris NMS binding returned null runtime LevelStem.");
                }
                return levelStem;
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to create runtime LevelStem from full server registry access for world \"" + request.worldName() + "\".", unwrap(e));
            }
        }

        try {
            Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
            Object overworldKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                    .getField("OVERWORLD")
                    .get(null);
            Method getValueMethod = CapabilityResolution.resolveMethod(levelStemRegistry.getClass(), "getValue", method -> method.getParameterCount() == 1);
            if (getValueMethod != null) {
                Object resolved = getValueMethod.invoke(levelStemRegistry, overworldKey);
                if (resolved != null) {
                    return resolved;
                }
            }

            Method getMethod = CapabilityResolution.resolveMethod(levelStemRegistry.getClass(), "get", method -> method.getParameterCount() == 1);
            if (getMethod == null) {
                throw new IllegalStateException("Unable to resolve OVERWORLD LevelStem from registry.");
            }
            Object raw = getMethod.invoke(levelStemRegistry, overworldKey);
            return extractRegistryValue(raw);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to resolve fallback OVERWORLD LevelStem from datapack registry access for world \"" + request.worldName() + "\".", unwrap(e));
        }
    }

    static String runtimeLevelStemRegistrySource(WorldLifecycleRequest request) {
        if (request.generator() instanceof PlatformChunkGenerator) {
            return "full_server_registry";
        }
        return "datapack_level_stem_registry";
    }

    static Object extractRegistryValue(Object raw) throws ReflectiveOperationException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Optional<?> optional) {
            Object nested = optional.orElse(null);
            if (nested == null) {
                return null;
            }
            return extractRegistryValue(nested);
        }
        Method valueMethod = CapabilityResolution.resolveMethod(raw.getClass(), "value", method -> method.getParameterCount() == 0);
        if (valueMethod != null) {
            return valueMethod.invoke(raw);
        }
        return raw;
    }

    static void applyWorldDataNameAndModInfo(CapabilitySnapshot capabilities, Object worldDataAndGenSettings, String worldName) throws ReflectiveOperationException {
        Method dataMethod = CapabilityResolution.resolveMethod(worldDataAndGenSettings.getClass(), "data", method -> method.getParameterCount() == 0);
        if (dataMethod == null) {
            return;
        }

        Object worldData = dataMethod.invoke(worldDataAndGenSettings);
        if (worldData == null) {
            return;
        }

        Method checkNameMethod = CapabilityResolution.resolveMethod(worldData.getClass(), "checkName", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        if (checkNameMethod != null) {
            checkNameMethod.invoke(worldData, worldName);
        }

        Method getModdedStatusMethod = CapabilityResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getModdedStatus", method -> method.getParameterCount() == 0);
        Method getServerModNameMethod = CapabilityResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getServerModName", method -> method.getParameterCount() == 0);
        if (getModdedStatusMethod == null || getServerModNameMethod == null) {
            return;
        }

        Object modCheck = getModdedStatusMethod.invoke(capabilities.minecraftServer());
        Method shouldReportAsModifiedMethod = CapabilityResolution.resolveMethod(modCheck.getClass(), "shouldReportAsModified", method -> method.getParameterCount() == 0);
        Method setModdedInfoMethod = CapabilityResolution.resolveMethod(worldData.getClass(), "setModdedInfo", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 2 && String.class.equals(params[0]) && boolean.class.equals(params[1]);
        });
        if (shouldReportAsModifiedMethod == null || setModdedInfoMethod == null) {
            return;
        }

        boolean modified = Boolean.TRUE.equals(shouldReportAsModifiedMethod.invoke(modCheck));
        String modName = (String) getServerModNameMethod.invoke(capabilities.minecraftServer());
        setModdedInfoMethod.invoke(worldData, modName, modified);
    }

    static boolean hasExistingWorldData(NamespacedKey worldKey) {
        File worldFolder = IrisWorldStorage.dimensionRoot(worldKey);
        return new File(worldFolder, "region").exists()
                || new File(worldFolder, "entities").exists()
                || new File(worldFolder, "poi").exists()
                || new File(worldFolder, "data/paper/metadata.dat").exists()
                || new File(worldFolder, "paper-world.yml").exists();
    }

    static Object applySeedToWorldDataAndGenSettings(Object worldDataAndGenSettings, long seed) throws ReflectiveOperationException {
        Method genSettingsMethod = CapabilityResolution.resolveMethod(worldDataAndGenSettings.getClass(), "genSettings", method -> method.getParameterCount() == 0);
        Method dataMethod = CapabilityResolution.resolveMethod(worldDataAndGenSettings.getClass(), "data", method -> method.getParameterCount() == 0);
        if (genSettingsMethod == null || dataMethod == null) {
            throw new IllegalStateException("WorldDataAndGenSettings does not expose data()/genSettings().");
        }

        Object genSettings = genSettingsMethod.invoke(worldDataAndGenSettings);
        Method optionsMethod = CapabilityResolution.resolveMethod(genSettings.getClass(), "options", method -> method.getParameterCount() == 0);
        Method dimensionsMethod = CapabilityResolution.resolveMethod(genSettings.getClass(), "dimensions", method -> method.getParameterCount() == 0);
        if (optionsMethod == null || dimensionsMethod == null) {
            throw new IllegalStateException("WorldGenSettings does not expose options()/dimensions().");
        }

        Object options = optionsMethod.invoke(genSettings);
        Method seedMethod = CapabilityResolution.resolveMethod(options.getClass(), "seed", method -> method.getParameterCount() == 0 && long.class.equals(method.getReturnType()));
        Method withSeedMethod = CapabilityResolution.resolveMethod(options.getClass(), "withSeed", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && OptionalLong.class.equals(params[0]);
        });
        if (seedMethod == null || withSeedMethod == null) {
            throw new IllegalStateException("WorldOptions does not expose seed()/withSeed(OptionalLong).");
        }

        long currentSeed = (long) seedMethod.invoke(options);
        if (currentSeed == seed) {
            return worldDataAndGenSettings;
        }

        Object newOptions = withSeedMethod.invoke(options, OptionalLong.of(seed));
        Object newGenSettings = construct(genSettings.getClass(), newOptions, dimensionsMethod.invoke(genSettings));
        return construct(worldDataAndGenSettings.getClass(), dataMethod.invoke(worldDataAndGenSettings), newGenSettings);
    }

    private static Object construct(Class<?> type, Object first, Object second) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 2) {
                continue;
            }

            if ((first == null || params[0].isInstance(first)) && (second == null || params[1].isInstance(second))) {
                constructor.setAccessible(true);
                return constructor.newInstance(first, second);
            }
        }

        throw new IllegalStateException("No compatible two-argument constructor on " + type.getName());
    }

    static Object createCurrentWorldDataAndSettings(CapabilitySnapshot capabilities, String worldName) throws ReflectiveOperationException {
        Object settings = read(capabilities.settingsField(), capabilities.minecraftServer());
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
        boolean demo = Boolean.TRUE.equals(capabilities.isDemoMethod().invoke(capabilities.minecraftServer()));
        Object options = read(capabilities.optionsField(), capabilities.minecraftServer());
        Method hasMethod = CapabilityResolution.resolveMethod(options.getClass(), "has", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        boolean bonusChest = hasMethod != null && Boolean.TRUE.equals(hasMethod.invoke(options, "bonusChest"));
        Object dataLoadOutput = capabilities.createNewWorldDataMethod().invoke(null, settings, worldLoaderContext, levelStemRegistry, demo, bonusChest);
        Method cookieMethod = CapabilityResolution.resolveMethod(dataLoadOutput.getClass(), "cookie", method -> method.getParameterCount() == 0);
        if (cookieMethod == null) {
            throw new IllegalStateException("WorldLoader.DataLoadOutput does not expose cookie().");
        }
        Object worldDataAndGenSettings = cookieMethod.invoke(dataLoadOutput);
        applyWorldDataNameAndModInfo(capabilities, worldDataAndGenSettings, worldName);
        return worldDataAndGenSettings;
    }

    static Object createLegacyPrimaryLevelData(CapabilitySnapshot capabilities, Object levelStorageAccess, String worldName) throws ReflectiveOperationException {
        Object levelDataResult = capabilities.paperWorldDataMethod().invoke(null, levelStorageAccess);
        Method fatalErrorMethod = CapabilityResolution.resolveMethod(levelDataResult.getClass(), "fatalError", method -> method.getParameterCount() == 0);
        Method dataTagMethod = CapabilityResolution.resolveMethod(levelDataResult.getClass(), "dataTag", method -> method.getParameterCount() == 0);
        if (fatalErrorMethod != null && Boolean.TRUE.equals(fatalErrorMethod.invoke(levelDataResult))) {
            throw new IllegalStateException("Paper runtime world-data helper reported a fatal error for \"" + worldName + "\".");
        }
        if (dataTagMethod != null && dataTagMethod.invoke(levelDataResult) != null) {
            throw new IllegalStateException("Runtime world \"" + worldName + "\" already contains level data.");
        }

        Object settings = read(capabilities.settingsField(), capabilities.minecraftServer());
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
        boolean demo = Boolean.TRUE.equals(capabilities.isDemoMethod().invoke(capabilities.minecraftServer()));
        Object options = read(capabilities.optionsField(), capabilities.minecraftServer());
        Method hasMethod = CapabilityResolution.resolveMethod(options.getClass(), "has", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        boolean bonusChest = hasMethod != null && Boolean.TRUE.equals(hasMethod.invoke(options, "bonusChest"));
        Object dataLoadOutput = capabilities.createNewWorldDataMethod().invoke(null, settings, worldLoaderContext, levelStemRegistry, demo, bonusChest);
        Method cookieMethod = CapabilityResolution.resolveMethod(dataLoadOutput.getClass(), "cookie", method -> method.getParameterCount() == 0);
        if (cookieMethod == null) {
            throw new IllegalStateException("WorldLoader.DataLoadOutput does not expose cookie().");
        }
        Object primaryLevelData = cookieMethod.invoke(dataLoadOutput);

        Method checkNameMethod = CapabilityResolution.resolveMethod(primaryLevelData.getClass(), "checkName", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        if (checkNameMethod != null) {
            checkNameMethod.invoke(primaryLevelData, worldName);
        }

        Method getModdedStatusMethod = CapabilityResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getModdedStatus", method -> method.getParameterCount() == 0);
        Method getServerModNameMethod = CapabilityResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getServerModName", method -> method.getParameterCount() == 0);
        if (getModdedStatusMethod != null && getServerModNameMethod != null) {
            Object modCheck = getModdedStatusMethod.invoke(capabilities.minecraftServer());
            Method shouldReportAsModifiedMethod = CapabilityResolution.resolveMethod(modCheck.getClass(), "shouldReportAsModified", method -> method.getParameterCount() == 0);
            Method setModdedInfoMethod = CapabilityResolution.resolveMethod(primaryLevelData.getClass(), "setModdedInfo", method -> {
                Class<?>[] params = method.getParameterTypes();
                return params.length == 2 && String.class.equals(params[0]) && boolean.class.equals(params[1]);
            });
            if (shouldReportAsModifiedMethod != null && setModdedInfoMethod != null) {
                boolean modified = Boolean.TRUE.equals(shouldReportAsModifiedMethod.invoke(modCheck));
                String modName = (String) getServerModNameMethod.invoke(capabilities.minecraftServer());
                setModdedInfoMethod.invoke(primaryLevelData, modName, modified);
            }
        }

        return primaryLevelData;
    }

    static Object createLegacyStorageAccess(CapabilitySnapshot capabilities) throws ReflectiveOperationException {
        Class<?> levelStorageSourceClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource");
        Method createDefaultMethod = levelStorageSourceClass.getMethod("createDefault", Path.class);
        File levelRoot = IrisWorldStorage.levelRoot();
        Object levelStorageSource = createDefaultMethod.invoke(null, levelRoot.getParentFile().toPath());
        Method storageAccessMethod = capabilities.levelStorageAccessMethod();
        if (storageAccessMethod.getParameterCount() == 1) {
            return storageAccessMethod.invoke(levelStorageSource, levelRoot.getName());
        }
        Object overworldStemKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                .getField("OVERWORLD")
                .get(null);
        return storageAccessMethod.invoke(levelStorageSource, levelRoot.getName(), overworldStemKey);
    }

    static void closeLevelStorageAccess(Object levelStorageAccess) {
        if (levelStorageAccess == null) {
            return;
        }
        try {
            Method closeMethod = levelStorageAccess.getClass().getMethod("close");
            closeMethod.invoke(levelStorageAccess);
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to close the level storage access; the world session lock may still be held.",
                    unwrap(failure));
        }
    }

    static CompletableFuture<Boolean> unloadWorldAsync(CapabilitySnapshot capabilities, World world, boolean save) {
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable invokeTask = () -> beginUnload(capabilities, world, save, result);
        boolean folia = J.isFolia();
        if ((!folia && J.isPrimaryThread()) || (folia && isGlobalTickThread())) {
            invokeTask.run();
            return result;
        }

        CompletableFuture<Void> scheduled = runGlobalAsync(invokeTask);
        scheduled.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrap(throwable));
            }
        });
        return result;
    }

    private static void beginUnload(
            CapabilitySnapshot capabilities,
            World world,
            boolean save,
            CompletableFuture<Boolean> result
    ) {
        CompletableFuture<Boolean> operation;
        try {
            operation = unloadWorldViaAsyncApi(capabilities, world, save);
            if (operation == null) {
                operation = unloadWorldWithoutAsyncApi(capabilities, world, save);
            }
        } catch (Throwable e) {
            result.completeExceptionally(unwrap(e));
            return;
        }

        operation.whenComplete((unloaded, throwable) -> {
            if (throwable == null) {
                result.complete(Boolean.TRUE.equals(unloaded));
            } else {
                result.completeExceptionally(unwrap(throwable));
            }
        });
    }

    private static CompletableFuture<Boolean> unloadWorldWithoutAsyncApi(
            CapabilitySnapshot capabilities,
            World world,
            boolean save
    ) {
        String worldName = world.getName();
        try {
            if (capabilities.minecraftServer() == null || capabilities.removeLevelMethod() == null) {
                return CompletableFuture.completedFuture(Bukkit.unloadWorld(world, save));
            }

            if (!announceManualWorldUnload(world)) {
                return CompletableFuture.completedFuture(false);
            }

            if (save) {
                world.save();
            }
            Method getHandleMethod = world.getClass().getMethod("getHandle");
            Object serverLevel = getHandleMethod.invoke(world);
            CompletableFuture<Boolean> operation = detachServerLevelAsync(capabilities, serverLevel, world)
                    .thenCompose(unused -> drainChunkTasksAsync(world, serverLevel))
                    .thenCompose(unused -> closeServerLevelAsync(world, serverLevel))
                    .thenApply(unused -> WorldIdentity.resolve(WorldIdentity.key(world)).isEmpty());
            return contextualizeUnloadFailure(worldName, operation);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Failed to unload world \"" + worldName + "\" through the selected world lifecycle backend.",
                    unwrap(e)
            ));
        }
    }

    static boolean announceManualWorldUnload(World world) {
        WorldUnloadEvent unloadEvent = new WorldUnloadEvent(world);
        Bukkit.getPluginManager().callEvent(unloadEvent);
        return !unloadEvent.isCancelled();
    }

    private static CompletableFuture<Boolean> unloadWorldViaAsyncApi(CapabilitySnapshot capabilities, World world, boolean save) {
        if (capabilities.unloadWorldAsyncMethod() == null || capabilities.bukkitServer() == null) {
            return null;
        }

        return invokeAsyncUnload(
                capabilities.bukkitServer(),
                capabilities.unloadWorldAsyncMethod(),
                world,
                save
        );
    }

    static CompletableFuture<Boolean> invokeAsyncUnload(
            Object bukkitServer,
            Method unloadWorldAsyncMethod,
            World world,
            boolean save
    ) {
        CompletableFuture<Boolean> callbackFuture = new CompletableFuture<>();
        Consumer<Object> callback = unloaded -> {
            try {
                callbackFuture.complete(asyncUnloadSucceeded(unloaded));
            } catch (Throwable failure) {
                callbackFuture.completeExceptionally(unwrap(failure));
            }
        };
        try {
            unloadWorldAsyncMethod.invoke(bukkitServer, world, save, callback);
        } catch (Throwable e) {
            callbackFuture.completeExceptionally(unwrap(e));
        }
        return callbackFuture;
    }

    private static boolean asyncUnloadSucceeded(Object result) throws ReflectiveOperationException {
        if (result instanceof Boolean unloaded) {
            return unloaded;
        }
        if (result == null) {
            return false;
        }
        Method isSuccessMethod = result.getClass().getMethod("isSuccess");
        return Boolean.TRUE.equals(isSuccessMethod.invoke(result));
    }

    private static CompletableFuture<Boolean> contextualizeUnloadFailure(
            String worldName,
            CompletableFuture<Boolean> operation
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        operation.whenComplete((unloaded, throwable) -> {
            if (throwable == null) {
                result.complete(Boolean.TRUE.equals(unloaded));
            } else {
                result.completeExceptionally(new IllegalStateException(
                        "Failed to unload world \"" + worldName + "\" through the selected world lifecycle backend.",
                        unwrap(throwable)
                ));
            }
        });
        return result;
    }

    private static CompletableFuture<Void> closeServerLevelAsync(World world, Object serverLevel) {
        Method closeMethod;
        try {
            closeMethod = CapabilityResolution.resolveMethod(
                    serverLevel.getClass(),
                    "close",
                    method -> method.getParameterCount() == 0
            );
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(unwrap(e));
        }
        if (closeMethod == null) {
            return CompletableFuture.completedFuture(null);
        }

        Runnable closeTask = () -> {
            try {
                closeMethod.invoke(serverLevel);
            } catch (Throwable e) {
                throw new RuntimeException(unwrap(e));
            }
        };
        return runGlobalAsync(closeTask).orTimeout(90L, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Void> drainChunkTasksAsync(World world, Object serverLevel) {
        Method schedulerMethod;
        try {
            schedulerMethod = CapabilityResolution.resolveMethod(
                    serverLevel.getClass(),
                    "moonrise$getChunkTaskScheduler",
                    method -> method.getParameterCount() == 0
            );
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(unwrap(e));
        }
        if (schedulerMethod == null) {
            return CompletableFuture.completedFuture(null);
        }

        return J.afut(() -> {
            try {
                Object scheduler = schedulerMethod.invoke(serverLevel);
                Method haltMethod = CapabilityResolution.resolveMethod(
                        scheduler.getClass(),
                        "halt",
                        method -> {
                            Class<?>[] parameters = method.getParameterTypes();
                            return parameters.length == 2
                                    && boolean.class.equals(parameters[0])
                                    && long.class.equals(parameters[1]);
                        }
                );
                if (haltMethod == null) {
                    return;
                }
                Object halted = haltMethod.invoke(
                        scheduler,
                        true,
                        TimeUnit.SECONDS.toNanos(90L));
                if (halted instanceof Boolean complete && !complete) {
                    throw new IllegalStateException(
                            "Chunk scheduler drain timed out for world \"" + world.getName() + "\".");
                }
            } catch (Throwable e) {
                throw new RuntimeException(unwrap(e));
            }
        }).orTimeout(90L, TimeUnit.SECONDS);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeWorldFromCraftServerMap(World world) throws ReflectiveOperationException {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            return;
        }

        Field worldsField = CapabilityResolution.resolveField(bukkitServer.getClass(), "worlds");
        Object rawWorlds = worldsField.get(bukkitServer);
        if (rawWorlds instanceof Map map) {
            boolean removed = map.values().removeIf(candidate -> candidate == world);
            if (!removed) {
                throw new IllegalStateException(
                        "CraftServer world registry did not contain \"" + world.getName() + "\".");
            }
        }
    }

    private static CompletableFuture<Void> detachServerLevelAsync(
            CapabilitySnapshot capabilities,
            Object serverLevel,
            World world
    ) {
        Runnable detachTask = () -> {
            try {
                capabilities.removeLevelMethod().invoke(capabilities.minecraftServer(), serverLevel);
                removeWorldFromCraftServerMap(world);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        if (!J.isFolia() || isGlobalTickThread()) {
            try {
                detachTask.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(unwrap(e));
            }
        }

        CompletableFuture<Void> detachFuture = runGlobalAsync(detachTask);
        return detachFuture.orTimeout(15L, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Void> runGlobalAsync(Runnable task) {
        if (!J.isFolia()) {
            return J.sfut(task);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable settlement = () -> {
            try {
                task.run();
                result.complete(null);
            } catch (Throwable e) {
                result.completeExceptionally(unwrap(e));
            }
        };

        try {
            if (!FoliaScheduler.runGlobal(BukkitPlatform.plugin(), settlement)) {
                result.completeExceptionally(new IllegalStateException("Failed to schedule global world lifecycle task."));
            }
        } catch (Throwable e) {
            result.completeExceptionally(unwrap(e));
        }
        return result;
    }

    static boolean isGlobalTickThread() {
        Object server = Bukkit.getServer();
        if (server == null) {
            return false;
        }
        return CapabilityProbe.attempt("server#isGlobalTickThread",
                () -> Boolean.TRUE.equals(server.getClass().getMethod("isGlobalTickThread").invoke(server)),
                Boolean.FALSE);
    }
}
