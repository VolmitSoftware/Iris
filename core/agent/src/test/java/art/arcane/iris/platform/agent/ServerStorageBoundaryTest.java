package art.arcane.iris.platform.agent;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ServerStorageBoundaryTest {
    @Test
    public void closingEqualStorageDoesNotCompleteTrackedStorage() {
        Storage primary = new Storage(1);
        Storage other = new Storage(1);
        AtomicBoolean closed = Installer.trackServerStorage(primary);
        try {
            assertSame(closed, Installer.trackServerStorage(primary));
            Installer.serverStorageClosed(other);
            assertFalse(closed.get());
            Installer.serverStorageClosed(primary);
            assertTrue(closed.get());
        } finally {
            Installer.releaseServerStorage(primary);
        }
    }

    @Test
    public void releasedStorageNoLongerReceivesCloseSignals() {
        Storage storage = new Storage(1);
        AtomicBoolean released = Installer.trackServerStorage(storage);
        Installer.releaseServerStorage(storage);
        Installer.serverStorageClosed(storage);
        assertFalse(released.get());
        AtomicBoolean current = Installer.trackServerStorage(storage);
        try {
            assertNotSame(released, current);
            Installer.serverStorageClosed(storage);
            assertTrue(current.get());
            assertFalse(released.get());
        } finally {
            Installer.releaseServerStorage(storage);
        }
    }

    private record Storage(int value) {
    }
}
