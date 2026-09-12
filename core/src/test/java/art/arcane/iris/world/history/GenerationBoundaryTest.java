package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationBoundaryTest {
    @Test
    public void freezesHistoricalOwnershipAndDeduplicatesCoordinates() {
        ArrayList<GenerationBoundary.ChunkCoordinate> source = new ArrayList<>(List.of(
                new GenerationBoundary.ChunkCoordinate(2, -3),
                new GenerationBoundary.ChunkCoordinate(2, -3)
        ));

        GenerationBoundary boundary = GenerationBoundary.freeze("activation-4-frontier", source);
        source.clear();
        source.add(new GenerationBoundary.ChunkCoordinate(9, 9));

        assertEquals(1, boundary.historicalChunkCount());
        assertTrue(boundary.isHistoricalChunk(2, -3));
        assertFalse(boundary.isHistoricalChunk(9, 9));

        long[] exported = boundary.packedHistoricalChunks();
        exported[0] = GenerationBoundary.packChunk(9, 9);
        assertTrue(boundary.isHistoricalChunk(2, -3));
    }

    @Test
    public void mapsNegativeBlocksWithFloorDivision() {
        GenerationBoundary boundary = GenerationBoundary.freeze("negative-frontier", List.of(
                new GenerationBoundary.ChunkCoordinate(-1, -1)
        ));

        assertTrue(boundary.isHistoricalBlock(-1, -1));
        assertTrue(boundary.isHistoricalBlock(-16, -16));
        assertFalse(boundary.isHistoricalBlock(-17, -16));
        assertEquals(1D, boundary.distanceToHistoricalChunks(-17, -16, 64), 0D);
        assertEquals(1D, boundary.distanceToHistoricalChunks(0, -1, 64), 0D);
    }

    @Test
    public void measuresIrregularAndEnclosedFrontiersInBlockSpace() {
        GenerationBoundary boundary = GenerationBoundary.freeze("enclosed-frontier", List.of(
                new GenerationBoundary.ChunkCoordinate(-1, -1),
                new GenerationBoundary.ChunkCoordinate(0, -1),
                new GenerationBoundary.ChunkCoordinate(1, -1),
                new GenerationBoundary.ChunkCoordinate(-1, 0),
                new GenerationBoundary.ChunkCoordinate(1, 0),
                new GenerationBoundary.ChunkCoordinate(-1, 1),
                new GenerationBoundary.ChunkCoordinate(0, 1),
                new GenerationBoundary.ChunkCoordinate(1, 1)
        ));

        assertFalse(boundary.isHistoricalChunk(0, 0));
        assertEquals(8D, boundary.distanceToHistoricalChunks(8, 8, 64), 0D);
        assertEquals(4D, boundary.distanceToHistoricalChunks(100, 8, 4), 0D);

        GenerationBoundary corner = GenerationBoundary.freeze("corner", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        assertEquals(Math.sqrt(2D), corner.distanceToHistoricalChunks(16, 16, 64), 0D);
    }

    @Test
    public void packedCoordinatesRoundTripAcrossIntegerRange() {
        long[] packed = {
                GenerationBoundary.packChunk(Integer.MIN_VALUE, Integer.MAX_VALUE),
                GenerationBoundary.packChunk(-1, 0),
                GenerationBoundary.packChunk(0, -1),
                GenerationBoundary.packChunk(Integer.MAX_VALUE, Integer.MIN_VALUE)
        };

        GenerationBoundary boundary = GenerationBoundary.freezePacked("packed-frontier", packed);

        assertArrayEquals(packed, boundary.packedHistoricalChunks());
        assertEquals(new GenerationBoundary.ChunkCoordinate(Integer.MIN_VALUE, Integer.MAX_VALUE),
                GenerationBoundary.unpackChunk(packed[0]));
        assertEquals(new GenerationBoundary.ChunkCoordinate(Integer.MAX_VALUE, Integer.MIN_VALUE),
                GenerationBoundary.unpackChunk(packed[3]));
    }

    @Test
    public void samplesEveryExposedEdgeBlockWithoutDuplicatingCorners() {
        GenerationBoundary isolated = GenerationBoundary.freeze("isolated", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0),
                new GenerationBoundary.ChunkCoordinate(-1, -1)
        ));

        assertEquals(120, isolated.exposedBlockColumns().size());
        assertTrue(isolated.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(-16, -16)));
        assertTrue(isolated.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(-1, -1)));
        assertTrue(isolated.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(0, 0)));
        assertTrue(isolated.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(15, 15)));
        assertFalse(isolated.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(7, 7)));

        GenerationBoundary adjacent = GenerationBoundary.freeze("adjacent", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0),
                new GenerationBoundary.ChunkCoordinate(1, 0)
        ));
        assertEquals(92, adjacent.exposedBlockColumns().size());
        assertFalse(adjacent.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(15, 8)));
        assertFalse(adjacent.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(16, 8)));
        assertTrue(adjacent.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(0, 8)));
        assertTrue(adjacent.exposedBlockColumns().contains(new GenerationBoundary.BlockColumn(31, 8)));
    }

    @Test
    public void fragmentedExplorationStreamsBeyondTheFormerMonolithicSnapshotLimit() {
        int chunkCount = 70_000;
        ArrayList<GenerationBoundary.ChunkCoordinate> chunks = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            chunks.add(new GenerationBoundary.ChunkCoordinate(index * 2, 0));
        }

        GenerationBoundary boundary = GenerationBoundary.freeze("fragmented", chunks);

        assertEquals(4_200_000L, boundary.exposedBlockColumnCount());
    }

    @Test
    public void rejectsInvalidBoundaryInputs() {
        assertThrows(NullPointerException.class, () -> GenerationBoundary.freeze("boundary", null));
        ArrayList<GenerationBoundary.ChunkCoordinate> withNull = new ArrayList<>();
        withNull.add(new GenerationBoundary.ChunkCoordinate(0, 0));
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> GenerationBoundary.freeze("boundary", withNull));
        assertThrows(IllegalArgumentException.class, () -> GenerationBoundary.freeze(" ", List.of()));

        GenerationBoundary boundary = GenerationBoundary.freeze("boundary", List.of());
        assertThrows(IllegalArgumentException.class,
                () -> boundary.distanceToHistoricalChunks(0, 0, 0));
    }
}
