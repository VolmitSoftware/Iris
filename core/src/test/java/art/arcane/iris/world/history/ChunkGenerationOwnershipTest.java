package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ChunkGenerationOwnershipTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void unassignedChunksUseTheSuppliedCurrentActivation() throws Exception {
        Path directory = temporaryFolder.newFolder("fallback").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);

        assertEquals(4L, ownership.resolve(0, 0, 4L));
        assertEquals(9L, ownership.resolve(0, 0, 9L));
        assertFalse(ownership.isExplicitlyAssigned(0, 0));
        assertThrows(IllegalStateException.class, () -> ownership.explicitActivation(0, 0));
    }

    @Test
    public void boundedQueryOnlyVisitsAssignedChunksInsideTheSquare() throws Exception {
        Path directory = temporaryFolder.newFolder("bounded-query").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(-33, 0, 1L);
        ownership.assign(-32, 1, 2L);
        ownership.assign(-31, 2, 3L);
        ownership.assign(8192, 8192, 4L);
        ownership.persist();
        ChunkGenerationOwnership loaded = ChunkGenerationOwnership.load(directory);
        Set<Long> visited = new HashSet<>();

        assertFalse(loaded.anyMatchingInSquare(-33, 0, 1, (x, z, activation) -> {
            visited.add(ChunkGenerationOwnership.packChunk(x, z));
            return false;
        }));
        assertEquals(Set.of(ChunkGenerationOwnership.packChunk(-33, 0),
                ChunkGenerationOwnership.packChunk(-32, 1)), visited);
        assertTrue(loaded.anyMatchingInSquare(-32, 1, 0, (x, z, activation) -> activation == 2L));
        assertFalse(loaded.anyMatchingInSquare(0, 0, 16, (x, z, activation) -> {
            throw new AssertionError("Unassigned chunks must not be evaluated.");
        }));
        assertThrows(IllegalArgumentException.class,
                () -> loaded.anyMatchingInSquare(0, 0, -1, (x, z, activation) -> true));
    }

    @Test
    public void sparseWholeCoordinateQueryUsesTheCatalogWithoutOverflow() throws Exception {
        Path directory = temporaryFolder.newFolder("whole-coordinate-query").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(Integer.MAX_VALUE, Integer.MAX_VALUE, 1L);
        ownership.assign(Integer.MIN_VALUE, Integer.MIN_VALUE, 2L);

        assertTrue(ownership.anyMatchingInSquare(0, 0, Integer.MAX_VALUE,
                (x, z, activation) -> activation == 1L));
        assertFalse(ownership.anyMatchingInSquare(0, 0, Integer.MAX_VALUE,
                (x, z, activation) -> activation == 2L));
        assertTrue(ownership.anyMatchingInSquare(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
                (x, z, activation) -> activation == 2L));
    }

    @Test
    public void assignmentsPersistAcrossEverySignedRegionBoundary() throws Exception {
        Path directory = temporaryFolder.newFolder("boundaries").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        int[][] coordinates = {
                {-33, -33},
                {-32, -32},
                {-1, -1},
                {0, 0},
                {31, 31},
                {32, 32}
        };
        for (int index = 0; index < coordinates.length; index++) {
            ownership.assign(coordinates[index][0], coordinates[index][1], index + 1L);
        }

        assertEquals(4, ownership.persist());
        assertEquals(0, ownership.persist());
        ChunkGenerationOwnership loaded = ChunkGenerationOwnership.load(directory);

        assertEquals(coordinates.length, loaded.explicitChunkCount());
        for (int index = 0; index < coordinates.length; index++) {
            int chunkX = coordinates[index][0];
            int chunkZ = coordinates[index][1];
            assertEquals(index + 1L, loaded.resolve(chunkX, chunkZ, 99L));
            assertTrue(loaded.isExplicitlyAssigned(chunkX, chunkZ));
        }
    }

    @Test
    public void assignmentCannotReplaceHistoricalOwnership() throws Exception {
        Path directory = temporaryFolder.newFolder("immutable").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);

        assertTrue(ownership.assign(8, -2, 41L));
        assertFalse(ownership.assign(8, -2, 41L));
        assertThrows(IllegalStateException.class, () -> ownership.assign(8, -2, 42L));
        assertEquals(41L, ownership.explicitActivation(8, -2));
    }

    @Test
    public void bulkConflictDoesNotPartiallyAssignInventory() throws Exception {
        Path directory = temporaryFolder.newFolder("bulk").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(0, 0, 3L);
        WorldChunkInventory inventory = WorldChunkInventory.ofPackedChunks(
                ChunkGenerationOwnership.packChunk(0, 0),
                ChunkGenerationOwnership.packChunk(1, 0)
        );

        assertThrows(IllegalStateException.class, () -> ownership.assignAll(inventory, 4L));

        assertFalse(ownership.isExplicitlyAssigned(1, 0));
        assertEquals(1, ownership.explicitChunkCount());
        assertEquals(1, ownership.assignAll(inventory, 3L));
        assertEquals(3L, ownership.explicitActivation(1, 0));
    }

    @Test
    public void cutoverAssignmentOnlyMaterializesPreviouslyUnassignedChunks() throws Exception {
        Path directory = temporaryFolder.newFolder("cutover").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(-1, 0, 2L);
        WorldChunkInventory inventory = WorldChunkInventory.ofPackedChunks(
                ChunkGenerationOwnership.packChunk(-1, 0),
                ChunkGenerationOwnership.packChunk(0, 0),
                ChunkGenerationOwnership.packChunk(1, 0)
        );

        assertEquals(2, ownership.assignUnassigned(inventory, 3L));

        assertEquals(2L, ownership.explicitActivation(-1, 0));
        assertEquals(3L, ownership.explicitActivation(0, 0));
        assertEquals(3L, ownership.explicitActivation(1, 0));
    }

    @Test
    public void snapshotUsesTheStablePackedCoordinateContract() throws Exception {
        Path directory = temporaryFolder.newFolder("snapshot").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(Integer.MIN_VALUE, Integer.MAX_VALUE, 1L);
        ownership.assign(-1, 0, 2L);
        ownership.assign(32, -33, 3L);

        long[] expected = {
                ChunkGenerationOwnership.packChunk(Integer.MIN_VALUE, Integer.MAX_VALUE),
                ChunkGenerationOwnership.packChunk(-1, 0),
                ChunkGenerationOwnership.packChunk(32, -33)
        };
        Arrays.sort(expected);
        long[] snapshot = ownership.snapshotExplicitChunkKeys();

        assertArrayEquals(expected, snapshot);
        for (long packed : snapshot) {
            assertEquals(packed, ChunkGenerationOwnership.packChunk(
                    ChunkGenerationOwnership.chunkX(packed),
                    ChunkGenerationOwnership.chunkZ(packed)
            ));
        }
    }

    @Test
    public void loadedLookupsDecodeOnlyTheRequestedRegion() throws Exception {
        Path directory = temporaryFolder.newFolder("lazy-load").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        ownership.assign(-65, 63, 17L);
        ownership.persist();
        ChunkGenerationOwnership loaded = ChunkGenerationOwnership.load(directory);

        assertEquals(0, loaded.cachedRegionCount());
        assertEquals(17L, loaded.resolve(-65, 63, 30L));
        assertEquals(1, loaded.cachedRegionCount());
        assertEquals(30L, loaded.resolve(-64, 63, 30L));
    }

    @Test
    public void regionCacheRemainsBoundedAndReloadsEvictedOwnership() throws Exception {
        Path directory = temporaryFolder.newFolder("bounded-cache").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);
        for (int regionX = 0; regionX < 80; regionX++) {
            ownership.assign(regionX << 5, 0, regionX + 1L);
            assertTrue(ownership.cachedRegionCount() <= 64);
        }
        ownership.persist();

        ChunkGenerationOwnership loaded = ChunkGenerationOwnership.load(directory);
        assertEquals(0, loaded.cachedRegionCount());
        assertEquals(80, loaded.regionCount());
        for (int regionX = 79; regionX >= 0; regionX--) {
            assertEquals(regionX + 1L, loaded.explicitActivation(regionX << 5, 0));
            assertTrue(loaded.cachedRegionCount() <= 64);
        }
        assertEquals(1L, loaded.explicitActivation(0, 0));
    }

    @Test
    public void activationIdsMustBePositive() throws Exception {
        Path directory = temporaryFolder.newFolder("activation-range").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(directory);

        assertThrows(IllegalArgumentException.class, () -> ownership.resolve(0, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> ownership.assign(0, 0, -2L));
        assertThrows(
                IllegalArgumentException.class,
                () -> ownership.assignAll(WorldChunkInventory.empty(), 0L)
        );
    }

    @Test
    public void noncanonicalShardNameFailsClosed() throws Exception {
        Path sourceDirectory = temporaryFolder.newFolder("canonical-source").toPath();
        ChunkGenerationOwnership source = ChunkGenerationOwnership.load(sourceDirectory);
        source.assign(0, 0, 1L);
        source.persist();
        Path invalidDirectory = temporaryFolder.newFolder("canonical-invalid").toPath();
        Files.copy(
                sourceDirectory.resolve(RegionGenerationOwnership.fileName(0, 0)),
                invalidDirectory.resolve("unexpected.irow")
        );

        assertThrows(IOException.class, () -> ChunkGenerationOwnership.load(invalidDirectory));
    }

    @Test
    public void symbolicLinkStorageRootAndParentFailClosed() throws Exception {
        Path probe = temporaryFolder.newFolder("ownership-symlink-probe").toPath();
        Path probeTarget = temporaryFolder.newFolder("ownership-symlink-probe-target").toPath();
        try {
            Files.createSymbolicLink(probe.resolve("link"), probeTarget);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }

        Path rootContainer = temporaryFolder.newFolder("ownership-linked-root-container").toPath();
        Path linkedRoot = rootContainer.resolve("ownership");
        Files.createSymbolicLink(linkedRoot, temporaryFolder.newFolder("ownership-linked-root-target").toPath());
        assertThrows(IOException.class, () -> ChunkGenerationOwnership.load(linkedRoot));

        Path parentContainer = temporaryFolder.newFolder("ownership-linked-parent-container").toPath();
        Path linkedParent = parentContainer.resolve("generation");
        Files.createSymbolicLink(linkedParent, temporaryFolder.newFolder("ownership-linked-parent-target").toPath());
        assertThrows(IOException.class, () -> ChunkGenerationOwnership.load(linkedParent.resolve("ownership")));
    }
}
