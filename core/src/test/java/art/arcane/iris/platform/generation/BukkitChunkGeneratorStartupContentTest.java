package art.arcane.iris.platform.generation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.WorldInfo;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorStartupContentTest {
    @Test(timeout = 1000L)
    public void pendingContentRejectsGenerationWithoutWaitingOnTheServerThread() {
        CompletableFuture<Void> ready = new CompletableFuture<>();

        assertThrows(IllegalStateException.class,
                () -> BukkitChunkGenerator.requireStartupContentReady(ready, true));
        assertFalse(ready.isDone());
        ready.complete(null);
        assertThrows(IllegalStateException.class,
                () -> BukkitChunkGenerator.requireStartupContentReady(ready, true));
        BukkitChunkGenerator.requireStartupContentReady(ready, false);
    }

    @Test
    public void failedContentNeverAdmitsGeneration() {
        IllegalStateException failure = new IllegalStateException("Required provider is unavailable");
        CompletableFuture<Void> ready = CompletableFuture.failedFuture(failure);

        CompletionException rejected = assertThrows(CompletionException.class,
                () -> BukkitChunkGenerator.requireStartupContentReady(ready, false));

        assertSame(failure, rejected.getCause());
    }

    @Test
    public void failedInitializationRejectsTerrainEvenAfterContentBecomesReady() throws Exception {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        IllegalStateException failure = new IllegalStateException("Generator injection failed");
        setField(generator, "startupContentReady", CompletableFuture.completedFuture(null));
        setField(generator, "startupReady", CompletableFuture.failedFuture(failure));

        CompletionException rejected = assertThrows(CompletionException.class,
                () -> generator.getBaseHeight(mock(WorldInfo.class), new Random(1L), 0, 0, HeightMap.WORLD_SURFACE));

        assertSame(failure, rejected.getCause());
        verify(generator, never()).getTarget();
    }

    @Test
    public void pendingWorldExposesItsStemTargetAndFailsReadinessOnProviderTimeout() throws Exception {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        IrisWorld irisWorld = mock(IrisWorld.class);
        World world = mock(World.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisData data = mock(IrisData.class);
        VolmitPlugin plugin = mock(VolmitPlugin.class);
        CompletableFuture<Void> contentReady = new CompletableFuture<>();
        List<Runnable> tasks = new ArrayList<>();
        when(irisWorld.identity()).thenReturn("iris:test");
        when(irisWorld.name()).thenReturn("world_iris_test");
        when(world.getGenerator()).thenReturn(generator);
        when(target.getData()).thenReturn(data);
        setField(generator, "world", irisWorld);
        setField(generator, "setup", new AtomicBoolean());
        setField(generator, "lock", new ReentrantLock());
        setField(generator, "spawnChunks", new CompletableFuture<Integer>());
        setField(generator, "initialSpawnReady", new CompletableFuture<Void>());
        generator.deferStartup(target, contentReady);

        assertSame(target, generator.getTarget());
        generator.touch(world);
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);
             MockedStatic<WorldIdentity> identity = mockStatic(WorldIdentity.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            platform.when(BukkitPlatform::volmitPlugin).thenReturn(plugin);
            identity.when(() -> WorldIdentity.key(world)).thenReturn(NamespacedKey.fromString("iris:test"));
            scheduling.when(() -> J.s(any(Runnable.class))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0, Runnable.class));
                return null;
            });

            generator.onWorldInit(new WorldInitEvent(world));
            assertTrue(tasks.isEmpty());
            TimeoutException failure = new TimeoutException("Provider startup expired");
            contentReady.completeExceptionally(failure);
            assertFalse(generator.getStartupReady().isDone());
            assertEquals(1, tasks.size());
            tasks.removeFirst().run();

            assertTrue(generator.getStartupReady().isCompletedExceptionally());
            assertTrue(generator.getInitialSpawnReady().isCompletedExceptionally());
            assertSame(failure, generator.getInitializationFailure());
            generator.touch(world);
            verify(data).close();
            bukkit.verify(Bukkit::shutdown);
        }
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }
}
