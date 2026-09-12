package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.graph.StructureAssemblyStatus;
import art.arcane.iris.structure.graph.StructureGraphCompilation;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JigsawStudioProjectCreatorTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final JigsawStudioProjectCreator.Options DEFAULT_PLANAR_OPTIONS =
            new JigsawStudioProjectCreator.Options(
                    "settlement/test",
                    JigsawStudioMode.PLANAR_JIGSAW,
                    JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                    new JigsawStudioCellDimensions(16, 16, 16));

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path defaultPlanarPrototype;

    @BeforeClass
    public static void createPrototypeProject() throws Exception {
        defaultPlanarPrototype = prototypeFolder.newFolder("planar-prototype").toPath();
        assertTrue(JigsawStudioProjectCreator.create(
                defaultPlanarPrototype,
                DEFAULT_PLANAR_OPTIONS).successful());
    }

    @Test
    public void createsCompletePlanarProjectAtomicallyWithoutOverwritingIt() throws Exception {
        Path temporaryDirectory = temporaryFolder.getRoot().toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "settlement/test",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                new JigsawStudioCellDimensions(24, 12, 18));

        StructureResourceBundleGraphCompiler.requireViable(JigsawStudioProjectCreator.bundle(options));

        StructureWriteResult created = JigsawStudioProjectCreator.create(temporaryDirectory, options);

        assertTrue(created.successful());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("structures/settlement/test.json")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("jigsaw-pools/settlement/test/start.json")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("jigsaw-pools/settlement/test/caps.json")));
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            String key = archetype.name().toLowerCase();
            assertTrue(Files.isRegularFile(
                    temporaryDirectory.resolve("jigsaw-pieces/settlement/test/" + key + ".json")));
            assertTrue(Files.isRegularFile(
                    temporaryDirectory.resolve("objects/settlement/test/" + key + ".iob")));
        }

        JsonObject structure = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("structures/settlement/test.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("settlement/test/start", structure.get("startPool").getAsString());
        assertEquals("PLANAR_JIGSAW", structure.get("mode").getAsString());
        assertEquals("VANILLA_PORTABLE", structure.get("compatibility").getAsString());
        assertEquals("TERMINATE_BRANCH", structure.get("branchFailurePolicy").getAsString());
        assertEquals(24, structure.getAsJsonObject("cellSize").get("x").getAsInt());
        assertEquals(6, structure.getAsJsonArray("planarWorkcells").size());
        assertTrue(structure.getAsJsonArray("themeSets").isEmpty());

        JsonObject startPool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/start.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, startPool.getAsJsonArray("pieces").size());
        assertEquals("settlement/test/cross", startPool.getAsJsonArray("pieces").get(0)
                .getAsJsonObject().get("piece").getAsString());
        JsonObject piecePool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(5, piecePool.getAsJsonArray("pieces").size());
        assertEquals("settlement/test/caps", piecePool.get("fallback").getAsString());
        assertFalse(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json"),
                StandardCharsets.UTF_8).contains("settlement/test/blank"));
        JsonObject capPool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/caps.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject emptyCap = capPool.getAsJsonArray("pieces").get(1).getAsJsonObject();
        assertTrue(emptyCap.get("empty").getAsBoolean());
        assertFalse(emptyCap.has("piece"));
        JsonObject cross = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/settlement/test/cross.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, cross.getAsJsonArray("connectors").size());
        assertEquals("settlement/test/pieces", cross.getAsJsonArray("connectors").get(0)
                .getAsJsonObject().get("pool").getAsString());
        assertTrue(cross.getAsJsonArray("themes").isEmpty());

        StructureWriteResult duplicate = JigsawStudioProjectCreator.create(temporaryDirectory, options);
        assertFalse(duplicate.successful());
        assertEquals(StructureWriteResult.Status.ADD_ONLY_CONFLICT, duplicate.status());

        assertTrue(JigsawStudioStructureEditor.updateWorkcellDimensions(
                temporaryDirectory,
                "settlement/test",
                JigsawPlanarArchetype.BLANK,
                new JigsawStudioCellDimensions(32, 16, 20)).successful());
        assertTrue(JigsawStudioStructureEditor.updateWorkcellEnabled(
                temporaryDirectory,
                "settlement/test",
                JigsawPlanarArchetype.BLANK,
                false).successful());

        IOException capacityFailure = assertThrows(IOException.class, () ->
                JigsawStudioStructureEditor.updateWorkcellDimensions(
                        temporaryDirectory,
                        "settlement/test",
                        JigsawPlanarArchetype.END,
                        new JigsawStudioCellDimensions(8, 8, 8)));
        assertTrue(capacityFailure.getMessage().contains("cannot contain"));
        assertEquals(new IrisBlockVector(24, 12, 18), IrisObject.sampleSize(
                temporaryDirectory.resolve("objects/settlement/test/end.iob").toFile()));
        JsonObject editedStructure = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("structures/settlement/test.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("TERMINATE_BRANCH",
                editedStructure.get("branchFailurePolicy").getAsString());
    }

    @Test
    public void rejectsNewPlanarCellsTooSmallForDistinctGlyphs() {
        try {
            new JigsawStudioProjectCreator.Options(
                    "settlement/tiny",
                    JigsawStudioMode.PLANAR_JIGSAW,
                    JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                    new JigsawStudioCellDimensions(2, 8, 2));
            fail("Expected undersized planar authoring cells to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("at least 3"));
        }
    }

    @Test
    public void defaultIrisPlanarProjectCanEnableMandatoryCaps() throws Exception {
        Path temporaryDirectory = createDefaultPlanarProject();

        assertTrue(JigsawStudioStructureEditor.updateRequireCaps(
                temporaryDirectory,
                "settlement/test",
                true).successful());

        JsonObject structure = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("structures/settlement/test.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject end = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/settlement/test/end.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(structure.get("requireCaps").getAsBoolean());
        assertEquals("FAIL_ASSEMBLY", structure.get("branchFailurePolicy").getAsString());
        assertTrue(end.getAsJsonObject("rules").get("terminal").getAsBoolean());
    }

    @Test
    public void defaultMandatoryCapsCompleteAtCompilerAndPreviewSeeds() throws Exception {
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "caps/seeds",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16));
        StructureResourceBundle source = JigsawStudioProjectCreator.bundle(options);
        StructureResourceBundle.Builder builder = StructureResourceBundle.builder(source.key())
                .source(source.source())
                .backend(source.backend())
                .capabilities(source.capabilities())
                .losses(source.losses());
        String structurePath = "structures/caps/seeds.json";
        for (StructureResourceBundle.Resource resource : source.resources().values()) {
            byte[] content = resource.content();
            if (resource.relativePath().equals(structurePath)) {
                JsonObject structure = JsonParser.parseString(
                        new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
                structure.addProperty("requireCaps", true);
                content = structure.toString().getBytes(StandardCharsets.UTF_8);
            }
            builder.resource(resource.relativePath(), content);
        }
        StructureGraphCompilation compilation = StructureResourceBundleGraphCompiler.compile(
                builder.build()).getFirst();

        for (long seed : List.of(0L, 1337L)) {
            StructureAssemblyResult result = StructureAssembler.forCompilation(
                    compilation,
                    new IrisPosition(0, 64, 0)).assemble(new RNG(seed));

            assertEquals(result.detail(), StructureAssemblyStatus.COMPLETE, result.status());
        }
    }

    @Test
    public void spatialProjectSeedsZeroThroughSixConnectorVariants() throws Exception {
        Path temporaryDirectory = temporaryFolder.getRoot().toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "stronghold/gallery",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(15, 15, 15));

        StructureWriteResult result = JigsawStudioProjectCreator.create(temporaryDirectory, options);

        assertTrue(result.successful());
        JsonObject startPool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/stronghold/gallery/start.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject piecePool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/stronghold/gallery/pieces.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(7, startPool.getAsJsonArray("pieces").size());
        assertEquals(7, piecePool.getAsJsonArray("pieces").size());
        assertEquals("stronghold/gallery/connectors-1", piecePool.getAsJsonArray("pieces").get(0)
                .getAsJsonObject().get("piece").getAsString());
        assertTrue(piecePool.getAsJsonArray("pieces").get(6).getAsJsonObject()
                .get("empty").getAsBoolean());
        for (int connectorCount = 0; connectorCount <= 6; connectorCount++) {
            String pieceName = connectorCount == 0 ? "start" : "connectors-" + connectorCount;
            JsonObject piece = JsonParser.parseString(Files.readString(
                    temporaryDirectory.resolve("jigsaw-pieces/stronghold/gallery/"
                            + pieceName + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(connectorCount, piece.getAsJsonArray("connectors").size());
            assertEquals(connectorCount + (connectorCount == 1 ? " Connector" : " Connectors"),
                    piece.get("displayName").getAsString());
            assertEquals(16, piece.getAsJsonObject("rules").get("maximumPlacements").getAsInt());
            assertEquals(new IrisBlockVector(15, 15, 15), IrisObject.sampleSize(
                    temporaryDirectory.resolve("objects/stronghold/gallery/"
                            + pieceName + ".iob").toFile()));
        }
        JsonObject six = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/stronghold/gallery/connectors-6.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("NORTH_NEGATIVE_Z", six.getAsJsonArray("connectors").get(0)
                .getAsJsonObject().get("direction").getAsString());
        assertEquals("DOWN_NEGATIVE_Y", six.getAsJsonArray("connectors").get(5)
                .getAsJsonObject().get("direction").getAsString());
        assertEquals(0, six.getAsJsonArray("connectors").get(5)
                .getAsJsonObject().getAsJsonObject("position").get("y").getAsInt());

        StructureGraphCompilation compilation = StructureResourceBundleGraphCompiler.compile(
                JigsawStudioProjectCreator.bundle(options)).getFirst();
        StructureAssemblyResult preview = StructureAssembler.forCompilation(
                compilation,
                new IrisPosition(0, 0, 0)).assemble(new RNG(1337L));
        assertEquals(preview.detail(), StructureAssemblyStatus.COMPLETE, preview.status());
        assertTrue(preview.pieces().size() > 1);
        assertTrue(preview.pieces().size() <= 96);
    }

    @Test
    public void updatesOwnedPoolThroughWholeGraphTransaction() throws Exception {
        Path temporaryDirectory = createDefaultPlanarProject();
        Path pool = temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json");

        StructureWriteResult terminalPool = JigsawStudioGraphEditor.createPool(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/terminal",
                "");
        assertTrue(terminalPool.successful());
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/terminal.json")));

        JigsawStudioPoolEditor.PoolUpdate fallback = JigsawStudioPoolEditor.updateFallback(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/start",
                "settlement/test/terminal");
        assertTrue(fallback.changed());

        JigsawStudioPoolEditor.WeightUpdate update = JigsawStudioPoolEditor.updateWeight(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                "settlement/test/end",
                9);

        assertTrue(update.changed());
        assertEquals(1, update.changedEntries());
        assertNotNull(update.writeResult());
        assertTrue(update.writeResult().successful());
        JsonObject saved = JsonParser.parseString(Files.readString(pool, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(9, saved.getAsJsonArray("pieces").get(0).getAsJsonObject()
                .get("weight").getAsInt());

        assertTrue(JigsawStudioGraphEditor.createPiece(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                "settlement/test/side",
                2,
                new JigsawStudioCellDimensions(16, 16, 16),
                JigsawPlanarTopology.NORTH_EAST_CORNER).successful());
        JsonObject sidePiece = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/settlement/test/side.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, sidePiece.getAsJsonArray("connectors").size());
        JsonObject northConnector = sidePiece.getAsJsonArray("connectors").get(0).getAsJsonObject();
        assertEquals("NORTH_NEGATIVE_Z", northConnector.get("direction").getAsString());
        assertEquals(8, northConnector.getAsJsonObject("position").get("x").getAsInt());
        assertEquals(8, northConnector.getAsJsonObject("position").get("y").getAsInt());
        assertEquals(0, northConnector.getAsJsonObject("position").get("z").getAsInt());
        assertEquals("minecraft:structure_void", northConnector.get("finalState").getAsString());
        assertTrue(JigsawStudioGraphEditor.updateConnectorChannel(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/side",
                new IrisPosition(8, 8, 0),
                "village/road").successful());
        JsonObject channeledPiece = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/settlement/test/side.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("village/road", channeledPiece.getAsJsonArray("connectors")
                .get(0).getAsJsonObject().get("channel").getAsString());
        JigsawStudioPoolEditor.PoolUpdate removed = JigsawStudioPoolEditor.removePiece(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                "settlement/test/side");
        assertTrue(removed.changed());
        JigsawStudioPoolEditor.PoolUpdate added = JigsawStudioPoolEditor.addPiece(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                "settlement/test/side",
                4);
        assertTrue(added.changed());
        JsonObject reloaded = JsonParser.parseString(
                Files.readString(pool, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, reloaded.getAsJsonArray("pieces").get(5).getAsJsonObject()
                .get("weight").getAsInt());
    }

    @Test
    public void createsOwnedBlankPieceObjectAndPoolEntryInOneTransaction() throws Exception {
        Path temporaryDirectory = temporaryFolder.getRoot().toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "stronghold/test",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(12, 10, 14));
        assertTrue(JigsawStudioProjectCreator.create(temporaryDirectory, options).successful());

        StructureWriteResult result = JigsawStudioGraphEditor.createPiece(
                temporaryDirectory,
                "stronghold/test",
                "stronghold/test/start",
                "stronghold/test/hall",
                3,
                new JigsawStudioCellDimensions(12, 10, 14),
                null);

        assertTrue(result.successful());
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("jigsaw-pieces/stronghold/test/hall.json")));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("objects/stronghold/test/hall.iob")));
        assertTrue(JigsawStudioGraphEditor.ownsPiece(
                temporaryDirectory,
                "stronghold/test",
                "stronghold/test/hall",
                "stronghold/test/hall"));
        assertTrue(JigsawStudioGraphEditor.updateRotatable(
                temporaryDirectory,
                "stronghold/test",
                "stronghold/test/hall",
                false).successful());
        JsonObject hallPiece = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/stronghold/test/hall.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(hallPiece.get("rotatable").getAsBoolean());
        JsonObject pool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/stronghold/test/start.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(8, pool.getAsJsonArray("pieces").size());
        assertEquals("stronghold/test/hall", pool.getAsJsonArray("pieces").get(7)
                .getAsJsonObject().get("piece").getAsString());
        assertEquals(3, pool.getAsJsonArray("pieces").get(7)
                .getAsJsonObject().get("weight").getAsInt());

        StructureWriteResult resized = JigsawStudioStructureEditor.updateCellSize(
                temporaryDirectory,
                "stronghold/test",
                new JigsawStudioCellDimensions(16, 12, 18));
        assertTrue(resized.successful());
        JsonObject structure = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("structures/stronghold/test.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(16, structure.getAsJsonObject("cellSize").get("x").getAsInt());

        assertTrue(JigsawStudioGraphEditor.resizePieceObject(
                temporaryDirectory,
                "stronghold/test",
                "stronghold/test/hall",
                new JigsawStudioCellDimensions(16, 12, 18)).writeResult().successful());
        IrisBlockVector hallSize = IrisObject.sampleSize(
                temporaryDirectory.resolve("objects/stronghold/test/hall.iob").toFile());
        IrisBlockVector starterSize = IrisObject.sampleSize(
                temporaryDirectory.resolve("objects/stronghold/test/start.iob").toFile());
        assertEquals(new IrisBlockVector(16, 12, 18), hallSize);
        assertEquals(new IrisBlockVector(12, 10, 14), starterSize);

        StructureWriteResult limited = JigsawStudioStructureEditor.updateLimits(
                temporaryDirectory,
                "stronghold/test",
                12,
                6);
        assertTrue(limited.successful());
        JsonObject limitedStructure = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("structures/stronghold/test.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(12, limitedStructure.get("maxDepth").getAsInt());
        assertEquals(6, limitedStructure.get("maxSizeChunks").getAsInt());
    }

    @Test
    public void resizesRotatedPlanarVariantToCanonicalDimensionsAndRelocatesSockets() throws Exception {
        Path temporaryDirectory = temporaryFolder.getRoot().toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "settlement/rectangular",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(10, 6, 14));
        assertTrue(JigsawStudioProjectCreator.create(temporaryDirectory, options).successful());
        assertTrue(JigsawStudioGraphEditor.createPiece(
                temporaryDirectory,
                "settlement/rectangular",
                "settlement/rectangular/pieces",
                "settlement/rectangular/east-end",
                1,
                new JigsawStudioCellDimensions(14, 6, 10),
                JigsawPlanarTopology.EAST_END).successful());
        assertTrue(JigsawStudioStructureEditor.updateWorkcellDimensions(
                temporaryDirectory,
                "settlement/rectangular",
                JigsawPlanarArchetype.END,
                new JigsawStudioCellDimensions(12, 8, 18)).successful());

        JigsawStudioGraphEditor.VariantResizeResult resized = JigsawStudioGraphEditor.resizePieceObject(
                temporaryDirectory,
                "settlement/rectangular",
                "settlement/rectangular/east-end",
                new JigsawStudioCellDimensions(12, 8, 18));

        assertTrue(resized.writeResult().successful());
        assertEquals(new IrisBlockVector(18, 8, 12), IrisObject.sampleSize(
                temporaryDirectory.resolve("objects/settlement/rectangular/east-end.iob").toFile()));
        JsonObject piece = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pieces/settlement/rectangular/east-end.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject connector = piece.getAsJsonArray("connectors").get(0).getAsJsonObject();
        assertEquals(17, connector.getAsJsonObject("position").get("x").getAsInt());
        assertEquals(4, connector.getAsJsonObject("position").get("y").getAsInt());
        assertEquals(6, connector.getAsJsonObject("position").get("z").getAsInt());
    }

    @Test
    public void duplicatesVariantsAndEditsOnePinnedPoolEntry() throws Exception {
        Path temporaryDirectory = createDefaultPlanarProject();

        List<String> pools = JigsawStudioGraphEditor.ownedPoolKeys(
                temporaryDirectory,
                "settlement/test");
        assertEquals(List.of("settlement/test/caps", "settlement/test/pieces", "settlement/test/start"), pools);
        String firstKey = JigsawStudioGraphEditor.nextVariantKey(
                temporaryDirectory,
                "settlement/test",
                "end");
        assertEquals("settlement/test/variants/end/variant-1", firstKey);
        assertTrue(JigsawStudioGraphEditor.duplicatePiece(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/end",
                firstKey).successful());
        assertEquals("settlement/test/variants/end/variant-2",
                JigsawStudioGraphEditor.nextVariantKey(
                        temporaryDirectory,
                        "settlement/test",
                        "end"));
        assertTrue(Files.mismatch(
                temporaryDirectory.resolve("objects/settlement/test/end.iob"),
                temporaryDirectory.resolve("objects/settlement/test/variants/end/variant-1.iob")) == -1L);

        JigsawStudioPoolEditor.WeightUpdate weight = JigsawStudioPoolEditor.updateWeightAtIndex(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                1,
                firstKey,
                7);
        assertTrue(weight.changed());
        assertEquals(1, weight.changedEntries());
        JsonObject pool = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, pool.getAsJsonArray("pieces").get(0).getAsJsonObject()
                .get("weight").getAsInt());
        assertEquals(7, pool.getAsJsonArray("pieces").get(1).getAsJsonObject()
                .get("weight").getAsInt());

        try {
            JigsawStudioPoolEditor.updateWeightAtIndex(
                    temporaryDirectory,
                    "settlement/test",
                    "settlement/test/pieces",
                    1,
                    "settlement/test/end",
                    8);
            fail("Expected a stale pool-entry identity to be rejected");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("changed before the update"));
        }

        JigsawStudioPoolEditor.PoolUpdate removed = JigsawStudioPoolEditor.removeEntry(
                temporaryDirectory,
                "settlement/test",
                "settlement/test/pieces",
                1,
                firstKey);
        assertTrue(removed.changed());
        JsonObject afterRemoval = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("jigsaw-pools/settlement/test/pieces.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(5, afterRemoval.getAsJsonArray("pieces").size());
    }

    private Path createDefaultPlanarProject() throws IOException {
        Path packRoot = temporaryFolder.getRoot().toPath();
        try (Stream<Path> entries = Files.walk(defaultPlanarPrototype)) {
            for (Path entry : entries.toList()) {
                Path destination = packRoot.resolve(
                        defaultPlanarPrototype.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return packRoot;
    }
}
