package art.arcane.iris.core;

import art.arcane.iris.Iris;
import org.bukkit.NamespacedKey;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PendingWorldReplacementAdmissionTest {
    private static final long STATE_TIMEOUT_SECONDS = 5L;
    private static final long JOIN_TIMEOUT_SECONDS = 10L;
    private static final long STATE_POLL_MILLIS = 5L;

    private final NamespacedKey worldKey = NamespacedKey.minecraft("overworld");

    @Test
    public void shutdownDoesNotWaitWhenNothingIsStaging() throws Exception {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        Thread shutdown = new Thread(manager::awaitStagingForShutdown);

        shutdown.start();
        shutdown.join(TimeUnit.SECONDS.toMillis(JOIN_TIMEOUT_SECONDS));

        assertFalse(shutdown.isAlive());
    }

    @Test
    public void shutdownRejectsReplacementsThatStartAfterIt() throws Exception {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        manager.awaitStagingForShutdown();

        IOException rejected = assertThrows(IOException.class, () -> manager.admitStaging(worldKey));

        assertTrue(rejected.getMessage().contains("shutting down"));
    }

    @Test
    public void shutdownWaitsForTheReplacementBeingStaged() throws Exception {
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        PendingWorldReplacementManager.StagingAdmission staging = manager.admitStaging(worldKey);
        Thread shutdown = new Thread(manager::awaitStagingForShutdown);

        shutdown.start();
        try {
            awaitState(shutdown, Thread.State.WAITING);
            assertTrue(shutdown.isAlive());
        } finally {
            staging.close();
        }
        shutdown.join(TimeUnit.SECONDS.toMillis(JOIN_TIMEOUT_SECONDS));

        assertFalse(shutdown.isAlive());
    }

    private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STATE_TIMEOUT_SECONDS);
        while (thread.getState() != state) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(thread.getName() + " never reached " + state + "; it is " + thread.getState());
            }
            Thread.sleep(STATE_POLL_MILLIS);
        }
    }
}
