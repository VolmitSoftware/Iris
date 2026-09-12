package art.arcane.iris.world.runtime;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldRuntimeControlBackendUrgencyTest {
    @Test
    public void urgentRequestsUsePaperUrgencyFlag() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        CompletableFuture<Chunk> expected = CompletableFuture.completedFuture(chunk);
        when(world.getChunkAtAsync(4, -3, true, true)).thenReturn(expected);
        RecordingBackend backend = new RecordingBackend();

        CompletableFuture<Chunk> actual = backend.requestChunkAsync(world, 4, -3, true, true);

        assertSame(expected, actual);
        assertEquals(0, backend.regularRequests);
        verify(world).getChunkAtAsync(4, -3, true, true);
    }

    @Test
    public void backgroundRequestsRemainNonUrgent() {
        World world = mock(World.class);
        RecordingBackend backend = new RecordingBackend();

        backend.requestChunkAsync(world, 4, -3, true, false);

        assertEquals(1, backend.regularRequests);
    }

    private static final class RecordingBackend implements WorldRuntimeControlBackend {
        private int regularRequests;

        @Override
        public String backendName() {
            return "recording";
        }

        @Override
        public String describeCapabilities() {
            return "recording";
        }

        @Override
        public OptionalLong readDayTime(World world) {
            return OptionalLong.empty();
        }

        @Override
        public boolean writeDayTime(World world, long dayTime) {
            return false;
        }

        @Override
        public void syncTime(World world) {
        }

        @Override
        public CompletableFuture<Chunk> requestChunkAsync(
                World world,
                int chunkX,
                int chunkZ,
                boolean generate
        ) {
            regularRequests++;
            return CompletableFuture.completedFuture(null);
        }
    }
}
