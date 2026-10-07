package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.iris.testsupport.KeyedBlockState;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;

public class IrisObjectIoWriteLimitsTest {
    private static IrisObject oversizedPaletteObject;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void buildSharedObjects() {
        oversizedPaletteObject = objectWithDistinctStates(32_768);
    }

    private static NativeBlockState state(String key) {
        return new KeyedBlockState(key);
    }

    private static IrisObject objectWithDistinctStates(int paletteSize) {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        int placed = 0;
        outer:
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                for (int z = 0; z < 64; z++) {
                    if (placed >= paletteSize) {
                        break outer;
                    }
                    object.blocks.put(new IrisBlockVector(x, y, z), state("iris:test_" + placed));
                    placed++;
                }
            }
        }
        return object;
    }

    @Test
    public void preservesV2HeaderSignedCoordinatesAndTilePayload() throws IOException {
        IrisObject object = new IrisObject(7, 9, 11);
        object.blocks.put(new IrisBlockVector(-3, 2, -1), state("minecraft:chest"));
        KMap<String, Object> properties = new KMap<>();
        properties.put("marker", "stored");
        object.states.put(new IrisBlockVector(-3, 2, -1), new TileData("minecraft:chest", properties));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();

        IrisObjectIO.write(object, encoded);

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()))) {
            assertEquals(7, input.readInt());
            assertEquals(9, input.readInt());
            assertEquals(11, input.readInt());
            assertEquals("Iris V2 IOB;", input.readUTF());
            assertEquals(1, input.readShort());
            assertEquals("minecraft:chest", input.readUTF());
            assertEquals(1, input.readInt());
            assertEquals(-3, input.readShort());
            assertEquals(2, input.readShort());
            assertEquals(-1, input.readShort());
            assertEquals(0, input.readShort());
            assertEquals(1, input.readInt());
            assertEquals(-3, input.readShort());
            assertEquals(2, input.readShort());
            assertEquals(-1, input.readShort());
            assertEquals("minecraft:chest", input.readUTF());
            assertEquals("{\"marker\":\"stored\"}", input.readUTF());
            assertEquals(-1, input.read());
        }
    }

    @Test
    public void rejectsPaletteOverflowInsteadOfWrappingTheShort() {
        IrisObject object = oversizedPaletteObject;

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the oversized palette to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("limits-test"));
            assertTrue(e.getMessage(), e.getMessage().contains("32768"));
        }
    }

    @Test
    public void acceptsPaletteAtExactlyTheCap() throws IOException {
        IrisObject object = objectWithDistinctStates(32_767);
        File file = folder.newFile("cap.iob");

        IrisObjectIO.write(object, file);

        assertEquals(32_767, IrisObjectIO.readPaletteKeys(file).size());
    }

    @Test
    public void rejectsCoordinateBeyondShortRange() {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        object.blocks.put(new IrisBlockVector(40_000, 0, 0), state("iris:test"));

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the out-of-range coordinate to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("40000"));
            assertTrue(e.getMessage(), e.getMessage().contains("x"));
        }
    }

    @Test
    public void failedWriteLeavesExistingFileIntact() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObject valid = objectWithDistinctStates(3);
        IrisObjectIO.write(valid, file);
        byte[] before = Files.readAllBytes(file.toPath());

        IrisObject oversized = oversizedPaletteObject;
        try {
            IrisObjectIO.write(oversized, file);
            fail("expected the oversized write to be rejected");
        } catch (IOException expected) {
        }

        assertArrayEquals("a rejected write must not truncate the previous object", before,
                Files.readAllBytes(file.toPath()));
    }
    @Test
    public void invalidDimensionsLeaveExistingFileAndTemporaryDirectoryUntouched() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObjectIO.write(objectWithDistinctStates(3), file);
        byte[] before = Files.readAllBytes(file.toPath());
        for (int axis = 0; axis < 3; axis++) {
            for (int dimension : new int[]{0, -1}) {
                IrisObject invalid = new IrisObject(axis == 0 ? dimension : 1,
                        axis == 1 ? dimension : 1, axis == 2 ? dimension : 1);
                try {
                    IrisObjectIO.write(invalid, file);
                    fail("expected invalid object dimensions to fail");
                } catch (IOException expected) {
                    assertTrue(expected.getMessage().contains("dimensions"));
                }
                assertArrayEquals(before, Files.readAllBytes(file.toPath()));
                assertOnlyDestinationExists(file);
            }
        }
    }

    @Test
    public void serializationFailurePreservesExistingFileAndRemovesTemporaryFile() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObjectIO.write(objectWithDistinctStates(3), file);
        byte[] before = Files.readAllBytes(file.toPath());

        try {
            IrisObjectIO.write(objectWithOversizedTilePayload(), file);
            fail("expected oversized tile payload serialization to fail");
        } catch (IOException expected) {
        }

        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertOnlyDestinationExists(file);
    }

    @Test
    public void progressSerializationFailurePreservesExistingFileAndRemovesTemporaryFile() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObjectIO.write(objectWithDistinctStates(3), file);
        byte[] before = Files.readAllBytes(file.toPath());
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.afut(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable work = invocation.getArgument(0);
                work.run();
                return CompletableFuture.completedFuture(null);
            });
            try {
                IrisObjectIO.write(objectWithOversizedTilePayload(), file, mock(VolmitSender.class));
                fail("expected oversized tile payload serialization to fail");
            } catch (IOException expected) {
            }
        }

        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertOnlyDestinationExists(file);
    }

    @Test
    public void progressUncheckedSerializationFailurePreservesExistingFile() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObjectIO.write(objectWithDistinctStates(3), file);
        byte[] before = Files.readAllBytes(file.toPath());
        IrisObject object = new IrisObject(1, 1, 1);
        TileData tile = mock(TileData.class);
        doThrow(new IllegalStateException("Tile serialization failed")).when(tile).toBinary(any(DataOutputStream.class));
        object.states.put(new IrisBlockVector(0, 0, 0), tile);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.afut(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable work = invocation.getArgument(0);
                work.run();
                return CompletableFuture.completedFuture(null);
            });
            try {
                IrisObjectIO.write(object, file, mock(VolmitSender.class));
                fail("expected unchecked tile serialization failure");
            } catch (IllegalStateException expected) {
                assertEquals("Tile serialization failed", expected.getMessage());
            }
        }

        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertOnlyDestinationExists(file);
    }

    @Test(timeout = 5_000L)
    public void rejectedProgressSchedulingPreservesExistingFileWithoutWaitingForWork() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObjectIO.write(objectWithDistinctStates(3), file);
        byte[] before = Files.readAllBytes(file.toPath());
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.afut(any(Runnable.class))).thenReturn(
                    CompletableFuture.failedFuture(new RejectedExecutionException("Rejected object save")));
            try {
                IrisObjectIO.write(objectWithDistinctStates(1), file, mock(VolmitSender.class));
                fail("expected rejected object-save scheduling");
            } catch (RejectedExecutionException expected) {
                assertEquals("Rejected object save", expected.getMessage());
            }
        }

        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertOnlyDestinationExists(file);
    }

    @Test
    public void fileReplacementPreservesStreamEncodingAndSymbolicLink() throws IOException {
        File file = folder.newFile("existing.iob");
        Path link = folder.getRoot().toPath().resolve("linked.iob");
        Files.createSymbolicLink(link, file.toPath());
        IrisObject replacement = objectWithDistinctStates(3);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        IrisObjectIO.write(replacement, encoded);

        IrisObjectIO.write(replacement, link.toFile());

        assertTrue(Files.isSymbolicLink(link));
        assertArrayEquals(encoded.toByteArray(), Files.readAllBytes(file.toPath()));
        try (Stream<Path> entries = Files.list(folder.getRoot().toPath())) {
            assertEquals(2L, entries.count());
        }
    }

    @Test
    public void replacementPreservesExistingPosixPermissions() throws IOException {
        assumeTrue(Files.getFileStore(folder.getRoot().toPath()).supportsFileAttributeView(PosixFileAttributeView.class));
        File file = folder.newFile("existing.iob");
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(file.toPath(), permissions);

        IrisObjectIO.write(objectWithDistinctStates(3), file);

        assertEquals(permissions, Files.getPosixFilePermissions(file.toPath()));
        assertOnlyDestinationExists(file);
    }

    @Test
    public void newFileUsesNormalPosixCreationPermissions() throws IOException {
        assumeTrue(Files.getFileStore(folder.getRoot().toPath()).supportsFileAttributeView(PosixFileAttributeView.class));
        Path reference = folder.getRoot().toPath().resolve("reference.iob");
        try (FileOutputStream output = new FileOutputStream(reference.toFile())) {
            output.write(0);
        }
        Path destination = folder.getRoot().toPath().resolve("new.iob");

        IrisObjectIO.write(objectWithDistinctStates(3), destination.toFile());

        assertEquals(Files.getPosixFilePermissions(reference), Files.getPosixFilePermissions(destination));
        try (Stream<Path> entries = Files.list(folder.getRoot().toPath())) {
            assertEquals(2L, entries.count());
        }
    }

    @Test
    public void danglingSymbolicLinkChainCreatesItsTargetWithoutReplacingLinks() throws IOException {
        Path root = folder.getRoot().toPath();
        Path targetDirectory = folder.newFolder("objects").toPath();
        Path target = targetDirectory.resolve("created.iob");
        Path second = root.resolve("second.iob");
        Path first = root.resolve("first.iob");
        Files.createSymbolicLink(second, root.relativize(target));
        Files.createSymbolicLink(first, second.getFileName());
        IrisObject replacement = objectWithDistinctStates(3);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        IrisObjectIO.write(replacement, encoded);

        IrisObjectIO.write(replacement, first.toFile());

        assertTrue(Files.isSymbolicLink(first));
        assertTrue(Files.isSymbolicLink(second));
        assertArrayEquals(encoded.toByteArray(), Files.readAllBytes(target));
        try (Stream<Path> entries = Files.list(targetDirectory)) {
            assertEquals(List.of(target), entries.toList());
        }
    }

    @Test(timeout = 5_000L)
    public void symbolicLinkCycleFailsWithoutCreatingTemporaryFiles() throws IOException {
        Path root = folder.getRoot().toPath();
        Path first = root.resolve("first.iob");
        Path second = root.resolve("second.iob");
        Files.createSymbolicLink(first, second.getFileName());
        Files.createSymbolicLink(second, first.getFileName());

        try {
            IrisObjectIO.write(objectWithDistinctStates(3), first.toFile());
            fail("expected symbolic-link cycle to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("symbolic-link cycle"));
        }

        assertTrue(Files.isSymbolicLink(first));
        assertTrue(Files.isSymbolicLink(second));
        try (Stream<Path> entries = Files.list(root)) {
            assertEquals(2L, entries.count());
        }
    }

    private static IrisObject objectWithOversizedTilePayload() {
        IrisObject object = new IrisObject(1, 1, 1);
        IrisBlockVector position = new IrisBlockVector(0, 0, 0);
        object.blocks.put(position, state("minecraft:chest"));
        KMap<String, Object> properties = new KMap<>();
        properties.put("text", "x".repeat(65_536));
        object.states.put(position, new TileData("minecraft:chest", properties));
        return object;
    }

    private void assertOnlyDestinationExists(File file) throws IOException {
        try (Stream<Path> entries = Files.list(folder.getRoot().toPath())) {
            assertEquals(List.of(file.toPath()), entries.toList());
        }
    }

}
