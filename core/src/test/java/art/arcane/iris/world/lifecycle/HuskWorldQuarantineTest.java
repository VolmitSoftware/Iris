package art.arcane.iris.world.lifecycle;

import art.arcane.iris.generation.runtime.IrisEngineMantle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Deleting a loaded Iris world's folder and letting the server save the level back leaves a directory the
 * server still enumerates but that owns nothing: no pack snapshot, no chunk data. Left in the dimensions
 * tree it stops startup either way - Iris refuses to boot over it, or excluding it trips Paper's interactive
 * world-migration gate - so the bootstrap has to move it out before any level is created.
 */
public class HuskWorldQuarantineTest {
    private static final Instant STAMP = Instant.parse("2026-08-20T04:05:06Z");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aServerSkeletonWithNoPackAndNoChunkDataIsMovedOutOfTheDimensionsTree() throws IOException {
        Path levelRoot = level("husk");
        Path husk = dimensionRoot(levelRoot, "irisworld");
        write(husk.resolve("data/paper/level_overrides.dat"));
        write(husk.resolve("data/minecraft/raids.dat"));

        List<String> warnings = new ArrayList<>();
        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warnings::add, STAMP);

        assertEquals(1, moved.size());
        assertFalse("the server must not enumerate the husk", Files.exists(husk, LinkOption.NOFOLLOW_LINKS));
        Path destination = levelRoot.resolve("iris/husks/irisworld-20260820T040506");
        assertEquals(destination, moved.getFirst().destination());
        assertTrue(Files.isRegularFile(destination.resolve("data/paper/level_overrides.dat")));
        assertEquals(1, warnings.size());
        String warning = warnings.getFirst();
        assertTrue(warning, warning.contains(husk.toString()));
        assertTrue(warning, warning.contains(destination.toString()));
        assertTrue(warning, warning.contains("/iris remove world=world_iris_irisworld delete=true"));
    }

    @Test
    public void aWorldWithRegionDataIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("region");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("region/r.0.0.mca"));

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void aWorldWithEntitiesOrPoiDataIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("entities");
        Path entities = dimensionRoot(levelRoot, "onlyentities");
        write(entities.resolve("entities/r.0.0.mca"));
        Path poi = dimensionRoot(levelRoot, "onlypoi");
        write(poi.resolve("poi/r.0.0.mca"));

        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                }, STAMP);

        assertTrue(moved.isEmpty());
        assertTrue(Files.isDirectory(entities));
        assertTrue(Files.isDirectory(poi));
    }

    @Test
    public void aWorldWithMantleDataIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("mantle");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve(IrisEngineMantle.STORAGE_FOLDER_NAME).resolve("pv.0.ttp.lz4b"));

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void anIrisMarkerDirectoryIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("marker");
        Path world = dimensionRoot(levelRoot, "irisworld");
        Files.createDirectories(world.resolve("iris/engine-data"));
        write(world.resolve("data/paper/level_overrides.dat"));

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void aFrozenPackSnapshotIsLeftAlone() throws IOException {
        Path levelRoot = level("pack");
        Path world = dimensionRoot(levelRoot, "irisworld");
        Files.createDirectories(world.resolve("iris/pack/dimensions"));

        assertQuarantinesNothing(levelRoot, world);
    }

    /**
     * macOS recreates a directory to hold a .DS_Store while a delete is still in flight, so a husk whose
     * iris/ folder holds nothing but that file is exactly as worthless as one with no iris/ folder at all.
     */
    @Test
    public void anIrisFolderHoldingOnlyOsMetadataIsTreatedAsAbsent() throws IOException {
        Path levelRoot = level("dsstore");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("iris/.DS_Store"));
        write(world.resolve("data/paper/level_overrides.dat"));

        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                }, STAMP);

        assertEquals(1, moved.size());
        assertFalse("the server must not enumerate the husk", Files.exists(world, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void everyOsMetadataFileCountsAsAbsent() throws IOException {
        Path levelRoot = level("osmetadata");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("iris/.DS_Store"));
        write(world.resolve("iris/Thumbs.db"));
        write(world.resolve("iris/desktop.ini"));
        write(world.resolve("iris/._pack"));

        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                }, STAMP);

        assertEquals(1, moved.size());
    }

    @Test
    public void anIrisFolderWithRealContentBesideOsMetadataIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("dsstore-and-pack");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("iris/.DS_Store"));
        Files.createDirectories(world.resolve("iris/pack/dimensions"));

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void anIrisFolderHoldingOnlyAnEmptySubdirectoryIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("dsstore-and-dir");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("iris/.DS_Store"));
        Files.createDirectories(world.resolve("iris/engine-data"));

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void aSymlinkedIrisFolderIsLeftForTheFailClosedGuard() throws IOException {
        Path levelRoot = level("iris-symlink");
        Path world = dimensionRoot(levelRoot, "irisworld");
        Path elsewhere = temporaryFolder.newFolder("iris-symlink-target").toPath();
        try {
            Files.createSymbolicLink(world.resolve("iris"), elsewhere);
        } catch (IOException | UnsupportedOperationException unsupported) {
            assumeTrue("symbolic links are not creatable on this filesystem", false);
        }

        assertQuarantinesNothing(levelRoot, world);
    }

    @Test
    public void aSymlinkedDimensionRootIsNeverMoved() throws IOException {
        Path levelRoot = level("symlink");
        Path namespace = levelRoot.resolve("dimensions/iris");
        Files.createDirectories(namespace);
        Path elsewhere = temporaryFolder.newFolder("symlink-target").toPath();
        write(elsewhere.resolve("data/paper/level_overrides.dat"));
        Path link = namespace.resolve("irisworld");
        try {
            Files.createSymbolicLink(link, elsewhere);
        } catch (IOException | UnsupportedOperationException unsupported) {
            assumeTrue("symbolic links are not creatable on this filesystem", false);
        }

        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                }, STAMP);

        assertTrue(moved.isEmpty());
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.isRegularFile(elsewhere.resolve("data/paper/level_overrides.dat")));
    }

    @Test
    public void anUnreadableDimensionRootIsNeverMoved() throws IOException {
        Path levelRoot = level("unreadable");
        Path world = dimensionRoot(levelRoot, "irisworld");
        write(world.resolve("data/paper/level_overrides.dat"));
        assumeTrue("posix permissions are required to make a directory unreadable",
                Files.getFileStore(world).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(world, Set.of());
        try {
            assumeTrue("running as root defeats the permission bits", !Files.isReadable(world));
            List<HuskWorldQuarantine.Quarantine> moved =
                    HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                    }, STAMP);

            assertTrue(moved.isEmpty());
        } finally {
            Files.setPosixFilePermissions(world, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    public void aSecondHuskInTheSameSecondGetsItsOwnDestination() throws IOException {
        Path levelRoot = level("collision");
        Path first = dimensionRoot(levelRoot, "irisworld");
        write(first.resolve("data/paper/level_overrides.dat"));
        Files.createDirectories(levelRoot.resolve("iris/husks/irisworld-20260820T040506"));

        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
                }, STAMP);

        assertEquals(1, moved.size());
        assertEquals(levelRoot.resolve("iris/husks/irisworld-20260820T040506-2"), moved.getFirst().destination());
    }

    @Test
    public void aLevelWithNoIrisNamespaceIsAnEmptySweep() throws IOException {
        Path levelRoot = level("none");

        assertTrue(HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warning -> {
        }, STAMP).isEmpty());
    }

    private void assertQuarantinesNothing(Path levelRoot, Path world) {
        List<String> warnings = new ArrayList<>();
        List<HuskWorldQuarantine.Quarantine> moved =
                HuskWorldQuarantine.quarantineWorthlessHusks(levelRoot, warnings::add, STAMP);

        assertTrue(moved.isEmpty());
        assertTrue(warnings.isEmpty());
        assertTrue(Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS));
    }

    private Path level(String scope) throws IOException {
        return temporaryFolder.newFolder(scope, "world").toPath();
    }

    private static Path dimensionRoot(Path levelRoot, String key) throws IOException {
        Path root = levelRoot.resolve("dimensions/iris").resolve(key);
        Files.createDirectories(root);
        return root;
    }

    private static void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
