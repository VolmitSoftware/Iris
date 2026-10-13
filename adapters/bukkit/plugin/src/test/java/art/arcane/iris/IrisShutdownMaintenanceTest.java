package art.arcane.iris;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
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
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
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
                fixture.nms.verify(INMS::get);
                verify(fixture.binding).deferPluginClassLoaderClose();
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
