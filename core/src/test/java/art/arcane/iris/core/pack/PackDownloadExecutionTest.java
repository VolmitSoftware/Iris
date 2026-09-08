package art.arcane.iris.core.pack;

import art.arcane.iris.core.lifecycle.LifecycleOperationCoordinator;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PackDownloadExecutionTest {
    @Test
    public void cancellationBeforeBindingCancelsLateSubmissionAndReleasesLeaseOnce() throws Exception {
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        Future<?> future = mock(Future.class);
        AtomicBoolean ran = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(
                lease,
                cancellation -> ran.set(true)
        );

        execution.cancel();
        execution.bind(future);
        execution.run();

        assertTrue(execution.await(1L, TimeUnit.SECONDS));
        assertFalse(ran.get());
        verify(future).cancel(false);
        verify(lease, times(1)).close();
    }

    @Test
    public void cancellationOfBoundQueuedWorkReleasesLeaseWithoutRunning() throws Exception {
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        Future<?> future = mock(Future.class);
        AtomicBoolean ran = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(
                lease,
                cancellation -> ran.set(true)
        );

        execution.bind(future);
        execution.cancel();
        execution.run();

        assertTrue(execution.await(1L, TimeUnit.SECONDS));
        assertFalse(ran.get());
        verify(future).cancel(false);
        verify(lease, times(1)).close();
    }

    @Test
    public void cancellationInterruptsRunningWorkOutsidePublication() throws Exception {
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(lease, cancellation -> {
            started.countDown();
            try {
                neverReleased.await(30L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            cancellation.checkpoint();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            execution.bind(executor.submit(execution));
            assertTrue(started.await(5L, TimeUnit.SECONDS));

            execution.cancel();

            assertTrue(execution.await(5L, TimeUnit.SECONDS));
            assertTrue(interrupted.get());
            verify(lease, times(1)).close();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancellationAllowsAtomicPublicationToCompleteWithoutInterruptingIt() throws Exception {
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        PackDownloadExecution execution = new PackDownloadExecution(lease, cancellation -> {
            cancellation.beginPublication();
            publishing.countDown();
            try {
                releasePublication.await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            execution.bind(executor.submit(execution));
            assertTrue(publishing.await(5L, TimeUnit.SECONDS));

            execution.cancel();

            assertFalse(execution.await(100L, TimeUnit.MILLISECONDS));
            assertFalse(interrupted.get());
            releasePublication.countDown();
            assertTrue(execution.await(5L, TimeUnit.SECONDS));
            assertFalse(interrupted.get());
            verify(lease, times(1)).close();
        } finally {
            releasePublication.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }
}
