package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.WorldRemovalPathPolicy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisWorldRemovalServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsBusyOperationWithoutStartingBackend() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("busy"));
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        try (LifecycleOperationCoordinator.Lease existing = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "other"
        )) {
            IrisWorldRemovalService.RemovalResult result = service.remove("busy", true).join();

            assertEquals(IrisWorldRemovalService.RemovalStatus.BUSY, result.status());
            assertTrue(result.busy());
            assertEquals(existing.operation(), result.blockingOperation());
            assertFalse(backend.resolvedTarget);
        }
    }

    @Test
    public void holdsLeaseUntilEveryAsyncPhaseCompletes() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("serialized"));
        CompletableFuture<Void> evacuation = new CompletableFuture<>();
        backend.evacuation = evacuation;
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        CompletableFuture<IrisWorldRemovalService.RemovalResult> pending = service.remove("serialized", false);
        IrisWorldRemovalService.RemovalResult busy = service.remove("second", false).join();

        assertFalse(pending.isDone());
        assertEquals(IrisWorldRemovalService.RemovalStatus.BUSY, busy.status());
        evacuation.complete(null);

        IrisWorldRemovalService.RemovalResult result = pending.join();
        assertEquals(IrisWorldRemovalService.RemovalStatus.UNREGISTERED, result.status());
        assertTrue(result.succeeded());
        assertTrue(result.registryChanged());
        assertEquals(
                List.of("resolve", "begin", "evacuate", "unload", "close", "unregister", "unregisterRegistry", "end"),
                backend.operations
        );
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void configurationFailureStopsBeforeDeletionAndRetainsCause() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("configuration-failure"));
        IOException failure = new IOException("bukkit.yml is read-only");
        backend.unregister = CompletableFuture.completedFuture(
                new IrisWorldRemovalService.ConfigurationDisposition(false, failure)
        );
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        IrisWorldRemovalService.RemovalResult result = service.remove("configuration-failure", true).join();

        assertEquals(IrisWorldRemovalService.RemovalStatus.CONFIGURATION_FAILED, result.status());
        assertSame(failure, result.failure());
        assertFalse(result.succeeded());
        assertFalse(backend.operations.contains("delete"));
        assertFalse(backend.operations.contains("unregisterRegistry"));
        assertFalse(result.registryChanged());
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void queuedDeletionIsSuccessfulAfterRegistryRemoval() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        WorldRemovalPathPolicy.Target target = target("queued");
        FakeBackend backend = new FakeBackend(target);
        Path quarantine = target.worldDirectory().resolveSibling(".iris-delete-test");
        backend.deletion = CompletableFuture.completedFuture(
                new IrisWorldRemovalService.DeleteDisposition(true, quarantine)
        );
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        IrisWorldRemovalService.RemovalResult result = service.remove("queued", true).join();

        assertEquals(IrisWorldRemovalService.RemovalStatus.DELETE_QUEUED, result.status());
        assertTrue(result.succeeded());
        assertTrue(result.deletionDeferred());
        assertEquals(quarantine, result.quarantineDirectory());
        assertTrue(result.registryChanged());
    }

    @Test
    public void bukkitUnregistrationUsesCanonicalPaperStartupName() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("world").toPath();
        WorldRemovalPathPolicy.Target target = WorldRemovalPathPolicy.resolve("moon", "world", levelRoot);

        assertEquals(
                "world_iris_moon",
                IrisWorldRemovalService.bukkitConfigurationWorldName(target)
        );
    }

    @Test
    public void removalAcceptsTheConfiguredStartupNameThatIrisWorldsPrints() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("world").toPath();
        WorldRemovalPathPolicy.Target logical = WorldRemovalPathPolicy.resolve("moon", "world", levelRoot);
        WorldRemovalPathPolicy.Target configured =
                WorldRemovalPathPolicy.resolve("world_iris_moon", "world", levelRoot);

        assertEquals(logical.worldKey(), configured.worldKey());
        assertEquals("moon", configured.logicalName());
        assertEquals(logical.worldDirectory(), configured.worldDirectory());
        assertEquals(
                "world_iris_moon",
                IrisWorldRemovalService.bukkitConfigurationWorldName(configured)
        );
    }

    @Test
    public void diskInspectionReadsOnlyCanonicalPaperStartupSection() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("inspection-world").toPath();
        WorldRemovalPathPolicy.Target target = WorldRemovalPathPolicy.resolve(
                "moon",
                "inspection-world",
                levelRoot
        );
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("worlds.moon.generator", "Iris:noncanonical");

        assertNull(IrisWorldRemovalService.bukkitGenerator(configuration, target));

        configuration.set("worlds.inspection-world_iris_moon.generator", "Iris:overworld");
        assertEquals("Iris:overworld", IrisWorldRemovalService.bukkitGenerator(configuration, target));
    }

    @Test
    public void registryFailureStopsBeforeFilesystemDeletion() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("registry-failure"));
        IOException failure = new IOException("worlds.json is read-only");
        backend.unregisterRegistry = CompletableFuture.failedFuture(failure);
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        IrisWorldRemovalService.RemovalResult result = service.remove("registry-failure", true).join();

        assertEquals(IrisWorldRemovalService.RemovalStatus.REGISTRY_FAILED, result.status());
        assertSame(failure, result.failure());
        assertTrue(result.configurationChanged());
        assertFalse(result.registryChanged());
        assertFalse(backend.operations.contains("delete"));
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void deletionFailureRetainsCompletedUnregistrationState() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        WorldRemovalPathPolicy.Target target = target("delete-failure");
        FakeBackend backend = new FakeBackend(target);
        IOException failure = new IOException("cannot quarantine directory");
        backend.deletion = CompletableFuture.failedFuture(failure);
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        IrisWorldRemovalService.RemovalResult result = service.remove("delete-failure", true).join();

        assertEquals(IrisWorldRemovalService.RemovalStatus.QUARANTINE_FAILED, result.status());
        assertSame(failure, result.failure());
        assertTrue(result.configurationChanged());
        assertTrue(result.registryChanged());
    }

    @Test
    public void unloadRefusalLeavesGeneratorOpenAndEndsMaintenance() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("unload-refused"));
        backend.unload = CompletableFuture.completedFuture(false);
        IrisWorldRemovalService service = new IrisWorldRemovalService(coordinator, backend);

        IrisWorldRemovalService.RemovalResult result = service.remove("unload-refused", true).join();

        assertEquals(IrisWorldRemovalService.RemovalStatus.UNLOAD_FAILED, result.status());
        assertFalse(backend.operations.contains("close"));
        assertFalse(backend.operations.contains("unregister"));
        assertEquals("end", backend.operations.get(backend.operations.size() - 1));
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void timeoutInstallsRestartFenceBeforeResultAndRejectsLateContinuation() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("timeout-fence"));
        NonCancellableFuture<Boolean> unload = new NonCancellableFuture<>();
        backend.unload = unload;
        AtomicInteger restartDispatches = new AtomicInteger();
        AtomicBoolean fenceInstalledBeforeResult = new AtomicBoolean(false);
        AtomicReference<CompletableFuture<IrisWorldRemovalService.RemovalResult>> resultReference =
                new AtomicReference<>();
        IrisWorldRemovalService service = new IrisWorldRemovalService(
                coordinator,
                new IrisWorldRemovalService.ServiceOptions(
                        backend,
                        100L,
                        reason -> {
                            assertTrue(coordinator.quiesceForRestart(restartDispatches::incrementAndGet));
                            CompletableFuture<IrisWorldRemovalService.RemovalResult> result = resultReference.get();
                            fenceInstalledBeforeResult.set(
                                    coordinator.active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE).isPresent()
                                            && (result == null || !result.isDone()));
                        }
                )
        );

        CompletableFuture<IrisWorldRemovalService.RemovalResult> pending =
                service.remove("timeout-fence", false);
        resultReference.set(pending);
        IrisWorldRemovalService.RemovalResult result = pending.get(2L, TimeUnit.SECONDS);

        assertEquals(IrisWorldRemovalService.RemovalStatus.UNLOAD_FAILED, result.status());
        assertTrue(fenceInstalledBeforeResult.get());
        assertEquals(1, restartDispatches.get());
        assertEquals(LifecycleOperationCoordinator.OperationKind.SERVER_RESTART,
                coordinator.active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE)
                        .orElseThrow()
                        .kind());

        IrisWorldRemovalService.RemovalResult blocked = service.remove("second", false).join();
        assertEquals(IrisWorldRemovalService.RemovalStatus.BUSY, blocked.status());
        assertEquals(LifecycleOperationCoordinator.OperationKind.SERVER_RESTART,
                blocked.blockingOperation().kind());

        unload.complete(true);
        assertFalse(backend.operations.contains("close"));
        assertFalse(backend.operations.contains("unregister"));
        assertFalse(backend.operations.contains("delete"));
    }

    @Test
    public void terminalTimeoutStopsDeletionAfterIntentCompletion() throws Exception {
        LifecycleOperationCoordinator coordinator = new LifecycleOperationCoordinator();
        FakeBackend backend = new FakeBackend(target("delete-terminal"));
        backend.stageDeletion = true;
        AtomicInteger restartDispatches = new AtomicInteger();
        IrisWorldRemovalService service = new IrisWorldRemovalService(
                coordinator,
                new IrisWorldRemovalService.ServiceOptions(
                        backend,
                        100L,
                        reason -> coordinator.quiesceForRestart(restartDispatches::incrementAndGet)
                )
        );

        IrisWorldRemovalService.RemovalResult result = service
                .remove("delete-terminal", true)
                .get(2L, TimeUnit.SECONDS);

        assertEquals(IrisWorldRemovalService.RemovalStatus.QUARANTINE_FAILED, result.status());
        assertTrue(backend.operations.contains("deleteIntent"));
        assertEquals(1, restartDispatches.get());

        backend.deletionIntent.complete(null);
        assertFalse(backend.operations.contains("quarantine"));
        assertFalse(backend.operations.contains("deleteQuarantine"));
    }

    private WorldRemovalPathPolicy.Target target(String identifier) throws Exception {
        Path levelRoot = temporaryFolder.newFolder(identifier + "-level").toPath();
        return WorldRemovalPathPolicy.resolve(identifier, "production", levelRoot);
    }

    private static final class FakeBackend implements IrisWorldRemovalService.RemovalBackend {
        private final WorldRemovalPathPolicy.Target target;
        private final List<String> operations;
        private boolean resolvedTarget;
        private CompletableFuture<Void> evacuation;
        private CompletableFuture<Boolean> unload;
        private CompletableFuture<IrisWorldRemovalService.ConfigurationDisposition> unregister;
        private CompletableFuture<Boolean> unregisterRegistry;
        private CompletableFuture<IrisWorldRemovalService.DeleteDisposition> deletion;
        private final NonCancellableFuture<Void> deletionIntent;
        private boolean stageDeletion;

        private FakeBackend(WorldRemovalPathPolicy.Target target) {
            this.target = target;
            operations = new ArrayList<>();
            evacuation = CompletableFuture.completedFuture(null);
            unload = CompletableFuture.completedFuture(true);
            unregister = CompletableFuture.completedFuture(
                    new IrisWorldRemovalService.ConfigurationDisposition(true, null)
            );
            unregisterRegistry = CompletableFuture.completedFuture(true);
            deletion = CompletableFuture.completedFuture(
                    new IrisWorldRemovalService.DeleteDisposition(false, null)
            );
            deletionIntent = new NonCancellableFuture<>();
        }

        @Override
        public WorldRemovalPathPolicy.Target resolveTarget(String identifier) {
            resolvedTarget = true;
            return target;
        }

        @Override
        public CompletableFuture<IrisWorldRemovalService.ResolvedWorld> resolve(
                WorldRemovalPathPolicy.Target ignored
        ) {
            operations.add("resolve");
            return CompletableFuture.completedFuture(new IrisWorldRemovalService.ResolvedWorld(
                    target,
                    null,
                    null,
                    true,
                    false,
                    false,
                    true
            ));
        }

        @Override
        public CompletableFuture<Void> evacuatePlayers(IrisWorldRemovalService.ResolvedWorld resolvedWorld) {
            operations.add("evacuate");
            return evacuation;
        }

        @Override
        public CompletableFuture<Void> beginMaintenance(IrisWorldRemovalService.ResolvedWorld resolvedWorld) {
            operations.add("begin");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> endMaintenance(IrisWorldRemovalService.ResolvedWorld resolvedWorld) {
            operations.add("end");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> closeGenerator(IrisWorldRemovalService.ResolvedWorld resolvedWorld) {
            operations.add("close");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> unloadWorld(IrisWorldRemovalService.ResolvedWorld resolvedWorld) {
            operations.add("unload");
            return unload;
        }

        @Override
        public CompletableFuture<IrisWorldRemovalService.ConfigurationDisposition> unregister(
                WorldRemovalPathPolicy.Target ignored,
                BooleanSupplier terminal
        ) {
            operations.add("unregister");
            return unregister;
        }

        @Override
        public CompletableFuture<Boolean> unregisterRegistry(
                WorldRemovalPathPolicy.Target ignored,
                BooleanSupplier terminal
        ) {
            operations.add("unregisterRegistry");
            return unregisterRegistry;
        }

        @Override
        public CompletableFuture<IrisWorldRemovalService.DeleteDisposition> delete(
                WorldRemovalPathPolicy.Target ignored,
                BooleanSupplier terminal
        ) {
            operations.add("delete");
            if (stageDeletion) {
                operations.add("deleteIntent");
                NonCancellableFuture<IrisWorldRemovalService.DeleteDisposition> result =
                        new NonCancellableFuture<>();
                deletionIntent.whenComplete((ignoredValue, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                        return;
                    }
                    if (terminal.getAsBoolean()) {
                        result.completeExceptionally(new IllegalStateException(
                                "terminal after deletion intent"));
                        return;
                    }
                    operations.add("quarantine");
                    if (terminal.getAsBoolean()) {
                        result.completeExceptionally(new IllegalStateException(
                                "terminal after quarantine"));
                        return;
                    }
                    operations.add("deleteQuarantine");
                    result.complete(new IrisWorldRemovalService.DeleteDisposition(false, null));
                });
                return result;
            }
            return deletion;
        }
    }

    private static final class NonCancellableFuture<T> extends CompletableFuture<T> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }
}
