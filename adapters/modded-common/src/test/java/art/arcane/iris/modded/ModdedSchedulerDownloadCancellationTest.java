package art.arcane.iris.modded;

import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.pack.PackDownloadExecution;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedSchedulerDownloadCancellationTest {
    @Test
    public void rejectedDownloadSubmissionReleasesItsLease() throws Exception {
        ModdedScheduler scheduler = new ModdedScheduler();
        scheduler.shutdown();
        TestLease lease = new TestLease();
        AtomicBoolean ran = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(
                lease,
                cancellation -> ran.set(true)
        );

        boolean accepted = scheduler.asyncIfRunning(execution, execution::cancel);

        assertFalse(accepted);
        assertTrue(execution.await(1L, TimeUnit.SECONDS));
        assertFalse(ran.get());
        assertEquals(1, lease.closeCount());
    }

    @Test
    public void schedulerShutdownCancelsQueuedDownloadAndReleasesItsLease() throws Exception {
        ModdedScheduler scheduler = new ModdedScheduler();
        int workerCount = Math.max(4, Runtime.getRuntime().availableProcessors());
        CountDownLatch workersStarted = new CountDownLatch(workerCount);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        for (int index = 0; index < workerCount; index++) {
            scheduler.async(() -> {
                workersStarted.countDown();
                try {
                    releaseWorkers.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertTrue(workersStarted.await(10L, TimeUnit.SECONDS));

        TestLease lease = new TestLease();
        AtomicBoolean ran = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(
                lease,
                cancellation -> ran.set(true)
        );
        assertTrue(scheduler.asyncIfRunning(execution, execution::cancel));

        try {
            scheduler.shutdown();

            assertTrue(execution.await(5L, TimeUnit.SECONDS));
            assertFalse(ran.get());
            assertEquals(1, lease.closeCount());
        } finally {
            releaseWorkers.countDown();
            scheduler.shutdown();
        }
    }

    private static final class TestLease implements LifecycleOperationCoordinator.Lease {
        private final LifecycleOperationCoordinator.ActiveOperation operation;
        private final AtomicBoolean closed;
        private final AtomicInteger closeCount;

        private TestLease() {
            operation = new LifecycleOperationCoordinator.ActiveOperation(
                    1L,
                    LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                    "test"
            );
            closed = new AtomicBoolean();
            closeCount = new AtomicInteger();
        }

        @Override
        public LifecycleOperationCoordinator.ActiveOperation operation() {
            return operation;
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            closed.set(true);
        }

        private int closeCount() {
            return closeCount.get();
        }
    }
}
