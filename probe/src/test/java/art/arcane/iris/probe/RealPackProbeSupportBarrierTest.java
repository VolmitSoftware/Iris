package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.runtime.EngineEffectsProvider;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineWorldManagerProvider;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.IrisRuntimeState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.task.J;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class RealPackProbeSupportBarrierTest {
    @AfterClass
    public static void resetRuntime() {
        IrisRuntimeState.reset();
    }

    @Test
    public void closesOwnedRuntimeServicesAndPreservesUnrelatedServices() throws Exception {
        URL resource = RealPackProbeSupportBarrierTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        IrisServices.register(String.class, "unrelated");
        try {
            try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                    new File(resource.toURI()), "cohesion", "[workspace-cleanup]")) {
                assertNotNull(IrisServices.getOrNull(EngineEffectsProvider.class));
            }
            assertFalse(IrisPlatforms.isBound());
            assertNull(IrisServices.getOrNull(PreservationRegistry.class));
            assertNull(IrisServices.getOrNull(EngineWorldManagerProvider.class));
            assertNull(IrisServices.getOrNull(EngineEffectsProvider.class));
            assertNull(IrisServices.getOrNull(EnginePlatformHooks.class));
            assertEquals("unrelated", IrisServices.get(String.class));
        } finally {
            IrisServices.remove(String.class);
        }
    }

    @Test(timeout = 30_000L)
    public void waitsForCrossPoolContinuationsAndCollectsTheirErrors() throws Exception {
        URL resource = RealPackProbeSupportBarrierTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                new File(resource.toURI()), "cohesion", "[worker-barrier]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337L, false, "barrier")) {
            for (int repeat = 0; repeat < 40; repeat++) {
                verifyCrossPoolContinuation(session, MultiBurst.ioBurst, MultiBurst.hydrology);
                verifyCrossPoolContinuation(session, MultiBurst.hydrology, MultiBurst.ioBurst);
            }
        }
        PlatformLeakGuard.requireClean("closed real-pack workspace", "end");
    }

    private static void verifyCrossPoolContinuation(RealPackProbeSupport.EngineSession session,
                                                     MultiBurst first, MultiBurst second) throws Exception {
        IllegalStateException failure = new IllegalStateException("nested generation failure");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            first.lazy(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruption);
                }
                Runnable failedTask = () -> {
                    throw failure;
                };
                second.lazy(() -> first.lazy(() -> second.lazy(() -> J.a(failedTask))));
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain(session.engine());
            assertEquals(1, reports.size());
            assertSame(failure, reports.getFirst());
            assertTrue(RealPackProbeSupport.drainReported().isEmpty());
        } finally {
            release.countDown();
        }
    }

}
