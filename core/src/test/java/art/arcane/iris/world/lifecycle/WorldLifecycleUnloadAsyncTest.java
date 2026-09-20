package art.arcane.iris.world.lifecycle;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldLifecycleUnloadAsyncTest {
    @Test
    public void serviceRemovesRememberedBackendOnlyAfterTrueCompletion() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        ControlledBackend fallback = new ControlledBackend("fallback");
        WorldLifecycleService service = service(remembered, fallback);
        NamespacedKey worldKey = new NamespacedKey("iris", "async_true");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());

        CompletableFuture<Boolean> result = service.unloadAsync(world, false);

        assertFalse(result.isDone());
        assertEquals("remembered", service.backendNameForWorld(worldKey));
        remembered.complete(true);
        assertTrue(result.join());
        assertEquals("fallback", service.backendNameForWorld(worldKey));
    }

    @Test
    public void servicePublishesRawUnloadCompletionForTheWorldEventConsumer() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        ControlledBackend fallback = new ControlledBackend("fallback");
        WorldLifecycleService service = service(remembered, fallback);
        NamespacedKey worldKey = new NamespacedKey("iris", "raw_boundary");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());

        CompletableFuture<Boolean> result = service.unloadAsync(world, false);
        CompletionStage<Boolean> boundary = WorldUnloadBoundaryRegistry.claim(WorldIdentity.serialize(world));

        assertNotNull(boundary);
        assertFalse(boundary.toCompletableFuture().isDone());
        assertFalse(result.isDone());
        remembered.complete(true);
        assertTrue(boundary.toCompletableFuture().join());
        assertTrue(result.join());
    }

    @Test
    public void serviceRetainsRememberedBackendAfterFalseCompletion() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        ControlledBackend fallback = new ControlledBackend("fallback");
        WorldLifecycleService service = service(remembered, fallback);
        NamespacedKey worldKey = new NamespacedKey("iris", "async_false");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());

        CompletableFuture<Boolean> result = service.unloadAsync(world, false);
        remembered.complete(false);

        assertFalse(result.join());
        assertEquals("remembered", service.backendNameForWorld(worldKey));
    }

    @Test
    public void serviceRetainsRememberedBackendAfterExceptionalCompletion() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        ControlledBackend fallback = new ControlledBackend("fallback");
        WorldLifecycleService service = service(remembered, fallback);
        NamespacedKey worldKey = new NamespacedKey("iris", "async_failure");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());

        CompletableFuture<Boolean> result = service.unloadAsync(world, false);
        remembered.fail(new IllegalStateException("delayed failure"));

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals("delayed failure", failure.getCause().getMessage());
        assertEquals("remembered", service.backendNameForWorld(worldKey));
    }

    @Test
    public void settledUnloadRetainsTheBoundaryUntilBackendCompletionWithoutATerminalTimer() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        WorldLifecycleService service = service(remembered, new ControlledBackend("fallback"));
        NamespacedKey worldKey = new NamespacedKey("iris", "settled_boundary");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());
        ArrayList<Runnable> timeouts = new ArrayList<>();
        Executor captureTimeout = timeouts::add;

        try (MockedStatic<CompletableFuture> futures = mockStatic(CompletableFuture.class, CALLS_REAL_METHODS)) {
            futures.when(() -> CompletableFuture.delayedExecutor(120L, TimeUnit.SECONDS))
                    .thenReturn(captureTimeout);

            CompletableFuture<Boolean> result = service.unloadSettledAsync(world, false);
            CompletionStage<Boolean> boundary = WorldUnloadBoundaryRegistry.claim(worldKey.toString());

            assertNotNull(boundary);
            assertFalse(boundary.toCompletableFuture().isDone());
            assertFalse(result.isDone());
            assertTrue(timeouts.isEmpty());
            assertEquals("remembered", service.backendNameForWorld(worldKey));

            remembered.complete(true);

            assertTrue(result.join());
            assertTrue(boundary.toCompletableFuture().join());
            assertEquals("fallback", service.backendNameForWorld(worldKey));
        }
    }

    @Test
    public void cancellingSettledUnloadResultPreservesBackendCompletionBookkeeping() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        WorldLifecycleService service = service(remembered, new ControlledBackend("fallback"));
        NamespacedKey worldKey = new NamespacedKey("iris", "cancelled_settlement");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());

        CompletableFuture<Boolean> result = service.unloadSettledAsync(world, false);
        CompletionStage<Boolean> boundary = WorldUnloadBoundaryRegistry.claim(worldKey.toString());

        assertNotNull(boundary);
        assertTrue(result.cancel(false));
        assertFalse(boundary.toCompletableFuture().isDone());
        assertEquals("remembered", service.backendNameForWorld(worldKey));

        remembered.complete(true);

        assertTrue(result.isCancelled());
        assertTrue(boundary.toCompletableFuture().join());
        assertEquals("fallback", service.backendNameForWorld(worldKey));
    }

    @Test
    public void guardedUnloadKeepsItsTimeoutAndTracksLateBackendSuccess() {
        ControlledBackend remembered = new ControlledBackend("remembered");
        WorldLifecycleService service = service(remembered, new ControlledBackend("fallback"));
        NamespacedKey worldKey = new NamespacedKey("iris", "late_settlement");
        World world = world(worldKey);
        service.rememberBackend(worldKey, remembered.backendName());
        ArrayList<Runnable> timeouts = new ArrayList<>();
        Executor captureTimeout = timeouts::add;

        try (MockedStatic<CompletableFuture> futures = mockStatic(CompletableFuture.class, CALLS_REAL_METHODS);
             MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class);
             MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            futures.when(() -> CompletableFuture.delayedExecutor(120L, TimeUnit.SECONDS))
                    .thenReturn(captureTimeout);

            CompletableFuture<Boolean> result = service.unloadAsync(world, false);
            CompletionStage<Boolean> boundary = WorldUnloadBoundaryRegistry.claim(worldKey.toString());

            assertEquals(1, timeouts.size());
            timeouts.getFirst().run();

            CompletionException failure = assertThrows(CompletionException.class, result::join);
            assertTrue(failure.getCause() instanceof TimeoutException);
            configurator.verify(() -> ServerConfigurator.restart("World unload timed out for \"late_settlement\"."));
            assertFalse(boundary.toCompletableFuture().isDone());
            assertEquals("remembered", service.backendNameForWorld(worldKey));

            remembered.complete(true);

            assertTrue(boundary.toCompletableFuture().join());
            assertTrue(result.isCompletedExceptionally());
            assertEquals("fallback", service.backendNameForWorld(worldKey));
        }
    }

    private static WorldLifecycleService service(
            ControlledBackend remembered,
            ControlledBackend fallback
    ) {
        ControlledBackend inactive = new ControlledBackend("inactive");
        return new WorldLifecycleService(
                CapabilitySnapshotFixtures.forTesting(ServerFamily.PURPUR, false, false, false),
                inactive,
                remembered,
                fallback
        );
    }

    private static World world(NamespacedKey key) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(key);
        when(world.getName()).thenReturn(key.getKey());
        return world;
    }

    private static final class ControlledBackend implements WorldLifecycleBackend {
        private final String name;
        private final CompletableFuture<Boolean> unloadResult = new CompletableFuture<>();

        private ControlledBackend(String name) {
            this.name = name;
        }

        @Override
        public boolean supports(WorldLifecycleRequest request, CapabilitySnapshot capabilities) {
            return false;
        }

        @Override
        public CompletableFuture<World> create(WorldLifecycleRequest request) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> unloadAsync(World world, boolean save) {
            return unloadResult;
        }

        @Override
        public String backendName() {
            return name;
        }

        private void complete(boolean unloaded) {
            unloadResult.complete(unloaded);
        }

        private void fail(Throwable failure) {
            unloadResult.completeExceptionally(failure);
        }
    }
}
