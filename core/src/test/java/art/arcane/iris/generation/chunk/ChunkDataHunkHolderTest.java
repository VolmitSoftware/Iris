package art.arcane.iris.generation.chunk;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkDataHunkHolderTest {
    @Test
    public void columnTopTracksTheHighestStoredValueAndIgnoresNulls() {
        ChunkDataHunkHolder holder = holder(64);
        NativeBlockState stone = mock(NativeBlockState.class);

        assertEquals(-1, holder.highestStoredY(3, 7));
        holder.setRaw(3, 40, 7, null);
        assertEquals(-1, holder.highestStoredY(3, 7));
        holder.setRaw(3, 12, 7, stone);
        holder.setRaw(3, 30, 7, stone);
        holder.setRaw(3, 5, 7, stone);

        assertEquals(30, holder.highestStoredY(3, 7));
        assertEquals(-1, holder.highestStoredY(7, 3));
        assertSame(stone, holder.getStoredRaw(3, 30, 7));
        assertNull(holder.getStoredRaw(3, 31, 7));
        assertEquals(30, ColumnExtent.highestStoredY(new ColumnExtentListeningHunk<>(holder, (x, y, z, state) -> {
        }), 3, 7));
    }

    @Test
    public void cellsKeepTheZMajorLayout() {
        ChunkDataHunkHolder holder = holder(32);
        List<NativeBlockState> states = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 32; y++) {
                    NativeBlockState state = mock(NativeBlockState.class);
                    states.add(state);
                    holder.setRaw(x, y, z, state);
                }
            }
        }
        int index = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 32; y++) {
                    assertSame(states.get(index++), holder.getStoredRaw(x, y, z));
                }
            }
        }
    }

    @Test
    public void concurrentWritersNeverLoseTheColumnMaximum() throws InterruptedException {
        for (int trial = 0; trial < 20; trial++) {
            ChunkDataHunkHolder holder = holder(768);
            NativeBlockState stone = mock(NativeBlockState.class);
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> writers = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int offset = thread;
                Thread writer = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int y = offset; y < 768; y += threads) {
                        holder.setRaw(9, y, 4, stone);
                    }
                });
                writer.start();
                writers.add(writer);
            }
            start.countDown();
            for (Thread writer : writers) {
                writer.join();
            }
            assertEquals(767, holder.highestStoredY(9, 4));
        }
    }

    private static ChunkDataHunkHolder holder(int height) {
        ChunkGenerator.ChunkData chunk = mock(ChunkGenerator.ChunkData.class);
        when(chunk.getMinHeight()).thenReturn(0);
        when(chunk.getMaxHeight()).thenReturn(height);
        return new ChunkDataHunkHolder(chunk);
    }
}
