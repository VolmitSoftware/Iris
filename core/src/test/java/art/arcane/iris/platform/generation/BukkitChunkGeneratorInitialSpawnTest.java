package art.arcane.iris.platform.generation;

import art.arcane.iris.world.task.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorInitialSpawnTest {
    @Test
    public void spawnReadinessWaitsForChunkAndRegionPlacement() throws Exception {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        BukkitChunkGenerator generator = generatorWithReadiness(readiness);
        World world = world("spawn-success");
        Chunk chunk = chunk(world);
        CompletableFuture<Chunk> chunkFuture = new CompletableFuture<>();
        AtomicReference<Runnable> regionTask = new AtomicReference<>();
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(chunkFuture);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
        when(world.getHighestBlockYAt(any(Location.class))).thenReturn(70);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runRegionFuture(
                            any(World.class),
                            anyInt(),
                            anyInt(),
                            any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        regionTask.set(invocation.getArgument(3, Runnable.class));
                        return CompletableFuture.completedFuture(null);
                    });

            invokeUpdateSpawnLocation(generator, world);
            assertFalse(readiness.isDone());

            chunkFuture.complete(chunk);
            assertNotNull(regionTask.get());
            assertFalse(readiness.isDone());
            regionTask.get().run();
            readiness.get(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void failedChunkRequestFailsSpawnReadiness() throws Exception {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        BukkitChunkGenerator generator = generatorWithReadiness(readiness);
        World world = world("spawn-chunk-failure");
        IllegalStateException failure = new IllegalStateException("chunk failed");
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(CompletableFuture.failedFuture(failure));

        invokeUpdateSpawnLocation(generator, world);

        assertSame(failure, awaitFailure(readiness));
    }

    @Test
    public void nullChunkFailsSpawnReadiness() throws Exception {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        BukkitChunkGenerator generator = generatorWithReadiness(readiness);
        World world = world("spawn-null-chunk");
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(CompletableFuture.completedFuture(null));

        invokeUpdateSpawnLocation(generator, world);

        Throwable failure = awaitFailure(readiness);
        assertTrue(failure.getMessage().contains("Initial spawn preparation failed"));
        assertNotNull(failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("completed without a chunk"));
    }

    @Test
    public void rejectedRegionScheduleFailsSpawnReadiness() throws Exception {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        BukkitChunkGenerator generator = generatorWithReadiness(readiness);
        World world = world("spawn-schedule-failure");
        Chunk chunk = chunk(world);
        IllegalStateException failure = new IllegalStateException("region rejected");
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(CompletableFuture.completedFuture(chunk));

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runRegionFuture(
                            any(World.class),
                            anyInt(),
                            anyInt(),
                            any(Runnable.class)))
                    .thenReturn(CompletableFuture.failedFuture(failure));

            invokeUpdateSpawnLocation(generator, world);
        }

        assertSame(failure, awaitFailure(readiness));
    }

    @Test
    public void failedRegionPlacementFailsSpawnReadiness() throws Exception {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        BukkitChunkGenerator generator = generatorWithReadiness(readiness);
        World world = world("spawn-placement-failure");
        Chunk chunk = chunk(world);
        IllegalStateException failure = new IllegalStateException("spawn placement failed");
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(CompletableFuture.completedFuture(chunk));
        when(world.getSpawnLocation()).thenThrow(failure);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runRegionFuture(
                            any(World.class),
                            anyInt(),
                            anyInt(),
                            any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.getArgument(3, Runnable.class).run();
                        return CompletableFuture.completedFuture(null);
                    });

            invokeUpdateSpawnLocation(generator, world);
        }

        assertSame(failure, awaitFailure(readiness));
    }

    private static BukkitChunkGenerator generatorWithReadiness(
            CompletableFuture<Void> readiness
    ) throws ReflectiveOperationException {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        Field readinessField = BukkitChunkGenerator.class.getDeclaredField("initialSpawnReady");
        readinessField.setAccessible(true);
        readinessField.set(generator, readiness);
        return generator;
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    private static Chunk chunk(World world) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        return chunk;
    }

    private static void invokeUpdateSpawnLocation(
            BukkitChunkGenerator generator,
            World world
    ) throws ReflectiveOperationException {
        Method updateSpawnLocation = BukkitChunkGenerator.class.getDeclaredMethod(
                "updateSpawnLocation",
                World.class);
        updateSpawnLocation.setAccessible(true);
        updateSpawnLocation.invoke(generator, world);
    }

    private static Throwable awaitFailure(CompletableFuture<Void> readiness) throws Exception {
        try {
            readiness.get(5L, TimeUnit.SECONDS);
            fail("Expected initial spawn readiness to fail.");
            throw new AssertionError("unreachable");
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }
}
