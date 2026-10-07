package art.arcane.iris.world;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class IrisCreatorRollbackTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void failedStagedGeneratorClosePreservesWorldFilesForStartupDeletion() throws Exception {
        Path storage = temporary.newFolder("failed-world").toPath();
        Path region = storage.resolve("region.mca");
        byte[] bytes = new byte[]{4, 9, 2};
        Files.write(region, bytes);
        IOException closeFailure = new IOException("generator close failed");
        IllegalStateException creationFailure = new IllegalStateException("creation failed");
        WorldDeletionQueue queue = mock(WorldDeletionQueue.class);

        rollback(storage, CompletableFuture.failedFuture(closeFailure), creationFailure, queue);

        assertArrayEquals(bytes, Files.readAllBytes(region));
        assertSame(closeFailure, creationFailure.getSuppressed()[0]);
        verify(queue).queueExactForStartupDeletion(List.of("rollback_test"));
    }

    @Test
    public void successfulStagedGeneratorCloseStillDeletesPartialWorldImmediately() throws Exception {
        Path storage = temporary.newFolder("closed-world").toPath();
        Files.write(storage.resolve("region.mca"), new byte[]{4, 9, 2});
        WorldDeletionQueue queue = mock(WorldDeletionQueue.class);

        rollback(storage, CompletableFuture.completedFuture(null), new IllegalStateException("creation failed"), queue);

        assertFalse(Files.exists(storage));
        verifyNoInteractions(queue);
    }

    private static void rollback(Path storage, CompletableFuture<Void> close, Throwable failure,
                                 WorldDeletionQueue queue) throws Exception {
        NamespacedKey key = new NamespacedKey("iris", "rollback_test");
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        IrisWorlds worlds = mock(IrisWorlds.class);
        doReturn(close).when(generator).closeAsync();
        try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class);
             MockedStatic<WorldIdentity> identity = mockStatic(WorldIdentity.class);
             MockedStatic<IrisWorlds> registry = mockStatic(IrisWorlds.class);
             MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            identity.when(() -> WorldIdentity.resolve(key)).thenReturn(Optional.empty());
            registry.when(IrisWorlds::get).thenReturn(worlds);
            services.when(() -> IrisServices.get(WorldDeletionQueue.class)).thenReturn(queue);
            IrisCreator creator = new IrisCreator().name(key.getKey());
            Method method = IrisCreator.class.getDeclaredMethod("rollbackWorldCreation", NamespacedKey.class,
                    World.class, PlatformChunkGenerator.class, File.class, boolean.class, Throwable.class);
            method.setAccessible(true);
            method.invoke(creator, key, null, generator, storage.toFile(), false, failure);
        }
        verify(generator).closeAsync();
        verify(worlds).remove(key.toString());
    }
}
