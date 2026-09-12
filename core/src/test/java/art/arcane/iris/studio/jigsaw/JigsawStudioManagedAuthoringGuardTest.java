package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioManagedAuthoringGuardTest {
    private static final String STRUCTURE_KEY = "managed/project";

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path prototypePackRoot;

    @BeforeClass
    public static void createPrototypeProject() throws Exception {
        prototypePackRoot = prototypeFolder.newFolder("prototype").toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                STRUCTURE_KEY,
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16));
        StructureWriteResult result = JigsawStudioProjectCreator.create(prototypePackRoot, options);
        assertTrue(result.successful());
    }

    @Test
    public void graphEditorRejectsManagedDatapackOwnership() throws Exception {
        Path packRoot = createManagedProject();

        IOException lookupFailure = assertThrows(IOException.class,
                () -> JigsawStudioGraphEditor.ownedPoolKeys(packRoot, STRUCTURE_KEY));
        IOException editFailure = assertThrows(IOException.class,
                () -> JigsawStudioGraphEditor.createPool(
                        packRoot,
                        STRUCTURE_KEY,
                        "managed/project/terminal",
                        ""));

        assertManagedGuidance(lookupFailure);
        assertManagedGuidance(editFailure);
        assertFalse(Files.exists(packRoot.resolve("jigsaw-pools/managed/project/terminal.json")));
    }

    @Test
    public void poolEditorRejectsManagedDatapackOwnershipWithoutChangingThePool() throws Exception {
        Path packRoot = createManagedProject();
        Path poolPath = packRoot.resolve("jigsaw-pools/managed/project/start.json");
        byte[] before = Files.readAllBytes(poolPath);

        IOException failure = assertThrows(IOException.class,
                () -> JigsawStudioPoolEditor.updateWeight(
                        packRoot,
                        STRUCTURE_KEY,
                        "managed/project/start",
                        "managed/project/start",
                        9));

        assertManagedGuidance(failure);
        assertArrayEquals(before, Files.readAllBytes(poolPath));
    }

    @Test
    public void structureEditorRejectsManagedDatapackOwnershipWithoutChangingRules() throws Exception {
        Path packRoot = createManagedProject();
        Path structurePath = packRoot.resolve("structures/managed/project.json");
        byte[] before = Files.readAllBytes(structurePath);

        IOException failure = assertThrows(IOException.class,
                () -> JigsawStudioStructureEditor.updateLimits(
                        packRoot,
                        STRUCTURE_KEY,
                        12,
                        6));

        assertManagedGuidance(failure);
        assertArrayEquals(before, Files.readAllBytes(structurePath));
    }

    @Test
    public void centralAccessRuleMatchesManagedDatapackProvenanceOnly() throws Exception {
        Path packRoot = createProject();
        StructureOwnershipManifest created = readManifest(packRoot, STRUCTURE_KEY);

        assertTrue(JigsawStudioAuthoringAccess.isEditable(created));

        StructureOwnershipManifest managed = withManagedProvenance(created);

        assertFalse(JigsawStudioAuthoringAccess.isEditable(managed));
        assertManagedGuidance(assertThrows(IOException.class,
                () -> JigsawStudioAuthoringAccess.requireEditable(managed)));
    }

    @Test
    public void projectDeletionRejectsManagedDatapackOwnership() throws Exception {
        Path packRoot = createManagedProject();

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioProjectDeletionService.inspect(packRoot, STRUCTURE_KEY));

        assertManagedGuidance(failure);
        assertTrue(Files.exists(packRoot.resolve("structures/managed/project.json")));
    }

    private Path createManagedProject() throws Exception {
        Path packRoot = createProject();
        StructureOwnershipManifest manifest = readManifest(packRoot, STRUCTURE_KEY);
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Files.write(
                writer.ownershipManifestPath(StructureKey.parse(STRUCTURE_KEY, "iris")),
                withManagedProvenance(manifest).toJson());
        return packRoot;
    }

    private Path createProject() throws Exception {
        Path packRoot = temporaryFolder.newFolder("pack").toPath();
        copyTree(prototypePackRoot, packRoot);
        return packRoot;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
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
