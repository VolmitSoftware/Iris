package art.arcane.iris;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.agent.Agent;
import art.arcane.iris.platform.bootstrap.SlimJar;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.lifecycle.IrisRuntimeStatics;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.GenerationSessionManager;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.world.runtime.BukkitEnginePlatformHooks;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.ServerShutdownBoundary;
import art.arcane.volmlib.integration.ReloadAware;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisShutdownMaintenanceTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void finisherRetainsResourcesWhileTheAuthoritativeBoundaryIsPending() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ServerShutdownBoundary boundary = mock(ServerShutdownBoundary.class);
            CountDownLatch waiting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Thread> finisher = new AtomicReference<>();
            when(boundary.await(anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(invocation -> {
                finisher.set(Thread.currentThread());
                waiting.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return false;
            });
            setField(fixture.plugin, "serverShutdownBoundary", boundary);
            fixture.quiesce();
            fixture.startFinisher();
            try {
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                fixture.startFinisher();
                verify(fixture.generator, never()).closeAsync();
                verify(fixture.binding, never()).releasePluginClassLoaderClose();
            } finally {
                release.countDown();
                Thread running = finisher.get();
                if (running != null) {
                    running.join(5_000L);
                    assertFalse(running.isAlive());
                }
            }
            verify(boundary, times(1)).await(anyLong(), eq(TimeUnit.SECONDS));
            verify(fixture.generator, never()).closeAsync();
        }
    }

    @Test
    public void missingShutdownBoundaryDoesNotAuthorizeResourceRelease() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Method method = Iris.class.getDeclaredMethod("awaitServerShutdownBoundary");
            method.setAccessible(true);
            assertEquals(false, method.invoke(fixture.plugin));
        }
    }

    @Test
    public void serverStopKeepsFoliaGenerationAdmittedUntilTheServerDrains() throws Exception {
        try (Fixture fixture = new Fixture()) {
            GenerationSessionManager sessions = new GenerationSessionManager();
            try (GenerationSessionLease active = sessions.acquireForEngine(fixture.engine, "active_chunk")) {
                fixture.quiesce();

                assertTrue(WorldMaintenance.isWorldMaintenanceActive(fixture.identity));
                assertFalse(fixture.hooks.shouldRefuseGeneration(fixture.engine));
                try (GenerationSessionLease queued = sessions.acquireForEngine(fixture.engine, "queued_chunk")) {
                    assertEquals(active.sessionId(), queued.sessionId());
                    assertEquals(2, sessions.activeLeases());
                }
                verify(fixture.generator).quiesceForServerShutdown();
                verify(fixture.generator, never()).closeAsync();
                verify(fixture.binding, never()).releasePluginClassLoaderClose();
            }
            assertEquals(0, sessions.activeLeases());
            fixture.drain();
            verify(fixture.generator).closeAsync();
        }
    }

    @Test
    public void serverStopDoesNotOverrideExistingBlockingMaintenance() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldMaintenance.beginWorldMaintenance(fixture.identity, "world_unload", true);
            try {
                fixture.quiesce();
                assertTrue(fixture.hooks.shouldRefuseGeneration(fixture.engine));
            } finally {
                WorldMaintenance.endWorldMaintenance(fixture.identity, "world_unload", true);
            }
            assertFalse(fixture.hooks.shouldRefuseGeneration(fixture.engine));
        }
    }

    @Test
    public void manualDisableStillBlocksGenerationBeforeClosingTheGenerator() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.generator.closeAsync()).thenAnswer(invocation -> {
                assertTrue(fixture.hooks.shouldRefuseGeneration(fixture.engine));
                return CompletableFuture.completedFuture(null);
            });
            try {
                fixture.drain();
                assertTrue(fixture.hooks.shouldRefuseGeneration(fixture.engine));
                verify(fixture.generator).closeAsync();
                verify(fixture.generator, never()).quiesceForServerShutdown();
            } finally {
                WorldMaintenance.endWorldMaintenance(fixture.identity, "manual_disable", true);
            }
        }
    }

    @Test
    public void startupEnsuresAgentReadinessBeforeLifecycleProtectionWithoutWorldInjection() throws Exception {
        try (Fixture fixture = new Fixture(); MockedStatic<Agent> agent = mockStatic(Agent.class)) {
            when(fixture.binding.supportsIrisWorldGeneration()).thenReturn(true);
            agent.when(Agent::install).thenReturn(true);
            doAnswer(invocation -> {
                agent.verify(Agent::install);
                assertSame(fixture.plugin.getClass().getClassLoader(), invocation.getArgument(0));
                return null;
            }).when(fixture.binding).initializeLifecycle(fixture.plugin.getClass().getClassLoader());
            invoke(fixture.plugin, "initializeShutdownProtection");
            verify(fixture.binding).initializeLifecycle(fixture.plugin.getClass().getClassLoader());
            verify(fixture.binding, never()).injectBukkit();
        }
    }

    @Test
    public void unavailableAgentStopsLifecycleInitialization() throws Exception {
        try (Fixture fixture = new Fixture(); MockedStatic<Agent> agent = mockStatic(Agent.class)) {
            when(fixture.binding.supportsIrisWorldGeneration()).thenReturn(true);
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> invoke(fixture.plugin, "initializeShutdownProtection"));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            verify(fixture.binding, never()).initializeLifecycle(fixture.plugin.getClass().getClassLoader());
        }
    }

    @Test
    public void worldHookTeardownFailureReleasesProtectionAfterServicesAndWorkersStop() throws Exception {
        try (Fixture fixture = new Fixture();
             MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<IrisRuntimeStatics> statics = mockStatic(IrisRuntimeStatics.class);
             MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             MockedStatic<BukkitPlatform> bukkitPlatform = mockStatic(BukkitPlatform.class);
             MockedStatic<SlimJar> providers = mockStatic(SlimJar.class)) {
            IrisService service = mock(IrisService.class);
            IllegalStateException failure = new IllegalStateException("world hook teardown failed");
            fixture.nms.when(INMS::isBound).thenReturn(true);
            doThrow(failure).when(fixture.binding).uninjectBukkit();
            setField(fixture.plugin, "services", new KMap<Class<? extends IrisService>, IrisService>());
            setField(fixture.plugin, "enabledServices", List.of(service));
            doAnswer(invocation -> {
                platforms.verify(IrisPlatforms::unbind);
                bukkitPlatform.verify(BukkitPlatform::releaseHost);
                assertTrue(MultiBurst.burst.isTerminated());
                assertTrue(MultiBurst.hydrology.isTerminated());
                assertTrue(MultiBurst.ioBurst.isTerminated());
                return null;
            }).when(fixture.binding).releasePluginClassLoaderClose();
            fixture.quiesce();
            try {
                fixture.finish(5L);
                fixture.finish(5L);
                verify(service).onDisable();
                iris.verify(() -> Iris.reportError("Failed to remove Iris world-generation hooks.", failure));
                assertTrue(MultiBurst.burst.isTerminated());
                assertTrue(MultiBurst.hydrology.isTerminated());
                assertTrue(MultiBurst.ioBurst.isTerminated());
                verify(fixture.binding).releasePluginClassLoaderClose();
                platforms.verify(IrisPlatforms::unbind);
                bukkitPlatform.verify(BukkitPlatform::releaseHost);
            } finally {
                MultiBurst.burst.reopen();
                MultiBurst.hydrology.reopen();
                MultiBurst.ioBurst.reopen();
            }
        }
    }

    @Test
    public void failedServiceWithAnOwnedExecutorRetainsTheClassLoader() throws Exception {
        ExecutorService ownedExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ownedExecutor.submit(() -> {
            started.countDown();
            finish.await();
            return null;
        });
        try (Fixture fixture = new Fixture();
             MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<IrisRuntimeStatics> statics = mockStatic(IrisRuntimeStatics.class);
             MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             MockedStatic<BukkitPlatform> bukkitPlatform = mockStatic(BukkitPlatform.class)) {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            IrisService service = mock(IrisService.class);
            IllegalStateException failure = new IllegalStateException("service teardown failed");
            doThrow(failure).when(service).onDisable();
            setField(fixture.plugin, "services", new KMap<Class<? extends IrisService>, IrisService>());
            setField(fixture.plugin, "enabledServices", List.of(service));
            fixture.quiesce();
            fixture.finish(5L);
            verify(service).onDisable();
            iris.verify(() -> Iris.reportError("Failed to disable " + service.getClass().getSimpleName() + ".", failure));
            assertFalse(ownedExecutor.isTerminated());
            verify(fixture.binding, never()).releasePluginClassLoaderClose();
        } finally {
            finish.countDown();
            ownedExecutor.shutdownNow();
            assertTrue(ownedExecutor.awaitTermination(5, TimeUnit.SECONDS));
            MultiBurst.burst.reopen();
            MultiBurst.hydrology.reopen();
            MultiBurst.ioBurst.reopen();
        }
    }

    @Test
    public void failedPostShutdownCallbackRetainsTheClassLoaderForSkippedCleanup() throws Exception {
        ExecutorService ownedExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ownedExecutor.submit(() -> {
            started.countDown();
            finish.await();
            return null;
        });
        try (Fixture fixture = new Fixture();
             MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<IrisRuntimeStatics> statics = mockStatic(IrisRuntimeStatics.class);
             MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             MockedStatic<BukkitPlatform> bukkitPlatform = mockStatic(BukkitPlatform.class)) {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            IllegalStateException failure = new IllegalStateException("post-shutdown callback failed");
            AtomicBoolean cleanupAttempted = new AtomicBoolean();
            KList<Runnable> callbacks = new KList<>();
            callbacks.add(() -> { throw failure; });
            callbacks.add(() -> {
                cleanupAttempted.set(true);
                ownedExecutor.shutdown();
            });
            Field callbacksField = VolmitPlugin.class.getDeclaredField("postShutdown");
            callbacksField.setAccessible(true);
            callbacksField.set(fixture.plugin, callbacks);
            Method runCallbacks = VolmitPlugin.class.getDeclaredMethod("runPostShutdown");
            runCallbacks.setAccessible(true);
            runCallbacks.invoke(doCallRealMethod().when(fixture.plugin));
            fixture.quiesce();
            fixture.finish(5L);
            iris.verify(() -> Iris.reportError("Failed to run Iris post-shutdown cleanup.", failure));
            assertFalse(cleanupAttempted.get());
            assertFalse(ownedExecutor.isShutdown());
            assertFalse(ownedExecutor.isTerminated());
            verify(fixture.binding, never()).releasePluginClassLoaderClose();
        } finally {
            finish.countDown();
            ownedExecutor.shutdownNow();
            assertTrue(ownedExecutor.awaitTermination(5, TimeUnit.SECONDS));
            MultiBurst.burst.reopen();
            MultiBurst.hydrology.reopen();
            MultiBurst.ioBurst.reopen();
        }
    }

    @Test
    public void incompleteDrainRetainsServicesWorkersAndClassLoader() throws Exception {
        try (Fixture fixture = new Fixture(); MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            IrisService service = mock(IrisService.class);
            setField(fixture.plugin, "services", new KMap<Class<? extends IrisService>, IrisService>());
            setField(fixture.plugin, "enabledServices", List.of(service));
            when(fixture.generator.closeAsync()).thenReturn(new CompletableFuture<>());
            fixture.quiesce();
            fixture.finish(0L);
            verify(service, never()).onDisable();
            verify(fixture.binding, never()).releasePluginClassLoaderClose();
            verify(fixture.binding, never()).uninjectBukkit();
        }
    }

    @Test
    public void serverStopPreUnloadDefersTeardownWhileTheClassLoaderIsHeld() throws Exception {
        try (Fixture fixture = new Fixture()) {
            setField(fixture.plugin, "postStopFinisherStarted", new AtomicBoolean(true));
            try {
                fixture.commitReloadDuringServerStop();
                verify(fixture.binding).deferPluginClassLoaderClose();
                verify(fixture.generator).quiesceForServerShutdown();
                verify(fixture.generator, never()).closeAsync();
                assertTrue(fixture.serverStopTeardownDeferred());
            } finally {
                WorldMaintenance.endWorldMaintenance(fixture.identity, "pre-unload:HOT_UNLOAD");
            }
        }
    }

    @Test
    public void serverStopPreUnloadDrainsInlineWhenTheClassLoaderCannotBeHeld() throws Exception {
        try (Fixture fixture = new Fixture(); MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            IllegalStateException failure = new IllegalStateException("class loader hold failed");
            doThrow(failure).when(fixture.binding).deferPluginClassLoaderClose();
            try {
                fixture.commitReloadDuringServerStop();
                verify(fixture.generator).closeAsync();
                verify(fixture.generator, never()).quiesceForServerShutdown();
                assertFalse(fixture.serverStopTeardownDeferred());
                iris.verify(() -> Iris.reportError("Failed to retain the Iris plugin class loader; draining shutdown inline.", failure));
            } finally {
                WorldMaintenance.endWorldMaintenance(fixture.identity, "pre-unload:HOT_UNLOAD", true);
            }
        }
    }

    @Test
    public void serverStopPreUnloadDrainsInlineWithoutInitializedProtection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            setField(fixture.plugin, "shutdownProtectionInitialized", false);
            try {
                fixture.commitReloadDuringServerStop();
                verify(fixture.binding, never()).deferPluginClassLoaderClose();
                verify(fixture.generator).closeAsync();
                verify(fixture.generator, never()).quiesceForServerShutdown();
                assertFalse(fixture.serverStopTeardownDeferred());
            } finally {
                WorldMaintenance.endWorldMaintenance(fixture.identity, "pre-unload:HOT_UNLOAD", true);
            }
        }
    }

    private static void invoke(Iris plugin, String name) throws ReflectiveOperationException {
        Method method = Iris.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void setField(Iris plugin, String name, Object value) throws ReflectiveOperationException {
        Field field = Iris.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final Iris plugin = mock(Iris.class);
        private final World world = mock(World.class);
        private final IrisWorld irisWorld = mock(IrisWorld.class);
        private final Engine engine = mock(Engine.class);
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        private final BukkitEnginePlatformHooks hooks = new BukkitEnginePlatformHooks();
        private final INMSBinding binding = mock(INMSBinding.class);
        private final String identity = "iris:shutdown_test";
        private final IrisSettings previousSettings = IrisSettings.settings;
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class, CALLS_REAL_METHODS);
        private final MockedStatic<BukkitWorldBinding> worlds = mockStatic(BukkitWorldBinding.class);
        private final MockedStatic<IrisServices> services = mockStatic(IrisServices.class);
        private final MockedStatic<INMS> nms = mockStatic(INMS.class);
        private final MockedStatic<J> tasks = mockStatic(J.class, CALLS_REAL_METHODS);
        private final MockedStatic<PregeneratorJob> pregenerator = mockStatic(PregeneratorJob.class);
        private int quiesces;

        private Fixture() throws ReflectiveOperationException {
            IrisSettings.settings = new IrisSettings();
            setField(plugin, "shutdownProtectionInitialized", true);
            setField(plugin, "alreadyDrained", new AtomicBoolean());
            setField(plugin, "servicesDisabled", new AtomicBoolean());
            setField(plugin, "runtimeTeardownFailed", new AtomicBoolean());
            setField(plugin, "sharedRuntimeClosed", new AtomicBoolean());
            setField(plugin, "terminalCleanupCompleted", new AtomicBoolean());
            setField(plugin, "serverStopTeardownDeferred", new AtomicBoolean());
            setField(plugin, "postStopFinisherStarted", new AtomicBoolean());
            setField(plugin, "deferredShutdownGenerators", new CopyOnWriteArrayList<PlatformChunkGenerator>());
            when(world.getKey()).thenReturn(NamespacedKey.fromString(identity));
            when(engine.getWorld()).thenReturn(irisWorld);
            when(irisWorld.hasPlatformWorld()).thenReturn(true);
            when(irisWorld.identity()).thenReturn(identity);
            when(generator.closeAsync()).thenReturn(CompletableFuture.completedFuture(null));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            worlds.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
            nms.when(INMS::get).thenReturn(binding);
            tasks.when(J::isFolia).thenReturn(true);
        }

        private void quiesce() throws ReflectiveOperationException {
            Method method = Iris.class.getDeclaredMethod("quiesceRuntimeForServerShutdown", String.class);
            method.setAccessible(true);
            method.invoke(plugin, "server_stop");
            quiesces++;
        }

        private void drain() throws ReflectiveOperationException {
            Method method = Iris.class.getDeclaredMethod("drainWorldGenerators", String.class, long.class);
            method.setAccessible(true);
            method.invoke(plugin, "manual_disable", 5L);
        }

        private void commitReloadDuringServerStop() throws ReflectiveOperationException {
            setField(plugin, "alreadyDrained", new AtomicBoolean());
            toolbelt.when(IrisToolbelt::isServerStopping).thenReturn(true);
            doCallRealMethod().when(plugin).commitReload(any());
            plugin.commitReload(ReloadAware.PreUnloadReason.HOT_UNLOAD);
        }

        private boolean serverStopTeardownDeferred() throws ReflectiveOperationException {
            Field field = Iris.class.getDeclaredField("serverStopTeardownDeferred");
            field.setAccessible(true);
            return ((AtomicBoolean) field.get(plugin)).get();
        }


        private void finish(long timeoutSeconds) throws ReflectiveOperationException {
            Method method = Iris.class.getDeclaredMethod("finishDeferredRuntimeTeardown", String.class, long.class);
            method.setAccessible(true);
            method.invoke(plugin, "test_shutdown", timeoutSeconds);
        }

        private void startFinisher() throws ReflectiveOperationException {
            Method method = Iris.class.getDeclaredMethod("startPostStopFinisher");
            method.setAccessible(true);
            method.invoke(plugin);
        }

        @Override
        public void close() {
            for (int index = 0; index < quiesces; index++) {
                WorldMaintenance.endWorldMaintenance(identity, "server_stop");
            }
            pregenerator.close();
            tasks.close();
            nms.close();
            services.close();
            worlds.close();
            toolbelt.close();
            bukkit.close();
            IrisSettings.settings = previousSettings;
        }
    }
}
