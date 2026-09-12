package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.StructurePackageClosure;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteMode;
import art.arcane.iris.structure.authoring.StructureWriteOptions;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.testsupport.DurabilityMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioResourceBundleAssemblerTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STRUCTURE_KEY = "fort";

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path prototypePackRoot;

    @BeforeClass
    public static void createPrototypeProject() throws Exception {
        prototypePackRoot = prototypeFolder.newFolder("prototype").toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                STRUCTURE_KEY,
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(4, 4, 4));
        StructureWriteResult result = JigsawStudioProjectCreator.create(prototypePackRoot, options);
        assertTrue(result.successful());
    }

    @Test
    public void assemblesAndAtomicallyOverwritesTheWholeOwnedGraph() throws Exception {
        Path packRoot = createProject();
        Path structurePath = packRoot.resolve("structures/fort.json");
        byte[] structureBefore = Files.readAllBytes(structurePath);
        IrisJigsawConnector connector = connector();

        JigsawStudioResourceBundleAssembler.Assembly assembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        "fort",
                        "fort/start",
                        emptyObject(),
                        List.of(connector),
                        false);

        assertEquals(17, assembly.bundle().resources().size());
        assertEquals("fort/start", assembly.objectKey());
        assertEquals(1, assembly.piece().getConnectors().size());
        StructureWriteResult result = new StructureTransactionWriter(packRoot)
                .write(assembly.bundle(), StructureWriteMode.OVERWRITE);
        assertTrue(result.successful());
        assertTrue(result.committed());

        Path piecePath = packRoot.resolve("jigsaw-pieces/fort/start.json");
        IrisJigsawPiece persisted = new Gson().fromJson(
                Files.readString(piecePath, StandardCharsets.UTF_8), IrisJigsawPiece.class);
        assertEquals(1, persisted.getConnectors().size());
        assertEquals("fort/start", persisted.getConnectors().getFirst().getPool());
        assertArrayEquals(structureBefore, Files.readAllBytes(structurePath));
        StructurePackageClosure closure = StructurePackageClosure.collect(packRoot.toFile(), List.of("fort"));
        assertTrue(closure.errors().toString(), closure.isValid());
    }

    @Test
    public void assemblesAutosaveBundleFromDefaultPlanarProject() throws Exception {
        String structureKey = "autosave/planar";
        Path packRoot = temporaryFolder.newFolder("planar-autosave").toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                structureKey,
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16));
        assertTrue(JigsawStudioProjectCreator.create(packRoot, options).successful());
        String pieceKey = structureKey + "/end";
        IrisJigsawPiece piece = new Gson().fromJson(
                Files.readString(
                        packRoot.resolve("jigsaw-pieces/" + pieceKey + ".json"),
                        StandardCharsets.UTF_8),
                IrisJigsawPiece.class);

        JigsawStudioResourceBundleAssembler.Assembly assembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        structureKey,
                        pieceKey,
                        Files.readAllBytes(packRoot.resolve("objects/" + pieceKey + ".iob")),
                        List.copyOf(piece.getConnectors()),
                        false);
        StructureWriteResult result = new StructureTransactionWriter(packRoot).write(
                assembly.bundle(),
                StructureWriteOptions.overwriteExpected(assembly.expectedManifestHash()));

        assertTrue(result.conflicts().toString(), result.successful());
        assertTrue(result.committed());
        StructurePackageClosure closure = StructurePackageClosure.collect(
                packRoot.toFile(),
                List.of(structureKey));
        assertTrue(closure.errors().toString(), closure.isValid());
    }

    @Test
    public void capturePreservesAbsentDefaultsAndUnknownPieceMetadata() throws Exception {
        Path packRoot = createProject();
        String pieceResource = "jigsaw-pieces/fort/start.json";
        Path piecePath = packRoot.resolve(pieceResource);
        JsonObject source = JsonParser.parseString(
                Files.readString(piecePath, StandardCharsets.UTF_8)).getAsJsonObject();
        source.remove("collidable");
        JsonObject extension = new JsonObject();
        extension.addProperty("futureMode", "retained");
        source.add("extensionMetadata", extension);
        byte[] sourceContent = (PRETTY_GSON.toJson(source) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(piecePath, sourceContent);
        IrisJigsawPiece sourcePiece = new Gson().fromJson(source, IrisJigsawPiece.class);
        JigsawStudioResourceBundleAssembler.Assembly unchangedAssembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        STRUCTURE_KEY,
                        "fort/start",
                        emptyObject(),
                        List.copyOf(sourcePiece.getConnectors()),
                        false);
        assertArrayEquals(
                sourceContent,
                unchangedAssembly.bundle().resources().get(pieceResource).content());
        IrisJigsawConnector updated = connector()
                .setPool("fort/start")
                .setName("iris:updated");

        JigsawStudioResourceBundleAssembler.Assembly assembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        STRUCTURE_KEY,
                        "fort/start",
                        emptyObject(),
                        List.of(updated),
                        false);

        JsonObject captured = JsonParser.parseString(new String(
                assembly.bundle().resources().get(pieceResource).content(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(captured.has("collidable"));
        assertEquals(extension, captured.getAsJsonObject("extensionMetadata"));
        assertEquals(
                "iris:updated",
                captured.getAsJsonArray("connectors")
                        .get(0)
                        .getAsJsonObject()
                        .get("name")
                        .getAsString());
    }

    @Test
    public void refusesAProjectWithoutAnOwnershipManifest() throws Exception {
        Path packRoot = createProject();
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Files.delete(writer.ownershipManifestPath(new StructureKey("iris", STRUCTURE_KEY)));

        IOException exception = assertThrows(IOException.class,
                () -> JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        STRUCTURE_KEY,
                        "fort/start",
                        emptyObject(),
                        List.of(connector()),
                        false));

        assertTrue(exception.getMessage().contains("not Studio-owned"));
    }

    @Test
    public void refusesManagedDatapackOwnershipWithoutAssemblingAStudioSave() throws Exception {
        Path packRoot = createProject();
        Path objectPath = packRoot.resolve("objects/fort/start.iob");
        byte[] before = Files.readAllBytes(objectPath);
        markManagedDatapack(packRoot, STRUCTURE_KEY);

        IOException exception = assertThrows(IOException.class,
                () -> JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        STRUCTURE_KEY,
                        "fort/start",
                        emptyObject(),
                        List.of(connector()),
                        false));

        assertTrue(exception.getMessage(), exception.getMessage().contains("managed by datapack ingest"));
        assertTrue(exception.getMessage(), exception.getMessage().contains("adopt or clone"));
        assertArrayEquals(before, Files.readAllBytes(objectPath));
    }

    @Test
    public void writerRejectsAResourceModifiedOutsideTheOwnershipTransaction() throws Exception {
        Path packRoot = createProject();
        Path piecePath = packRoot.resolve("jigsaw-pieces/fort/start.json");
        Files.writeString(piecePath, Files.readString(piecePath, StandardCharsets.UTF_8) + "\n");
        JigsawStudioResourceBundleAssembler.Assembly assembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        STRUCTURE_KEY,
                        "fort/start",
                        emptyObject(),
                        List.of(connector().setPool("fort/start")),
                        false);

        StructureWriteResult result = new StructureTransactionWriter(packRoot)
                .write(assembly.bundle(), StructureWriteMode.OVERWRITE);

        assertFalse(result.successful());
        assertEquals(StructureWriteResult.Status.OWNERSHIP_CONFLICT, result.status());
        assertFalse(result.conflicts().isEmpty());
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

    private static void markManagedDatapack(Path packRoot, String structureKey) throws IOException {
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Path manifestPath = writer.ownershipManifestPath(new StructureKey("iris", structureKey));
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(manifestPath));
        Map<String, String> mappings = new TreeMap<>();
        for (String relativePath : manifest.resourceHashes().keySet()) {
            mappings.put(relativePath, relativePath);
        }
        StructureOwnershipManifest.Provenance provenance = new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.fromString("88888888-8888-8888-8888-888888888888").toString(),
                "1".repeat(64),
                "2".repeat(64),
                1L,
                manifest.resourceHashes(),
                mappings,
                StructureOwnershipManifest.RollbackDisposition.NONE);
        StructureOwnershipManifest managed = new StructureOwnershipManifest(
                manifest.schemaVersion(),
                manifest.structure(),
                manifest.source(),
                manifest.backend(),
                manifest.capabilities(),
                manifest.losses(),
                manifest.resourceHashes(),
                provenance);
        Files.write(manifestPath, managed.toJson());
    }

    private static IrisJigsawConnector connector() {
        return new IrisJigsawConnector()
                .setPosition(new IrisPosition(0, 0, 0))
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("fort/start")
                .setName("iris:door")
                .setTargetName("iris:door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:air");
    }

    private static byte[] emptyObject() throws IOException {
        IrisObject object = new IrisObject(4, 4, 4);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            object.write(output);
            return output.toByteArray();
        }
    }
}
