package art.arcane.iris.world;

import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCreatorInitialSpawnTest {
    @Test
    public void worldCreationWaitsForInitialSpawnCompletion() throws Exception {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        CompletableFuture<Void> initialSpawn = new CompletableFuture<>();
        CountDownLatch waitStarted = new CountDownLatch(1);
        when(generator.getInitialSpawnReady()).thenAnswer(invocation -> {
            waitStarted.countDown();
            return initialSpawn;
        });

        CompletableFuture<Void> wait = CompletableFuture.runAsync(() -> {
            try {
                IrisCreator.awaitInitialSpawnPreparation(generator, "test-world");
            } catch (Throwable failure) {
                throw new RuntimeException(failure);
            }
        });

        if (!waitStarted.await(5L, TimeUnit.SECONDS)) {
            fail("Initial spawn wait did not start.");
        }
        if (wait.isDone()) {
            fail("Initial spawn wait completed before the chunk was ready.");
        }
        initialSpawn.complete(null);
        wait.get(5L, TimeUnit.SECONDS);
    }

    @Test
    public void initialSpawnFailureFailsWorldCreation() throws Exception {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        IllegalStateException failure = new IllegalStateException("spawn failed");
        when(generator.getInitialSpawnReady()).thenReturn(CompletableFuture.failedFuture(failure));

        try {
            IrisCreator.awaitInitialSpawnPreparation(generator, "test-world");
            fail("Expected initial spawn failure.");
        } catch (ExecutionException exception) {
            assertSame(failure, exception.getCause());
        }
    }

    @Test
    public void missingInitialSpawnFutureFailsWorldCreation() {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        when(generator.getInitialSpawnReady()).thenReturn(null);

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> IrisCreator.awaitInitialSpawnPreparation(generator, "test-world"));

        assertEquals("Initial spawn preparation future", failure.getMessage());
    }
}
