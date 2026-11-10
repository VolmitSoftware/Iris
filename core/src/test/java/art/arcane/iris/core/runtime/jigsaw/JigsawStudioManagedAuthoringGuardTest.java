package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioManagedAuthoringGuardTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void graphEditorRejectsManagedDatapackOwnership() throws Exception {
        Path packRoot = createManagedProject("managed/graph");

        IOException lookupFailure = assertThrows(IOException.class,
                () -> JigsawStudioGraphEditor.ownedPoolKeys(packRoot, "managed/graph"));
        IOException editFailure = assertThrows(IOException.class,
                () -> JigsawStudioGraphEditor.createPool(
                        packRoot,
                        "managed/graph",
                        "managed/graph/terminal",
                        ""));

        assertManagedGuidance(lookupFailure);
        assertManagedGuidance(editFailure);
        assertFalse(Files.exists(packRoot.resolve("jigsaw-pools/managed/graph/terminal.json")));
    }

    @Test
    public void poolEditorRejectsManagedDatapackOwnershipWithoutChangingThePool() throws Exception {
        Path packRoot = createManagedProject("managed/pool");
        Path poolPath = packRoot.resolve("jigsaw-pools/managed/pool/start.json");
        byte[] before = Files.readAllBytes(poolPath);

        IOException failure = assertThrows(IOException.class,
                () -> JigsawStudioPoolEditor.updateWeight(
                        packRoot,
                        "managed/pool",
                        "managed/pool/start",
                        "managed/pool/start",
                        9));

        assertManagedGuidance(failure);
        assertArrayEquals(before, Files.readAllBytes(poolPath));
    }

    @Test
    public void structureEditorRejectsManagedDatapackOwnershipWithoutChangingRules() throws Exception {
        Path packRoot = createManagedProject("managed/rules");
        Path structurePath = packRoot.resolve("structures/managed/rules.json");
        byte[] before = Files.readAllBytes(structurePath);

        IOException failure = assertThrows(IOException.class,
                () -> JigsawStudioStructureEditor.updateLimits(
                        packRoot,
                        "managed/rules",
                        12,
                        6));

        assertManagedGuidance(failure);
        assertArrayEquals(before, Files.readAllBytes(structurePath));
    }

    @Test
    public void centralAccessRuleMatchesManagedDatapackProvenanceOnly() throws Exception {
        Path packRoot = createProject("managed/access");
        StructureOwnershipManifest created = readManifest(packRoot, "managed/access");

        assertTrue(JigsawStudioAuthoringAccess.isEditable(created));

        StructureOwnershipManifest managed = withManagedProvenance(created);

        assertFalse(JigsawStudioAuthoringAccess.isEditable(managed));
        assertManagedGuidance(assertThrows(IOException.class,
                () -> JigsawStudioAuthoringAccess.requireEditable(managed)));
    }

    @Test
    public void projectDeletionRejectsManagedDatapackOwnership() throws Exception {
        Path packRoot = createManagedProject("managed/deletion");

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioProjectDeletionService.inspect(packRoot, "managed/deletion"));

        assertManagedGuidance(failure);
        assertTrue(Files.exists(packRoot.resolve("structures/managed/deletion.json")));
    }

    private Path createManagedProject(String structureKey) throws Exception {
        Path packRoot = createProject(structureKey);
        StructureOwnershipManifest manifest = readManifest(packRoot, structureKey);
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Files.write(
                writer.ownershipManifestPath(StructureKey.parse(structureKey, "iris")),
                withManagedProvenance(manifest).toJson());
        return packRoot;
    }

    private Path createProject(String structureKey) throws Exception {
        Path packRoot = temporaryFolder.newFolder(structureKey.replace('/', '-')).toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                structureKey,
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16));
        StructureWriteResult result = JigsawStudioProjectCreator.create(packRoot, new art.arcane.iris.core.tooling.IrisObjectCodec(), options);
        assertTrue(result.successful());
        return packRoot;
    }

    private static StructureOwnershipManifest readManifest(
            Path packRoot,
            String structureKey
    ) throws IOException {
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Path manifestPath = writer.ownershipManifestPath(StructureKey.parse(structureKey, "iris"));
        return StructureOwnershipManifest.fromJson(Files.readAllBytes(manifestPath));
    }

    private static StructureOwnershipManifest withManagedProvenance(
            StructureOwnershipManifest manifest
    ) {
        Map<String, String> sourceHashes = new TreeMap<>();
        Map<String, String> sourceMappings = new TreeMap<>();
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            String sourcePath = "managed-source/" + resource.getKey();
            sourceHashes.put(sourcePath, resource.getValue());
            sourceMappings.put(sourcePath, resource.getKey());
        }
        String planHash = StructureHash.sha256(
                ("managed-plan:" + manifest.structure().value()).getBytes(StandardCharsets.UTF_8));
        String closureHash = StructureHash.sha256(
                ("managed-closure:" + manifest.structure().value()).getBytes(StandardCharsets.UTF_8));
        StructureOwnershipManifest.Provenance provenance = new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.fromString("77777777-7777-7777-7777-777777777777").toString(),
                planHash,
                closureHash,
                1L,
                sourceHashes,
                sourceMappings,
                StructureOwnershipManifest.RollbackDisposition.NONE);
        return new StructureOwnershipManifest(
                manifest.schemaVersion(),
                manifest.structure(),
                manifest.source(),
                manifest.backend(),
                manifest.capabilities(),
                manifest.losses(),
                manifest.resourceHashes(),
                provenance);
    }

    private static void assertManagedGuidance(IOException failure) {
        assertTrue(failure.getMessage(), failure.getMessage().contains("managed by datapack ingest"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("adopt or clone"));
    }
}
