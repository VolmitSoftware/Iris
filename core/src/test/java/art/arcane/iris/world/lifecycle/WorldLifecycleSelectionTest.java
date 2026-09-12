package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisStartupValidation;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WorldLifecycleSelectionTest {
    @After
    public void disableStartupValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void studioSelectsPaperLikeBackendOnPaper() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.PAPER, false, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void studioSelectsPaperLikeBackendOnPurpur() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.PURPUR, false, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void studioSelectsPaperLikeBackendOnCanvas() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.CANVAS, true, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void studioSelectsPaperLikeBackendOnFolia() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.FOLIA, true, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void studioSelectsBukkitBackendOnSpigot() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.SPIGOT, false, false, false));
        WorldLifecycleRequest request = new WorldLifecycleRequest("studio", NamespacedKey.minecraft("studio"), World.Environment.NORMAL, null, null, null, true, false, 1337L, true, false, WorldLifecycleCaller.STUDIO);

        assertEquals("bukkit_public", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void persistentCreatePrefersBukkitBackendOnPaperLikeServers() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.PURPUR, false, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("persistent", NamespacedKey.minecraft("persistent"), World.Environment.NORMAL, null, null, null, true, false, 1337L, false, false, WorldLifecycleCaller.CREATE);

        assertEquals("bukkit_public", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void persistentCreateSelectsPaperLikeBackendOnFolia() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.FOLIA, true, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("persistent", NamespacedKey.minecraft("persistent"), World.Environment.NORMAL, null, null, null, true, false, 1337L, false, false, WorldLifecycleCaller.CREATE);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void persistentCreateSelectsPaperLikeBackendOnCanvas() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.CANVAS, true, false, true));
        WorldLifecycleRequest request = new WorldLifecycleRequest("persistent", NamespacedKey.minecraft("persistent"), World.Environment.NORMAL, null, null, null, true, false, 1337L, false, false, WorldLifecycleCaller.CREATE);

        assertEquals("paper_like_runtime", service.selectCreateBackend(request).backendName());
    }

    @Test
    public void persistentCreateFailsClosedOnFoliaWithoutRuntime() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.FOLIA, false, false, false));
        WorldLifecycleRequest request = new WorldLifecycleRequest("persistent", NamespacedKey.minecraft("persistent"), World.Environment.NORMAL, null, null, null, true, false, 1337L, false, false, WorldLifecycleCaller.CREATE);

        assertThrows(IllegalStateException.class, () -> service.selectCreateBackend(request));
    }

    @Test
    public void unloadUsesRememberedBackendFamily() {
        WorldLifecycleService service = new WorldLifecycleService(CapabilitySnapshotFixtures.forTesting(ServerFamily.PURPUR, false, false, true));

        service.rememberBackend(NamespacedKey.minecraft("studio"), "paper_like_runtime");
        assertEquals("paper_like_runtime", service.selectUnloadBackend("minecraft:studio").backendName());
    }

    @Test
    public void pendingStartupValidationStopsCreateBeforeBackendSelection() {
        CountingBackend selected = new CountingBackend("selected", true);
        CountingBackend inactive = new CountingBackend("inactive", false);
        WorldLifecycleService service = new WorldLifecycleService(
                CapabilitySnapshotFixtures.forTesting(ServerFamily.PAPER, false, false, true),
                selected,
                inactive,
                inactive);
        WorldLifecycleRequest request = new WorldLifecycleRequest(
                "blocked",
                NamespacedKey.minecraft("blocked"),
                World.Environment.NORMAL,
                null,
                null,
                null,
                true,
                false,
                1337L,
                false,
                false,
                WorldLifecycleCaller.CREATE);
        IrisStartupValidation.begin();

        CompletionException failure = assertThrows(CompletionException.class, () -> service.create(request).join());

        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals(0, selected.createCount.get());
    }

    private static final class CountingBackend implements WorldLifecycleBackend {
        private final String name;
        private final boolean supported;
        private final AtomicInteger createCount;

        private CountingBackend(String name, boolean supported) {
            this.name = name;
            this.supported = supported;
            this.createCount = new AtomicInteger();
        }

        @Override
        public boolean supports(WorldLifecycleRequest request, CapabilitySnapshot capabilities) {
            return supported;
        }

        @Override
        public CompletableFuture<World> create(WorldLifecycleRequest request) {
            createCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> unloadAsync(World world, boolean save) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public String backendName() {
            return name;
        }
    }
}
