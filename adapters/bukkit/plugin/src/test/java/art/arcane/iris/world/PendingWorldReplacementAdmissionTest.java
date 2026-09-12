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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
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
}
