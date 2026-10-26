package art.arcane.iris.world;

import art.arcane.iris.Iris;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator.BusyException;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator.Domain;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator.Lease;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator.OperationKind;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.bukkit.NamespacedKey;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class PendingWorldReplacementAdmissionTest {
    @Test
    public void busyReplacementRejectsBeforeWaitingForTheManagerMonitor() throws Exception {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        VolmitSender sender = mock(VolmitSender.class);
        IrisDimension dimension = mock(IrisDimension.class);
        NamespacedKey worldKey = NamespacedKey.minecraft("overworld");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Lease lease = LifecycleOperationCoordinator.get().acquire(
                Domain.WORLD_MUTATION, OperationKind.WORLD_REPLACE, worldKey.toString())) {
            synchronized (manager) {
                Future<BusyException> rejected = executor.submit(() -> assertThrows(
                        BusyException.class,
                        () -> manager.stageReplacement(sender, worldKey, dimension, null)
                ));
                assertEquals(lease.operation(), rejected.get(2L, TimeUnit.SECONDS).currentOperation());
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shutdownRejectsReplacementsThatStartAfterIt() {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        manager.awaitStagingForShutdown();

        IOException rejected = assertThrows(IOException.class, () -> manager.stageReplacement(
                mock(VolmitSender.class), NamespacedKey.minecraft("overworld"), mock(IrisDimension.class), null));

        assertTrue(rejected.getMessage().contains("shutting down"));
        assertTrue(LifecycleOperationCoordinator.get().isIdle());
    }

    @Test
    public void shutdownWaitsForTheReplacementBeingStaged() throws Exception {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        AtomicReference<Throwable> stagingFailure = new AtomicReference<>();
        Thread staging = new Thread(() -> {
            try {
                manager.stageReplacement(mock(VolmitSender.class), NamespacedKey.minecraft("overworld"),
                        mock(IrisDimension.class), null);
            } catch (Throwable failure) {
                stagingFailure.set(failure);
            }
        });
        Thread shutdown = new Thread(manager::awaitStagingForShutdown);
        synchronized (manager) {
            staging.start();
            awaitState(staging, Thread.State.BLOCKED);
            shutdown.start();
            awaitState(shutdown, Thread.State.WAITING);
            assertTrue(staging.isAlive());
        }
        shutdown.join(TimeUnit.SECONDS.toMillis(10L));
        staging.join(TimeUnit.SECONDS.toMillis(10L));

        assertFalse(shutdown.isAlive());
        assertFalse(staging.isAlive());
        assertTrue(stagingFailure.get() instanceof Exception);
        assertTrue(LifecycleOperationCoordinator.get().isIdle());
    }

    private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (thread.getState() != state) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(thread.getName() + " never reached " + state + "; it is " + thread.getState());
            }
            Thread.sleep(5L);
        }
    }
}
