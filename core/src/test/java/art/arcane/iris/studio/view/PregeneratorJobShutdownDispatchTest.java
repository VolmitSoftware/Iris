package art.arcane.iris.studio.view;

import art.arcane.iris.world.pregen.IrisPregenerator;
import art.arcane.iris.world.pregen.PregenApiPhase;
import art.arcane.iris.world.pregen.PregenApiSink;
import art.arcane.iris.world.pregen.PregenPhaseTracker;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.format.MemoryMonitor;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PregeneratorJobShutdownDispatchTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void pluginDisableCanJoinAWorkerAlreadyDispatchingSaving() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch stop = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    fixture.withBukkit(() -> {
                        fixture.job.onSaving();
                        try {
                            stop.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        fixture.job.onClose();
                    });
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            fixture.set("worker", worker);
            doAnswer(invocation -> {
                stop.countDown();
                return null;
            }).when(fixture.pregenerator).close();
            AtomicReference<PregeneratorJob> instance = instance();
            PregeneratorJob previous = instance.getAndSet(fixture.job);
            try {
                synchronized (fixture.manager) {
                    worker.start();
                    assertTrue(fixture.dispatchEntered.await(5, TimeUnit.SECONDS));
                    fixture.enabled.set(false);
                    assertTrue(PregeneratorJob.shutdownAndWait(500));
                }
                assertFalse(worker.isAlive());
                assertNull(failure.get());
                verify(fixture.pregenerator).close();
                verify(fixture.monitor).close();
                verify(fixture.service).shutdownNow();
                verify(fixture.manager, never()).isPluginEnabled(fixture.plugin);
            } finally {
                stop.countDown();
                worker.interrupt();
                worker.join(5_000);
                assertFalse(worker.isAlive());
                instance.set(previous);
            }
        }
    }

    @Test
    public void enabledJobsKeepScheduledSavingAndTerminalEvents() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.withBukkit(() -> {
                fixture.job.onSaving();
                fixture.job.onClose();
            });
            assertTrue(fixture.delivered.isEmpty());
            assertEquals(2, fixture.queued.size());
            fixture.queued.forEach(Runnable::run);
            assertEquals(List.of(PregenApiPhase.SAVING, PregenApiPhase.CANCELLED), fixture.delivered);
            verify(fixture.monitor).close();
            verify(fixture.service).shutdownNow();
        }
    }

    @Test
    public void disableAfterEnabledCheckReportsRejectionAndStillCleansUp() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalPluginAccessException rejected = new IllegalPluginAccessException("Plugin disabled during scheduling");
            when(fixture.scheduler.scheduleSyncDelayedTask(eq(fixture.plugin), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        fixture.enabled.set(false);
                        throw rejected;
                    });
            fixture.withBukkit(() -> {
                fixture.job.onSaving();
                fixture.job.onClose();
            });
            verify(fixture.platform).reportError(contains("SAVING"), eq(rejected));
            verify(fixture.monitor).close();
            verify(fixture.service).shutdownNow();
            assertTrue(fixture.queued.isEmpty());
        }
    }

    @Test
    public void unexpectedSchedulingFailureRetainsItsFullCause() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("Scheduler unavailable");
            when(fixture.scheduler.scheduleSyncDelayedTask(eq(fixture.plugin), any(Runnable.class))).thenThrow(failure);
            fixture.withBukkit(fixture.job::onSaving);
            verify(fixture.platform).reportError(contains("SAVING"), eq(failure));
        }
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<PregeneratorJob> instance() throws Exception {
        Field field = PregeneratorJob.class.getDeclaredField("instance");
        field.setAccessible(true);
        return (AtomicReference<PregeneratorJob>) field.get(null);
    }

    private static final class Fixture implements AutoCloseable {
        private final PregeneratorJob job = mock(PregeneratorJob.class, CALLS_REAL_METHODS);
        private final IrisPregenerator pregenerator = mock(IrisPregenerator.class);
        private final MemoryMonitor monitor = mock(MemoryMonitor.class);
        private final ExecutorService service = mock(ExecutorService.class);
        private final IrisPlatform platform = mock(IrisPlatform.class);
        private final Plugin plugin = mock(Plugin.class);
        private final PluginManager manager = mock(PluginManager.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final Server server = mock(Server.class);
        private final AtomicBoolean enabled = new AtomicBoolean(true);
        private final CountDownLatch dispatchEntered = new CountDownLatch(1);
        private final List<Runnable> queued = new ArrayList<>();
        private final List<PregenApiPhase> delivered = new ArrayList<>();
        private final PregenApiSink previousSink = IrisServices.getOrNull(PregenApiSink.class);

        private Fixture() throws Exception {
            when(platform.platformName()).thenReturn("bukkit");
            when(plugin.isEnabled()).thenAnswer(invocation -> enabled.get());
            when(manager.isPluginEnabled(plugin)).thenAnswer(invocation -> {
                synchronized (manager) {
                    return plugin.isEnabled();
                }
            });
            when(scheduler.scheduleSyncDelayedTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                queued.add(invocation.getArgument(1));
                return queued.size();
            });
            IrisPlatforms.bind(platform);
            BukkitPlatform.hostPlugin(plugin);
            PregenApiSink sink = (phase, progress) -> {
                dispatchEntered.countDown();
                J.s(() -> delivered.add(phase));
            };
            IrisServices.register(PregenApiSink.class, sink);
            set("bounds", new PregenRenderSnapshot.Bounds(0, 0, 0, 0));
            set("pregenerator", pregenerator);
            set("monitor", monitor);
            set("service", service);
            set("saving", new AtomicBoolean());
            set("stopRequested", new AtomicBoolean());
            set("apiPhases", new PregenPhaseTracker());
            set("whenDone", List.of());
        }

        @Override
        public void close() {
            if (previousSink == null) {
                IrisServices.remove(PregenApiSink.class);
            } else {
                IrisServices.register(PregenApiSink.class, previousSink);
            }
            BukkitPlatform.hostPlugin(null);
            IrisPlatforms.unbind();
        }

        private void withBukkit(Runnable action) {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                 MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
                bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkit.when(Bukkit::getServer).thenReturn(server);
                action.run();
            }
        }

        private void set(String name, Object value) throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(job, value);
        }
    }
}
