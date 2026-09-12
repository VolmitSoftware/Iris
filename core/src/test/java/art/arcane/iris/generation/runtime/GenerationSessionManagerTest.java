package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class GenerationSessionManagerTest {
    @Test
    public void teardownSealMarksRejectedWorkAsExpected() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();

        manager.sealAndAwait("close", 1000L, true);

        try {
            manager.acquire("chunk_generate");
        } catch (GenerationSessionException e) {
            assertTrue(e.isExpectedTeardown());
            assertTrue(e.getMessage().contains("during close"));
            return;
        }

        throw new AssertionError("Expected teardown rejection.");
    }

    @Test
    public void sealAndAwaitCompletesWhenOutstandingLeaseReleases() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();
        GenerationSessionLease lease = manager.acquire("chunk_generate");
        CountDownLatch latch = new CountDownLatch(1);

        Thread releaser = new Thread(() -> {
            try {
                latch.await(200L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            lease.close();
        });
        releaser.start();
        latch.countDown();

        manager.sealAndAwait("close", 1000L, true);
    }

    @Test
    public void activatingAfterSealPublishesAnIndependentSession() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();
        long sealedSession = manager.currentSessionId();
        manager.sealAndAwait("hotload", 1000L);

        manager.activateNextSession();

        try (GenerationSessionLease lease = manager.acquire("chunk_generate")) {
            assertNotEquals(sealedSession, lease.sessionId());
        }
    }

    @Test
    public void nestedWorkCanContinueAnAlreadyLeasedSessionAfterSeal() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();
        GenerationSessionLease outer = manager.acquire("chunk_pipeline");
        AtomicReference<Throwable> sealFailure = new AtomicReference<>();
        Thread sealer = new Thread(() -> {
            try {
                manager.sealAndAwait("hotload", 1000L);
            } catch (Throwable exception) {
                sealFailure.set(exception);
            }
        });
        sealer.start();

        waitForSeal(manager);
        try (GenerationSessionLease nested = manager.continueSession("biome_lookup", outer.sessionId())) {
            assertEquals(outer.sessionId(), nested.sessionId());
        }
        outer.close();
        sealer.join(1000L);

        assertTrue(!sealer.isAlive());
        if (sealFailure.get() != null) {
            throw new AssertionError("Session seal failed", sealFailure.get());
        }
    }

    private void waitForSeal(GenerationSessionManager manager) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (System.nanoTime() < deadline) {
            try (GenerationSessionLease ignored = manager.acquire("seal_probe")) {
                Thread.onSpinWait();
            } catch (GenerationSessionException expected) {
                return;
            }
        }
        throw new AssertionError("Generation session did not seal within one second.");
    }
}
