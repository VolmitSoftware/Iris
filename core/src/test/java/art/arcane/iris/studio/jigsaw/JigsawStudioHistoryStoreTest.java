package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteOptions;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioHistoryStoreTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void retainsFiveDeduplicatedIterationsAndRestoresThemThroughTheWriter() throws Exception {
        Path root = temporaryFolder.newFolder("history").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        JigsawStudioHistoryStore history = new JigsawStudioHistoryStore(root, "qa/history");
        assertTrue(writer.write(bundle(0), StructureWriteOptions.addOnly()).successful());

        for (int version = 1; version <= 7; version++) {
            JigsawStudioHistoryStore.Snapshot previous = history.snapshotCurrent("qa/history/piece");
            assertEquals(Math.min(version, JigsawStudioHistoryStore.MAX_ITERATIONS), history.append(previous));
            StructureWriteResult write = writer.write(
                    bundle(version),
                    StructureWriteOptions.overwriteExpected(manifestHash(writer)));
            assertTrue(write.successful());
        }

        assertEquals(JigsawStudioHistoryStore.MAX_ITERATIONS, history.availableIterations());
        assertTrue(Files.isRegularFile(history.historyPath()));
        try (Stream<Path> historyFiles = Files.list(history.historyPath().getParent())) {
            assertEquals(1L, historyFiles.filter(Files::isRegularFile).count());
        }
        for (int expectedVersion = 6; expectedVersion >= 2; expectedVersion--) {
            JigsawStudioHistoryStore.UndoResult undo = history.undoLatest();
            assertTrue(undo.available());
            assertTrue(undo.successful());
            assertEquals("qa/history/piece", undo.pieceKey());
            assertEquals(expectedVersion - 2, undo.remainingIterations());
            assertArrayEquals(
                    content(expectedVersion),
                    Files.readAllBytes(root.resolve("objects/qa/history/object.iob")));
            assertOwnedResourcesMatchManifest(root, writer);
        }

        assertFalse(Files.exists(history.historyPath()));
        assertFalse(history.undoLatest().available());
    }

    @Test
    public void identicalSnapshotsDoNotConsumeAnotherIteration() throws Exception {
        Path root = temporaryFolder.newFolder("dedup").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        JigsawStudioHistoryStore history = new JigsawStudioHistoryStore(root, "qa/history");
        assertTrue(writer.write(bundle(0), StructureWriteOptions.addOnly()).successful());
        JigsawStudioHistoryStore.Snapshot snapshot = history.snapshotCurrent("qa/history/piece");

        assertEquals(1, history.append(snapshot));
        assertEquals(1, history.append(snapshot));
        assertEquals(1, history.availableIterations());
    }

    @Test
    public void refusesToSnapshotAnOwnedResourceThatChangedOutsideTheWriter() throws Exception {
        Path root = temporaryFolder.newFolder("modified").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        JigsawStudioHistoryStore history = new JigsawStudioHistoryStore(root, "qa/history");
        assertTrue(writer.write(bundle(0), StructureWriteOptions.addOnly()).successful());
        Files.writeString(root.resolve("objects/qa/history/object.iob"), "external-change");

        assertThrows(Exception.class, () -> history.snapshotCurrent("qa/history/piece"));
        assertFalse(Files.exists(history.historyPath()));
    }

    private static StructureResourceBundle bundle(int version) throws Exception {
        StructureKey key = new StructureKey("iris", "qa/history");
        return StructureResourceBundle.builder(key)
                .source(StructureSource.of(StructureSource.Kind.IRIS, key))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS)
                .textResource("structures/qa/history.json", "{\"startPool\":\"qa/history/start\"}")
                .textResource("jigsaw-pools/qa/history/start.json",
                        "{\"pieces\":[{\"piece\":\"qa/history/piece\"}]}")
                .textResource("jigsaw-pieces/qa/history/piece.json",
                        "{\"object\":\"qa/history/object\",\"connectors\":[]}")
                .resource("objects/qa/history/object.iob", content(version))
                .build();
    }

    private static byte[] content(int version) throws Exception {
        IrisObject object = new IrisObject(version + 1, 1, 1);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            object.write(output);
            return output.toByteArray();
        }
    }

    private static String manifestHash(StructureTransactionWriter writer) throws Exception {
        Path manifestPath = writer.ownershipManifestPath(new StructureKey("iris", "qa/history"));
        return StructureHash.sha256(Files.readAllBytes(manifestPath));
    }

    private static void assertOwnedResourcesMatchManifest(
            Path root,
            StructureTransactionWriter writer
    ) throws Exception {
        Path manifestPath = writer.ownershipManifestPath(new StructureKey("iris", "qa/history"));
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(manifestPath));
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            assertEquals(
                    resource.getValue(),
                    StructureHash.sha256(Files.readAllBytes(root.resolve(resource.getKey()))));
        }
        assertEquals(
                List.of(StructureCapability.BLOCKS),
                manifest.capabilities());
    }
}
