package art.arcane.iris.core.lifecycle;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldLifecycleUnloadAsyncTest {
    @Test
    public void manualUnloadFallbackDispatchesWorldUnloadEvent() {
        World world = mock(World.class);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertTrue(WorldLifecycleSupport.announceManualWorldUnload(world));
        }

        verify(pluginManager).callEvent(any(WorldUnloadEvent.class));
    }

    @Test
    public void manualUnloadFallbackHonorsCancelledWorldUnloadEvent() {
        World world = mock(World.class);
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            WorldUnloadEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(WorldUnloadEvent.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertFalse(WorldLifecycleSupport.announceManualWorldUnload(world));
        }
    }

    @Test
    public void reflectedAsyncUnloadWaitsForTrueCallback() throws Exception {
        CallbackServer server = new CallbackServer();
        Method unloadMethod = CallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = WorldLifecycleSupport.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        assertFalse(result.isDone());
        server.complete(true);
        assertTrue(result.join());
    }

    @Test
    public void reflectedAsyncUnloadWaitsForFalseCallback() throws Exception {
        CallbackServer server = new CallbackServer();
        Method unloadMethod = CallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = WorldLifecycleSupport.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                true
        );

        assertFalse(result.isDone());
        server.complete(false);
        assertFalse(result.join());
    }

    @Test
    public void reflectedAsyncUnloadAcceptsCanvasResultCallback() throws Exception {
        CanvasCallbackServer server = new CanvasCallbackServer();
        Method unloadMethod = CanvasCallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = WorldLifecycleSupport.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                true
        );

        assertFalse(result.isDone());
        server.complete(CanvasUnloadResult.SUCCESS);
        assertTrue(result.join());
    }

    @Test
    public void reflectedAsyncUnloadMapsCanvasFailureResultToFalse() throws Exception {
        CanvasCallbackServer server = new CanvasCallbackServer();
        Method unloadMethod = CanvasCallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = WorldLifecycleSupport.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        server.complete(CanvasUnloadResult.FAILURE);
        assertFalse(result.join());
    }

    @Test
    public void reflectedAsyncUnloadPropagatesInvocationFailure() throws Exception {
        FailingServer server = new FailingServer();
        Method unloadMethod = FailingServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = WorldLifecycleSupport.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertEquals("unload failed", failure.getCause().getMessage());
    }

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

    public static final class CallbackServer {
        private Consumer<Boolean> callback;

        public void unloadWorldAsync(World world, boolean save, Consumer<Boolean> callback) {
            this.callback = callback;
        }

        private void complete(boolean unloaded) {
            callback.accept(unloaded);
        }
    }

    public static final class FailingServer {
        public void unloadWorldAsync(World world, boolean save, Consumer<Boolean> callback) {
            throw new IllegalStateException("unload failed");
        }
    }

    public static final class CanvasCallbackServer {
        private Consumer<CanvasUnloadResult> callback;

        public void unloadWorldAsync(World world, boolean save, Consumer<CanvasUnloadResult> callback) {
            this.callback = callback;
        }

        private void complete(CanvasUnloadResult result) {
            callback.accept(result);
        }
    }

    public enum CanvasUnloadResult {
        SUCCESS,
        FAILURE;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
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
