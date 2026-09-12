package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.platform.bukkit.nms.ServerShutdownBoundary;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.agent.Agent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

final class NmsWorldLifecycle {
    private final AtomicBoolean injected = new AtomicBoolean();
    private volatile ResettableClassFileTransformer levelStorageAccessTransformer;
    private volatile ResettableClassFileTransformer serverLevelTransformer;
    private volatile ResettableClassFileTransformer pluginClassLoaderTransformer;
    private boolean pluginClassLoaderCloseDeferred;

    public boolean injectBukkit() {
        synchronized (injected) {
            if (injected.get()) {
                return true;
            }
            try {
                IrisLogging.info("Injecting Bukkit");
                Agent.requireClassLoaderCloseDeferral();
                Class<?> loaderCloseType = getClass().getClassLoader().getClass()
                        .getMethod("close").getDeclaringClass();
                PluginClassLoaderInjectionListener loaderListener = new PluginClassLoaderInjectionListener();
                pluginClassLoaderTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .with(AgentBuilder.RedefinitionStrategy.Listener.ErrorEscalating.FAIL_FAST)
                        .with(loaderListener)
                        .type(ElementMatchers.is(loaderCloseType))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.to(PluginClassLoaderCloseAdvice.class)
                                        .on(ElementMatchers.named("close")
                                                .and(ElementMatchers.takesArguments(0))
                                                .and(ElementMatchers.returns(void.class)))))
                        .installOn(Agent.getInstrumentation());
                loaderListener.requireInstalled();
                levelStorageAccessTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .type(ElementMatchers.is(LevelStorageSource.LevelStorageAccess.class))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.to(LevelStorageAccessAdvice.class).on(ElementMatchers.isConstructor()
                                        .and(ElementMatchers.takesArguments(4))
                                        .and(ElementMatchers.takesArgument(0, LevelStorageSource.class))
                                        .and(ElementMatchers.takesArgument(1, String.class))
                                        .and(ElementMatchers.takesArgument(2, Path.class))
                                        .and(ElementMatchers.takesArgument(3, ResourceKey.class)))))
                        .installOn(Agent.getInstrumentation());
                serverLevelTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .type(ElementMatchers.is(ServerLevel.class))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.to(ServerLevelAdvice.class).on(ElementMatchers.isConstructor()
                                        .and(ElementMatchers.takesArgument(0, MinecraftServer.class))
                                        .and(ElementMatchers.takesArgument(5, LevelStem.class)))))
                        .installOn(Agent.getInstrumentation());
                NmsGenerationHooks.install();

                injected.set(true);
                return true;
            } catch (Throwable e) {
                IrisLogging.reportError(C.RED + "Failed to inject Bukkit", e);
                ResettableClassFileTransformer partialServerLevel = serverLevelTransformer;
                ResettableClassFileTransformer partialStorageAccess = levelStorageAccessTransformer;
                ResettableClassFileTransformer partialPluginClassLoader = pluginClassLoaderTransformer;
                serverLevelTransformer = null;
                levelStorageAccessTransformer = null;
                pluginClassLoaderTransformer = null;
                for (ResettableClassFileTransformer partial : new ResettableClassFileTransformer[]{
                        partialServerLevel,
                        partialStorageAccess,
                        partialPluginClassLoader
                }) {
                    if (partial == null) {
                        continue;
                    }
                    try {
                        partial.reset(Agent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                    } catch (Throwable cleanupFailure) {
                        IrisLogging.reportError("Failed to remove partial Bukkit lifecycle injection", cleanupFailure);
                    }
                }
                return false;
            }
        }
    }

    public void ensureServerLevelInjection() {
        if (!injected.get()) {
            throw new IllegalStateException("Iris world lifecycle injection is unavailable. Fix the Java Agent or Code Injection startup failure and restart before creating worlds.");
        }
        try {
            Agent.getInstrumentation().retransformClasses(
                    LevelStorageSource.LevelStorageAccess.class,
                    ServerLevel.class
            );
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to re-apply Bukkit world lifecycle injection. World creation was stopped before loading the world.", e);
        }
    }

    public void uninjectBukkit() {
        synchronized (injected) {
            ResettableClassFileTransformer activeServerLevel = serverLevelTransformer;
            ResettableClassFileTransformer activeStorageAccess = levelStorageAccessTransformer;
            serverLevelTransformer = null;
            levelStorageAccessTransformer = null;
            injected.set(false);
            for (ResettableClassFileTransformer transformer : new ResettableClassFileTransformer[]{
                    activeServerLevel,
                    activeStorageAccess
            }) {
                if (transformer == null) {
                    continue;
                }
                try {
                    transformer.reset(Agent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                } catch (Throwable e) {
                    IrisLogging.reportError(C.RED + "Failed to remove Bukkit world lifecycle injection", e);
                }
            }
        }
    }

    public boolean isServerStopping() {
        return ((CraftServer) Bukkit.getServer()).getServer().hasStopped();
    }

    public ServerShutdownBoundary createServerShutdownBoundary() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return new ServerShutdownBoundary(
                () -> server.hasFullyShutdown,
                server.getRunningThread()
        );
    }

    public void deferPluginClassLoaderClose() {
        synchronized (injected) {
            if (pluginClassLoaderTransformer == null || pluginClassLoaderCloseDeferred) {
                return;
            }
            Agent.retainClassLoader(getClass().getClassLoader());
            pluginClassLoaderCloseDeferred = true;
        }
    }

    public void releasePluginClassLoaderClose() {
        ResettableClassFileTransformer transformer;
        boolean releaseLoader;
        synchronized (injected) {
            transformer = pluginClassLoaderTransformer;
            pluginClassLoaderTransformer = null;
            releaseLoader = pluginClassLoaderCloseDeferred;
            pluginClassLoaderCloseDeferred = false;
        }
        try {
            if (transformer != null
                    && !transformer.reset(Agent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)) {
                throw new IllegalStateException("Failed to remove Iris plugin class loader lifecycle injection");
            }
        } finally {
            if (releaseLoader) {
                Agent.releaseClassLoader(getClass().getClassLoader());
            }
        }
    }

    private static final class PluginClassLoaderInjectionListener extends AgentBuilder.Listener.Adapter {
        private volatile boolean transformed;
        private volatile Throwable failure;

        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                     boolean loaded, DynamicType dynamicType) {
            transformed = true;
        }

        @Override
        public void onError(String typeName, ClassLoader loader, JavaModule module,
                            boolean loaded, Throwable throwable) {
            failure = throwable;
        }

        private void requireInstalled() {
            if (!transformed || failure != null) {
                throw new IllegalStateException("Iris plugin class loader lifecycle injection failed", failure);
            }
        }
    }

    private static class PluginClassLoaderCloseAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean enter(@Advice.This ClassLoader loader) throws ReflectiveOperationException {
            Class<?> installer = Class.forName(
                    "art.arcane.iris.platform.agent.Installer", true, ClassLoader.getSystemClassLoader());
            return Boolean.TRUE.equals(installer.getMethod("deferClassLoaderClose", ClassLoader.class)
                    .invoke(null, loader));
        }
    }

    private static class LevelStorageAccessAdvice {
        @Advice.OnMethodEnter
        static void enter(
                @Advice.Argument(1) String levelId,
                @Advice.Argument(value = 3, readOnly = false) ResourceKey<LevelStem> dimensionType
        ) {
            if (levelId == null || levelId.isBlank()) {
                return;
            }

            ClassLoader pluginClassLoader;
            Class<?> generatorType;
            Class<?> stagingType;
            try {
                Plugin irisPlugin = Bukkit.getPluginManager().getPlugin("Iris");
                if (irisPlugin == null) {
                    return;
                }
                pluginClassLoader = irisPlugin.getClass().getClassLoader();
                generatorType = Class.forName("art.arcane.iris.platform.generation.PlatformChunkGenerator", true, pluginClassLoader);
                stagingType = Class.forName("art.arcane.iris.world.lifecycle.WorldLifecycleStaging", true, pluginClassLoader);
            } catch (Throwable ignored) {
                return;
            }

            Object generator;
            try {
                generator = stagingType
                        .getDeclaredMethod("peekStemGenerator", String.class)
                        .invoke(null, levelId);
            } catch (Throwable e) {
                throw new RuntimeException("Iris failed to inspect the staged world generator",
                        e instanceof InvocationTargetException ex ? ex.getCause() : e);
            }
            if (generator == null || !generatorType.isInstance(generator)) {
                return;
            }

            try {
                Object target = generatorType.getMethod("getTarget").invoke(generator);
                if (target == null) {
                    throw new IllegalStateException("Iris generator has no engine target.");
                }
                Object world = target.getClass().getMethod("getWorld").invoke(target);
                if (world == null) {
                    throw new IllegalStateException("Iris generator target has no world identity.");
                }
                Object rawIdentity = world.getClass().getMethod("identity").invoke(world);
                String worldIdentity = rawIdentity == null ? "" : rawIdentity.toString().trim();
                Identifier worldIdentifier = Identifier.parse(worldIdentity);
                if (!"iris".equals(worldIdentifier.getNamespace())
                        && !"minecraft".equals(worldIdentifier.getNamespace())) {
                    throw new IllegalStateException(
                            "Iris generator has an unmanaged world identity: " + worldIdentity);
                }
                dimensionType = ResourceKey.create(Registries.LEVEL_STEM, worldIdentifier);
            } catch (Throwable e) {
                throw new RuntimeException("Iris failed to bind the staged world storage identity",
                        e instanceof InvocationTargetException ex ? ex.getCause() : e);
            }
        }
    }

    private static class ServerLevelAdvice {
        @Advice.OnMethodEnter
        static void enter(
                @Advice.Argument(0) MinecraftServer server,
                @Advice.Argument(value = 4, readOnly = false) ResourceKey<Level> dimensionKey,
                @Advice.Argument(value = 5, readOnly = false) LevelStem levelStem,
                @Advice.AllArguments Object[] constructorArguments
        ) {
            if (dimensionKey == null)
                return;

            // This advice is inlined into every ServerLevel construction on the server. Until a
            // world is proven Iris-owned, every failure must fail OPEN (keep the vanilla stem):
            // Iris being unloaded or half-loaded must never break other plugins' world creation.
            String levelId;
            ClassLoader pluginClassLoader;
            Class<?> generatorType;
            Class<?> stagingType;
            try {
                levelId = dimensionKey.identifier().getPath();
                if (levelId == null || levelId.isBlank()) {
                    return;
                }

                Plugin irisPlugin = Bukkit.getPluginManager().getPlugin("Iris");
                if (irisPlugin == null) {
                    return;
                }
                pluginClassLoader = irisPlugin.getClass().getClassLoader();
                generatorType = Class.forName("art.arcane.iris.platform.generation.PlatformChunkGenerator", true, pluginClassLoader);
                stagingType = Class.forName("art.arcane.iris.world.lifecycle.WorldLifecycleStaging", true, pluginClassLoader);
            } catch (Throwable ignored) {
                // Iris absent or half-loaded: fail OPEN for a world we cannot prove ours.
                return;
            }

            // From here Iris is present and its classes resolve; a failure resolving the
            // staged generator must fail LOUD — silently handing a possibly-staged Iris world
            // the vanilla stem corrupts generation.
            ChunkGenerator gen = null;
            try {
                ChunkGenerator constructorGenerator = null;
                for (Object argument : constructorArguments) {
                    if (argument instanceof ChunkGenerator candidate) {
                        constructorGenerator = candidate;
                        break;
                    }
                }
                Object generator = generatorType.isInstance(constructorGenerator) ? constructorGenerator : null;
                if (generator == null) {
                    generator = stagingType
                            .getDeclaredMethod("consumeStemGenerator", String.class)
                            .invoke(null, levelId);
                }
                if (generator instanceof ChunkGenerator owned && owned.getClass().getPackageName().startsWith("art.arcane.iris")) {
                    gen = owned;
                }
            } catch (Throwable e) {
                throw new RuntimeException("Iris failed to resolve the staged world generator",
                        e instanceof InvocationTargetException ex ? ex.getCause() : e);
            }
            if (gen == null) {
                return;
            }

            // Past the ownership gate the world is Iris-owned; silently handing back the
            // vanilla stem would corrupt generation, so failures from here rethrow.
            try {
                Object bindings = Class.forName("art.arcane.iris.platform.bukkit.nms.INMS", true, pluginClassLoader)
                        .getDeclaredMethod("get")
                        .invoke(null);
                if (bindings == null) {
                    throw new IllegalStateException("Iris failed to resolve an INMSBinding instance.");
                }

                Method stemMethod = null;
                for (Method candidate : bindings.getClass().getMethods()) {
                    if (candidate.getName().equals("createRuntimeLevelStem") && candidate.getParameterCount() == 2) {
                        stemMethod = candidate;
                        break;
                    }
                }
                if (stemMethod == null) {
                    throw new IllegalStateException("Iris binding is missing createRuntimeLevelStem.");
                }
                Object target = generatorType.getMethod("getTarget").invoke(gen);
                if (target == null) {
                    throw new IllegalStateException("Iris generator has no engine target.");
                }
                Object world = target.getClass().getMethod("getWorld").invoke(target);
                if (world == null) {
                    throw new IllegalStateException("Iris generator target has no world identity.");
                }
                Object rawIdentity = world.getClass().getMethod("identity").invoke(world);
                String worldIdentity = rawIdentity == null ? "" : rawIdentity.toString().trim();
                Identifier worldIdentifier = Identifier.parse(worldIdentity);
                if (!"iris".equals(worldIdentifier.getNamespace())
                        && !"minecraft".equals(worldIdentifier.getNamespace())) {
                    throw new IllegalStateException(
                            "Iris generator has an unmanaged world identity: " + worldIdentity);
                }
                Object resolvedStem = stemMethod.invoke(bindings, server.registryAccess(), gen);
                if (!(resolvedStem instanceof LevelStem runtimeStem)) {
                    throw new IllegalStateException("Iris runtime LevelStem binding returned " + (resolvedStem == null ? "null" : resolvedStem.getClass().getName()) + ".");
                }
                dimensionKey = ResourceKey.create(Registries.DIMENSION, worldIdentifier);
                levelStem = runtimeStem;
            } catch (Throwable e) {
                throw new RuntimeException("Iris failed to replace the levelStem", e instanceof InvocationTargetException ex ? ex.getCause() : e);
            }
        }
    }
}
