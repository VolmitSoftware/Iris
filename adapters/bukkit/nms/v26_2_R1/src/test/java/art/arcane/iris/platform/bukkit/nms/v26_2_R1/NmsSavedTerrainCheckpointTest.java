package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.world.history.SavedTerrainChunk;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

public class NmsSavedTerrainCheckpointTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void checkpointWaitsForDirtyAndUnloadedChunkWritesBeforeFlushAndVerification() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        Path root = Path.of("checkpoint-world");
        byte[] receipt = new byte[]{1, 2, 3};
        NmsSavedTerrainCapture.Checkpoint saved = new NmsSavedTerrainCapture.Checkpoint(new ChunkPos(4, -5),
                new NmsSavedTerrainCapture.Verification("minecraft:full", receipt, 7L));
        NmsSavedTerrainCapture.Checkpoint unloaded = NmsSavedTerrainCapture.checkpoint(-8, 9, null);
        Set<ChunkPos> completed = new HashSet<>();
        AtomicBoolean flushed = new AtomicBoolean();
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class);
             MockedStatic<SavedTerrainChunk> terrain = mockStatic(SavedTerrainChunk.class)) {
            io.when(() -> MoonriseRegionFileIO.getPriority(level, 4, -5, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        completed.add(saved.chunk());
                        return Priority.COMPLETING;
                    });
            io.when(() -> MoonriseRegionFileIO.getPriority(level, -8, 9, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        completed.add(unloaded.chunk());
                        return Priority.COMPLETING;
                    });
            io.when(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        assertEquals(Set.of(saved.chunk(), unloaded.chunk()), completed);
                        flushed.set(true);
                        return null;
                    });
            terrain.when(() -> SavedTerrainChunk.verifyCheckpoint(root, 4, -5, "minecraft:full", receipt, 7L))
                    .thenAnswer(invocation -> {
                        assertTrue(flushed.get());
                        return null;
                    });

            NmsSavedTerrainCapture.finishCheckpoint(level, root, List.of(saved, unloaded));

            terrain.verify(() -> SavedTerrainChunk.verifyCheckpoint(root, 4, -5, "minecraft:full", receipt, 7L));
            io.verify(() -> MoonriseRegionFileIO.flush(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), never());
            io.verify(() -> MoonriseRegionFileIO.getPriority(level, 100, 100, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), never());
            terrain.verifyNoMoreInteractions();
        }
    }

    @Test
    public void storageFlushFailureCannotCompleteTheCheckpoint() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        IOException failure = new IOException("storage flush failed");
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class);
             MockedStatic<SavedTerrainChunk> terrain = mockStatic(SavedTerrainChunk.class)) {
            io.when(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenThrow(failure);

            assertSame(failure, assertThrows(IOException.class,
                    () -> NmsSavedTerrainCapture.finishCheckpoint(level, Path.of("checkpoint-world"), List.of())));
            terrain.verifyNoInteractions();
        }
    }

    @Test
    public void persistedReceiptMismatchCannotCompleteTheCheckpoint() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        Path root = Path.of("checkpoint-world");
        byte[] receipt = new byte[]{4, 5};
        IOException failure = new IOException("saved terrain receipt mismatch");
        NmsSavedTerrainCapture.Checkpoint saved = new NmsSavedTerrainCapture.Checkpoint(new ChunkPos(1, 2),
                new NmsSavedTerrainCapture.Verification("minecraft:noise", receipt, 3L));
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class);
             MockedStatic<SavedTerrainChunk> terrain = mockStatic(SavedTerrainChunk.class)) {
            io.when(() -> MoonriseRegionFileIO.getPriority(level, 1, 2, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenReturn(Priority.COMPLETING);
            terrain.when(() -> SavedTerrainChunk.verifyCheckpoint(root, 1, 2, "minecraft:noise", receipt, 3L))
                    .thenThrow(failure);

            assertSame(failure, assertThrows(IOException.class,
                    () -> NmsSavedTerrainCapture.finishCheckpoint(level, root, List.of(saved))));
            io.verify(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA));
        }
    }
}
