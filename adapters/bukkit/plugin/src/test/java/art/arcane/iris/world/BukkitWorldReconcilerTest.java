package art.arcane.iris.world;

import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BukkitWorldReconcilerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void disableStartupValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void loadLeaseCoversRegistrationThroughExactWorldCompletion() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        LifecycleOperationCoordinator coordinator = coordinator();
        FakeBackend backend = new FakeBackend();
        CompletableFuture<World> creation = new CompletableFuture<>();
        backend.creation = creation;
        World exactWorld = world(backend.worldKey);
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator);

        CompletableFuture<BukkitWorldReconciler.LoadResult> load = reconciler.loadWorld(configuration, backend.worldKey.toString());

        assertFalse(load.isDone());
        assertEquals(LifecycleOperationCoordinator.OperationKind.WORLD_LOAD,
                coordinator.active(LifecycleOperationCoordinator.Domain.WORLD_MUTATION)
                        .orElseThrow()
                        .kind());
        assertEquals("Iris:overworld", YamlConfiguration.loadConfiguration(configuration)
                .getString("worlds.probe.generator"));
        assertThrows(LifecycleOperationCoordinator.BusyException.class,
                () -> coordinator.acquire(
                        LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                        backend.worldKey.toString()));

        backend.loaded = Optional.of(exactWorld);
        creation.complete(exactWorld);
        BukkitWorldReconciler.LoadResult result = load.join();

        assertTrue(result.succeeded());
        assertEquals(BukkitWorldReconciler.ReconciliationStatus.LOADED, result.status());
        assertEquals(BukkitWorldConfiguration.Registration.CREATED, result.registration());
        assertFalse(result.rollbackAttempted());
        assertTrue(coordinator.isIdle());
    }

    @Test
    public void failedCreationRollsBackOnlyNewRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        FakeBackend backend = new FakeBackend();
        backend.creation = CompletableFuture.failedFuture(new IllegalStateException("create failed"));
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler.loadWorld(configuration, backend.worldKey.toString()).join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.CREATE_FAILED, result.status());
        assertEquals(BukkitWorldConfiguration.Registration.CREATED, result.registration());
        assertTrue(result.rollbackAttempted());
        assertTrue(result.rollbackSucceeded());
        assertNull(YamlConfiguration.loadConfiguration(configuration).get("worlds.probe"));
    }

    @Test
    public void failedCreationPreservesPreexistingMatchingRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L);
        FakeBackend backend = new FakeBackend();
        backend.configuredSeed = 1337L;
        backend.creation = CompletableFuture.failedFuture(new IllegalStateException("create failed"));
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler.loadWorld(configuration, backend.worldKey.toString()).join();

        assertEquals(BukkitWorldConfiguration.Registration.UNCHANGED, result.registration());
        assertFalse(result.rollbackAttempted());
        assertEquals("Iris:overworld", YamlConfiguration.loadConfiguration(configuration)
                .getString("worlds.probe.generator"));
        assertEquals(1337L, YamlConfiguration.loadConfiguration(configuration)
                .getLong("worlds.probe.seed"));
    }

    @Test
    public void rollbackDoesNotRemoveARegistrationChangedDuringCreation() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        FakeBackend backend = new FakeBackend();
        CompletableFuture<World> creation = new CompletableFuture<>();
        backend.creation = creation;
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());
        CompletableFuture<BukkitWorldReconciler.LoadResult> load = reconciler
                .loadWorld(configuration, backend.worldKey.toString());
        YamlConfiguration changed = YamlConfiguration.loadConfiguration(configuration);
        changed.set("worlds.probe.generator", "Other:generator");
        changed.save(configuration);

        creation.completeExceptionally(new IllegalStateException("create failed"));
        BukkitWorldReconciler.LoadResult result = load.join();

        assertTrue(result.rollbackAttempted());
        assertFalse(result.rollbackSucceeded());
        assertEquals("Other:generator", YamlConfiguration.loadConfiguration(configuration)
                .getString("worlds.probe.generator"));
    }

    @Test
    public void mismatchedCreatedWorldIsNotReportedAsLoaded() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        FakeBackend backend = new FakeBackend();
        backend.creation = CompletableFuture.completedFuture(world(new NamespacedKey("iris", "other")));
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler.loadWorld(configuration, backend.worldKey.toString()).join();

        assertFalse(result.succeeded());
        assertEquals(BukkitWorldReconciler.ReconciliationStatus.IDENTITY_MISMATCH, result.status());
        assertTrue(result.rollbackSucceeded());
        assertNull(YamlConfiguration.loadConfiguration(configuration).get("worlds.probe"));
    }

    @Test
    public void mismatchedResolvedWorldIsNotReportedAsLoaded() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        FakeBackend backend = new FakeBackend();
        World exactCreatedWorld = world(backend.worldKey);
        backend.creation = CompletableFuture.completedFuture(exactCreatedWorld);
        backend.loaded = Optional.of(world(new NamespacedKey("iris", "other")));
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler
                .loadWorld(configuration, backend.worldKey.toString())
                .join();

        assertFalse(result.succeeded());
        assertEquals(BukkitWorldReconciler.ReconciliationStatus.IDENTITY_MISMATCH, result.status());
        assertTrue(result.rollbackSucceeded());
    }

    @Test
    public void exactNonIrisWorldIsAnIdentityConflict() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        FakeBackend backend = new FakeBackend();
        backend.loaded = Optional.of(world(backend.worldKey));
        backend.irisWorld = false;
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler.loadWorld(configuration, backend.worldKey.toString()).join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.IDENTITY_CONFLICT, result.status());
        assertEquals(0, backend.createCount.get());
        assertTrue(result.rollbackSucceeded());
    }

    @Test
    public void unresolvedDimensionAndBusyLifecycleDoNotTouchConfiguration() throws Exception {
        File unresolvedConfiguration = temporaryFolder.newFile("unresolved.yml");
        FakeBackend unresolvedBackend = new FakeBackend();
        unresolvedBackend.dimensionResolution = BukkitWorldReconciler.DimensionResolution.failed(
                new IllegalStateException("ambiguous"));
        BukkitWorldReconciler unresolved = new BukkitWorldReconciler(unresolvedBackend, coordinator());

        BukkitWorldReconciler.LoadResult unresolvedResult = unresolved
                .loadWorld(unresolvedConfiguration, unresolvedBackend.worldKey.toString())
                .join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.DIMENSION_UNRESOLVED, unresolvedResult.status());
        assertEquals(0L, unresolvedConfiguration.length());

        File busyConfiguration = temporaryFolder.newFile("busy.yml");
        LifecycleOperationCoordinator coordinator = coordinator();
        LifecycleOperationCoordinator.Lease removal = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                "iris:other");
        FakeBackend busyBackend = new FakeBackend();
        BukkitWorldReconciler busy = new BukkitWorldReconciler(busyBackend, coordinator);
        try {
            BukkitWorldReconciler.LoadResult busyResult = busy.loadWorld(busyConfiguration, busyBackend.worldKey.toString()).join();
            assertEquals(BukkitWorldReconciler.ReconciliationStatus.BUSY, busyResult.status());
            assertEquals(0, busyBackend.createCount.get());
            assertEquals(0L, busyConfiguration.length());
        } finally {
            removal.close();
        }
    }

    @Test
    public void pendingRestartRefusesWorldLoadBeforeRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        LifecycleOperationCoordinator coordinator = coordinator();
        assertTrue(coordinator.quiesceForRestart(() -> {
        }));
        FakeBackend backend = new FakeBackend();
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator);

        BukkitWorldReconciler.LoadResult result = reconciler.loadWorld(configuration, backend.worldKey.toString()).join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.BUSY, result.status());
        assertEquals(LifecycleOperationCoordinator.OperationKind.SERVER_RESTART,
                ((LifecycleOperationCoordinator.BusyException) result.failure()).operationKind());
        assertEquals(0, backend.createCount.get());
        assertEquals(0L, configuration.length());
    }

    @Test
    public void pendingStartupValidationRefusesWorldLoadBeforeRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("startup-pending.yml");
        FakeBackend backend = new FakeBackend();
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());
        IrisStartupValidation.begin();

        BukkitWorldReconciler.LoadResult result = reconciler
                .loadWorld(configuration, backend.worldKey.toString())
                .join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.DIMENSION_UNRESOLVED, result.status());
        assertEquals(0, backend.createCount.get());
        assertEquals(0L, configuration.length());
    }

    @Test
    public void invalidDimensionRefusesWorldLoadBeforeRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("invalid-dimension.yml");
        FakeBackend backend = new FakeBackend();
        backend.validationFailure = new IllegalStateException("pack is invalid");
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler
                .loadWorld(configuration, backend.worldKey.toString())
                .join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.DIMENSION_UNRESOLVED, result.status());
        assertEquals(0, backend.createCount.get());
        assertEquals(0L, configuration.length());
    }

    @Test
    public void terminalCreateTimeoutWinsOverLateWorldCompletion() {
        NamespacedKey worldKey = new NamespacedKey("iris", "probe");
        CompletableFuture<World> source = new CompletableFuture<>();
        AtomicInteger timeoutActions = new AtomicInteger();

        CompletableFuture<World> guarded = BukkitWorldReconciler.guardCreateCompletion(
                source,
                worldKey,
                1L,
                timeoutActions::incrementAndGet);

        CompletionException failure = assertThrows(CompletionException.class, guarded::join);
        assertTrue(failure.getCause() instanceof TimeoutException);
        assertEquals(1, timeoutActions.get());
        source.complete(world(worldKey));
        assertTrue(guarded.isCompletedExceptionally());
    }

    @Test
    public void timedOutCreationPreservesNewRegistrationForRestartReconciliation() throws Exception {
        File configuration = temporaryFolder.newFile("timeout.yml");
        FakeBackend backend = new FakeBackend();
        backend.creation = CompletableFuture.failedFuture(new TimeoutException("create timed out"));
        BukkitWorldReconciler reconciler = new BukkitWorldReconciler(backend, coordinator());

        BukkitWorldReconciler.LoadResult result = reconciler
                .loadWorld(configuration, backend.worldKey.toString())
                .join();

        assertEquals(BukkitWorldReconciler.ReconciliationStatus.RESTART_REQUIRED, result.status());
        assertFalse(result.rollbackAttempted());
        assertEquals("Iris:overworld", YamlConfiguration.loadConfiguration(configuration)
                .getString("worlds.probe.generator"));
    }

    private static LifecycleOperationCoordinator coordinator() throws Exception {
        Constructor<LifecycleOperationCoordinator> constructor = LifecycleOperationCoordinator.class
                .getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static World world(NamespacedKey worldKey) {
        return (World) Proxy.newProxyInstance(
                BukkitWorldReconcilerTest.class.getClassLoader(),
                new Class[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getKey" -> worldKey;
                    case "getName" -> worldKey.getKey();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> worldKey.toString();
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        throw new IllegalStateException("Unsupported primitive type: " + type);
    }

    private static final class FakeBackend implements BukkitWorldReconciler.Backend {
        private final NamespacedKey worldKey;
        private final AtomicInteger createCount;
        private CompletableFuture<World> creation;
        private Optional<World> loaded;
        private boolean irisWorld;
        private Long configuredSeed;
        private BukkitWorldReconciler.DimensionResolution dimensionResolution;
        private RuntimeException validationFailure;

        private FakeBackend() {
            worldKey = new NamespacedKey("iris", "probe");
            createCount = new AtomicInteger();
            creation = CompletableFuture.completedFuture(null);
            loaded = Optional.empty();
            irisWorld = true;
            configuredSeed = null;
            dimensionResolution = BukkitWorldReconciler.DimensionResolution.resolved("overworld");
            validationFailure = null;
        }

        @Override
        public Map<String, String> configuredWorlds() {
            return Map.of("probe", "overworld");
        }

        @Override
        public Long configuredSeed(String worldName) {
            return configuredSeed;
        }

        @Override
        public String configuredWorldName(NamespacedKey requestedWorldKey) {
            return requestedWorldKey.getKey();
        }

        @Override
        public NamespacedKey worldKeyFromConfiguration(String configuredWorldName) {
            return worldKey;
        }

        @Override
        public Optional<World> loadedWorld(NamespacedKey requestedWorldKey) {
            return worldKey.equals(requestedWorldKey) ? loaded : Optional.empty();
        }

        @Override
        public CompletableFuture<World> createWorld(NamespacedKey requestedWorldKey, String dimension, Long seed) {
            createCount.incrementAndGet();
            return creation;
        }

        @Override
        public boolean isIrisWorld(World world) {
            return irisWorld;
        }

        @Override
        public BukkitWorldReconciler.DimensionResolution resolveDimension(NamespacedKey requestedWorldKey) {
            return dimensionResolution;
        }

        @Override
        public void requireDimensionLoadable(NamespacedKey requestedWorldKey, String dimension) {
            if (validationFailure != null) {
                throw validationFailure;
            }
        }
    }
}
