package art.arcane.iris.generation.runtime;

import org.junit.Test;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class EngineAssignedWorldManagerLifecycleTest {
    @Test
    public void failedRegistrationStillAttemptsListenerRollback() {
        TestWorldManager manager = new TestWorldManager(mock(Engine.class));
        manager.failRegister = true;

        assertThrows(IllegalStateException.class, manager::start);

        assertEquals(1, manager.unregisterAttempts);
        assertEquals(0, manager.cancelAttempts);
    }

    @Test
    public void closeRetriesOnlyIncompleteManagerResources() {
        TestWorldManager manager = new TestWorldManager(mock(Engine.class));
        manager.start();
        manager.failUnregister = true;
        manager.failCancel = true;

        assertThrows(IllegalStateException.class, manager::close);

        assertEquals(1, manager.unregisterAttempts);
        assertEquals(1, manager.cancelAttempts);

        manager.failUnregister = false;
        manager.failCancel = false;
        manager.close();

        assertEquals(2, manager.unregisterAttempts);
        assertEquals(2, manager.cancelAttempts);

        manager.close();

        assertEquals(2, manager.unregisterAttempts);
        assertEquals(2, manager.cancelAttempts);
    }

    private static final class TestWorldManager extends EngineAssignedWorldManager {
        private boolean failRegister;
        private boolean failUnregister;
        private boolean failCancel;
        private int unregisterAttempts;
        private int cancelAttempts;

        private TestWorldManager(Engine engine) {
            super(engine);
        }

        @Override
        protected void registerManagerListener() {
            if (failRegister) {
                throw new IllegalStateException("registration failure");
            }
        }

        @Override
        protected int scheduleManagerTick(Runnable tick) {
            return 42;
        }

        @Override
        protected void unregisterManagerListener() {
            unregisterAttempts++;
            if (failUnregister) {
                throw new IllegalStateException("listener failure");
            }
        }

        @Override
        protected void cancelManagerTick(int scheduledTaskId) {
            cancelAttempts++;
            if (failCancel) {
                throw new IllegalStateException("scheduler failure");
            }
        }

        @Override
        public int getEntityCount() {
            return 0;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public double getEntitySaturation() {
            return 0.0;
        }

        @Override
        public void onTick() {
        }

        @Override
        public void onSave() {
        }

        @Override
        public void onBlockBreak(BlockBreakEvent event) {
        }

        @Override
        public void onBlockPlace(BlockPlaceEvent event) {
        }

        @Override
        public void onChunkLoad(Chunk chunk, boolean generated) {
        }

        @Override
        public void onChunkUnload(Chunk chunk) {
        }

        @Override
        public void teleportAsync(PlayerTeleportEvent event) {
        }
    }
}
