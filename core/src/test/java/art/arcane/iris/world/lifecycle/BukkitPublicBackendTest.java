package art.arcane.iris.world.lifecycle;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BukkitPublicBackendTest {
    @Test
    public void injectionFailureCompletesExceptionallyAndClearsStaging() {
        String worldName = "injection_failure";
        NamespacedKey worldKey = new NamespacedKey("iris", worldName);
        ChunkGenerator generator = mock(ChunkGenerator.class);
        BiomeProvider biomeProvider = mock(BiomeProvider.class);
        INMSBinding binding = mock(INMSBinding.class);
        IllegalStateException injectionFailure = new IllegalStateException("injection failed");
        WorldLifecycleRequest request = new WorldLifecycleRequest(
                worldName, worldKey, World.Environment.NORMAL, generator, biomeProvider,
                WorldType.NORMAL, true, false, 123L, false, false, WorldLifecycleCaller.CREATE);
        BukkitPublicBackend backend = new BukkitPublicBackend(
                CapabilitySnapshotFixtures.forTesting(ServerFamily.PAPER, false, false, false));

        doAnswer(invocation -> {
            assertSame(generator, WorldLifecycleStaging.peekStemGenerator(worldName));
            throw injectionFailure;
        }).when(binding).ensureServerLevelInjection();

        try (MockedStatic<WorldIdentity> identity = mockStatic(WorldIdentity.class);
             MockedStatic<INMS> nms = mockStatic(INMS.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            identity.when(() -> WorldIdentity.resolve(worldKey)).thenReturn(Optional.empty());
            nms.when(INMS::get).thenReturn(binding);

            CompletableFuture<World> result = backend.create(request);

            assertTrue(result.isCompletedExceptionally());
            CompletionException failure = assertThrows(CompletionException.class, result::join);
            assertSame(injectionFailure, failure.getCause());
            verify(binding).ensureServerLevelInjection();
            bukkit.verify(() -> Bukkit.createWorld(any(WorldCreator.class)), never());
            assertNull(WorldLifecycleStaging.consumeGenerator(worldName));
            assertNull(WorldLifecycleStaging.consumeBiomeProvider(worldName));
            assertNull(WorldLifecycleStaging.consumeStemGenerator(worldName));
        } finally {
            WorldLifecycleStaging.clearAll(worldName);
        }
    }
}
