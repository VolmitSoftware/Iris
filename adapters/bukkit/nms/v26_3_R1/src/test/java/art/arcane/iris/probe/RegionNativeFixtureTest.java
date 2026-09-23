package art.arcane.iris.probe;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RegionNativeFixtureTest {
    @Test
    public void stoppingReleasesPendingAndRejectsLaterAdmissions() {
        RegionNativeFixture.LoadCompletions requests = new RegionNativeFixture.LoadCompletions();
        CompletableFuture<Void> pending = requests.register();
        CompletableFuture<Void> completed = requests.register();
        completed.complete(null);
        assertEquals(1, requests.pendingCount());
        requests.stop();
        assertTrue(pending.isCompletedExceptionally());
        assertFalse(completed.isCompletedExceptionally());
        assertTrue(requests.register().isCompletedExceptionally());
        assertEquals(0, requests.pendingCount());
    }

    @Test
    public void concurrentShutdownLeavesNoUncompletedAdmissions() throws Exception {
        for (int round = 0; round < 32; round++) {
            RegionNativeFixture.LoadCompletions requests = new RegionNativeFixture.LoadCompletions();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CompletableFuture<Void>>> admissions = new ArrayList<>();
            try (ExecutorService workers = Executors.newFixedThreadPool(8)) {
                for (int index = 0; index < 32; index++) {
                    admissions.add(workers.submit(() -> {
                        start.await();
                        return requests.register();
                    }));
                }
                start.countDown();
                requests.stop();
                for (Future<CompletableFuture<Void>> admission : admissions) {
                    assertTrue(admission.get(5, TimeUnit.SECONDS).isCompletedExceptionally());
                }
            }
            assertEquals(0, requests.pendingCount());
        }
    }
}
