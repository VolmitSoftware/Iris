package art.arcane.iris.world.lifecycle;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LifecycleOperationCoordinatorTest {
    @Test
    public void getReturnsSingletonInstance() {
        assertSame(LifecycleOperationCoordinator.get(), LifecycleOperationCoordinator.get());
    }

    @Test
    public void duplicateDomainIsRejectedWithCurrentOperationMetadata() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease lease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");

        LifecycleOperationCoordinator.BusyException exception = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                        "world-two"));

        assertEquals(lease.operation(), exception.currentOperation());
        assertEquals(lease.operation().id(), exception.operationId());
        assertEquals(LifecycleOperationCoordinator.Domain.WORLD_MUTATION, exception.domain());
        assertEquals(LifecycleOperationCoordinator.OperationKind.WORLD_CREATE, exception.operationKind());
        assertEquals("world-one", exception.target());
        lease.close();
    }

    @Test
    public void differentDomainsConflictWithCurrentOperationMetadata() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease worldLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");

        LifecycleOperationCoordinator.BusyException exception = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.PACK_CREATE,
                        "pack-one"));

        Map<LifecycleOperationCoordinator.Domain, LifecycleOperationCoordinator.ActiveOperation> snapshot = coordinator.snapshot();
        assertEquals(1, snapshot.size());
        assertEquals(worldLease.operation(), snapshot.get(LifecycleOperationCoordinator.Domain.WORLD_MUTATION));
        assertEquals(Optional.of(worldLease.operation()), coordinator.active(LifecycleOperationCoordinator.Domain.WORLD_MUTATION));
        assertEquals(Optional.empty(), coordinator.active(LifecycleOperationCoordinator.Domain.PACK_MUTATION));
        assertEquals(worldLease.operation(), exception.currentOperation());
        assertFalse(coordinator.isIdle());

        worldLease.close();
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void closeReleasesDomainForNextOperation() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease firstLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_CREATE,
                "pack-one");
        firstLease.close();

        LifecycleOperationCoordinator.Lease secondLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                "pack-two");

        assertTrue(firstLease.isClosed());
        assertFalse(secondLease.isClosed());
        assertTrue(secondLease.operation().id() > firstLease.operation().id());
        secondLease.close();
    }

    @Test
    public void closingLeaseTwiceIsHarmless() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease lease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                "world-one");

        lease.close();
        lease.close();

        assertTrue(lease.isClosed());
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void idleCallbackRunsImmediatelyExactlyOnce() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        AtomicInteger callbackCount = new AtomicInteger();

        coordinator.whenIdle(callbackCount::incrementAndGet);

        assertEquals(1, callbackCount.get());
    }

    @Test
    public void idleCallbackWaitsForActiveMutation() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease worldLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");
        AtomicInteger callbackCount = new AtomicInteger();

        coordinator.whenIdle(callbackCount::incrementAndGet);
        assertEquals(0, callbackCount.get());
        worldLease.close();
        assertEquals(1, callbackCount.get());
    }

    @Test
    public void idleCallbackAcquiresBeforeACompetingMutationCanEnter() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease initialLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");
        AtomicReference<LifecycleOperationCoordinator.Lease> callbackLease = new AtomicReference<>();
        coordinator.whenIdle(() -> callbackLease.set(coordinator.acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_CREATE,
                "pack-one")));

        initialLease.close();

        LifecycleOperationCoordinator.Lease reservedLease = callbackLease.get();
        assertEquals(LifecycleOperationCoordinator.Domain.PACK_MUTATION, reservedLease.operation().domain());
        LifecycleOperationCoordinator.BusyException exception = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                        "world-two"));
        assertEquals(reservedLease.operation(), exception.currentOperation());
        reservedLease.close();
    }

    @Test
    public void quiesceBlocksNewMutationsBeforeDrainAndThroughRestartDispatch() throws InterruptedException {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease activeLease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch allowDispatchCompletion = new CountDownLatch(1);

        assertTrue(coordinator.quiesceForRestart(() -> {
            dispatchStarted.countDown();
            await(allowDispatchCompletion);
        }));

        LifecycleOperationCoordinator.BusyException drainingException = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                        "pack-one"));
        assertEquals(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE, drainingException.domain());
        assertEquals(LifecycleOperationCoordinator.OperationKind.SERVER_RESTART, drainingException.operationKind());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(activeLease::close);
        assertTrue(dispatchStarted.await(5L, TimeUnit.SECONDS));

        LifecycleOperationCoordinator.BusyException dispatchException = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                        "world-two"));
        assertEquals(drainingException.currentOperation(), dispatchException.currentOperation());

        allowDispatchCompletion.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        assertFalse(coordinator.isIdle());
        assertEquals(Optional.of(drainingException.currentOperation()),
                coordinator.active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE));
        assertFalse(coordinator.quiesceForRestart(() -> {
            throw new AssertionError("duplicate restart callback must not run");
        }));
        LifecycleOperationCoordinator.BusyException terminalException = assertThrows(
                LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.PACK_PUBLISH,
                        "pack-two"));
        assertEquals(drainingException.currentOperation(), terminalException.currentOperation());
    }

    @Test
    public void concurrentIdleCallbacksEachRunExactlyOnce() throws InterruptedException {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease lease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");
        int callbackTotal = 32;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch registered = new CountDownLatch(callbackTotal);
        AtomicIntegerArray callbackCounts = new AtomicIntegerArray(callbackTotal);

        for (int callbackIndex = 0; callbackIndex < callbackTotal; callbackIndex++) {
            int registeredIndex = callbackIndex;
            executor.execute(() -> {
                await(start);
                coordinator.whenIdle(() -> callbackCounts.incrementAndGet(registeredIndex));
                registered.countDown();
            });
        }

        start.countDown();
        assertTrue(registered.await(5L, TimeUnit.SECONDS));
        lease.close();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));

        for (int callbackIndex = 0; callbackIndex < callbackTotal; callbackIndex++) {
            assertEquals(1, callbackCounts.get(callbackIndex));
        }
    }

    @Test
    public void concurrentDoubleCloseReleasesAndSignalsIdleOnce() throws InterruptedException {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease lease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_PUBLISH,
                "pack-one");
        AtomicInteger callbackCount = new AtomicInteger();
        coordinator.whenIdle(callbackCount::incrementAndGet);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int threadIndex = 0; threadIndex < 16; threadIndex++) {
            executor.execute(() -> {
                await(start);
                lease.close();
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        assertTrue(coordinator.isIdle());
        assertEquals(1, callbackCount.get());
    }

    @Test
    public void failingIdleCallbackDoesNotBlockLeaseReleaseOrLaterCallbacks() {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        LifecycleOperationCoordinator.Lease lease = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "world-one");
        AtomicInteger callbackCount = new AtomicInteger();
        coordinator.whenIdle(() -> {
            throw new IllegalStateException("expected");
        });
        coordinator.whenIdle(callbackCount::incrementAndGet);

        lease.close();

        assertTrue(coordinator.isIdle());
        assertEquals(1, callbackCount.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
