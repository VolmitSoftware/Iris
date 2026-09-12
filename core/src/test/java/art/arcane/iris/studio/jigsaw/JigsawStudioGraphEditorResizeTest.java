package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import com.google.gson.JsonArray;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JigsawStudioGraphEditorResizeTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final String STRUCTURE_KEY = "resize/planar";

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
        assertTrue(JigsawStudioProjectCreator.create(prototypePackRoot, options).successful());
        JigsawStudioGraphEditor.duplicateActiveFamily(
                prototypePackRoot,
                STRUCTURE_KEY,
                planarSources(STRUCTURE_KEY),
                "variant-2");
    }

    @Test
    public void resizesOnlyTheRequestedVariantWithinWorkcellCapacity() throws Exception {
        Path packRoot = createPlanarProjectWithVariantFamily();

        JigsawStudioGraphEditor.VariantResizeResult result =
                JigsawStudioGraphEditor.resizePieceObject(
                        packRoot,
                        STRUCTURE_KEY,
                        "resize/planar/variants/corner/variant-2",
                        new JigsawStudioCellDimensions(7, 4, 9));

        assertTrue(result.writeResult().successful());
        assertEquals(new JigsawStudioCellDimensions(16, 16, 16), result.previousDimensions());
        assertEquals(new JigsawStudioCellDimensions(7, 4, 9), result.dimensions());
        assertEquals(2, result.relocatedConnectors());
        assertEquals(new IrisBlockVector(16, 16, 16), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/planar/corner.iob").toFile()));
        assertEquals(new IrisBlockVector(7, 4, 9), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/planar/variants/corner/variant-2.iob").toFile()));
        assertEquals(new IrisBlockVector(16, 16, 16), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/planar/end.iob").toFile()));

        JsonObject structure = readJson(packRoot.resolve("structures/resize/planar.json"));
        JsonObject workcell = workcell(structure.getAsJsonArray("planarWorkcells"), "CORNER");
        assertEquals(16, workcell.get("width").getAsInt());
        assertEquals(16, workcell.get("height").getAsInt());
        assertEquals(16, workcell.get("depth").getAsInt());
        JsonArray connectors = readJson(packRoot.resolve(
                        "jigsaw-pieces/resize/planar/variants/corner/variant-2.json"))
                .getAsJsonArray("connectors");
        assertEquals(new IrisPosition(3, 2, 0), connectorPosition(connectors, "NORTH_NEGATIVE_Z"));
        assertEquals(new IrisPosition(6, 2, 4), connectorPosition(connectors, "EAST_POSITIVE_X"));
    }

    @Test
    public void capacityChangesNeverRewriteVariantObjects() throws Exception {
        Path packRoot = createPlanarProjectWithVariantFamily();
        Path sourceObject = packRoot.resolve("objects/resize/planar/corner.iob");
        Path familyObject = packRoot.resolve("objects/resize/planar/variants/corner/variant-2.iob");
        byte[] sourceBefore = Files.readAllBytes(sourceObject);
        byte[] familyBefore = Files.readAllBytes(familyObject);

        IOException rejected = assertThrows(
                IOException.class,
                () -> JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                        packRoot,
                        STRUCTURE_KEY,
                        JigsawPlanarArchetype.CORNER,
                        new JigsawStudioCellDimensions(7, 4, 9)));

        assertTrue(rejected.getMessage().contains("fit") || rejected.getMessage().contains("capacity"));
        assertArrayEquals(sourceBefore, Files.readAllBytes(sourceObject));
        assertArrayEquals(familyBefore, Files.readAllBytes(familyObject));

        JigsawStudioGraphEditor.WorkcellCapacityResult result =
                JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                        packRoot,
                        STRUCTURE_KEY,
                        JigsawPlanarArchetype.CORNER,
                        new JigsawStudioCellDimensions(24, 18, 20));

        assertTrue(result.writeResult().successful());
        assertEquals(2, result.checkedVariants());
        assertArrayEquals(sourceBefore, Files.readAllBytes(sourceObject));
        assertArrayEquals(familyBefore, Files.readAllBytes(familyObject));
        JsonObject structure = readJson(packRoot.resolve("structures/resize/planar.json"));
        JsonObject workcell = workcell(structure.getAsJsonArray("planarWorkcells"), "CORNER");
        assertEquals(24, workcell.get("width").getAsInt());
        assertEquals(18, workcell.get("height").getAsInt());
        assertEquals(20, workcell.get("depth").getAsInt());
    }

    @Test
    public void preservesCanonicalCoordinatesForRotatedVariantsAndMovesExplicitAirUnderlays() throws Exception {
        IrisObject source = new IrisObject(7, 3, 5);
        PlatformBlockState explicitAir = state("minecraft:air", true);
        PlatformBlockState stone = state("minecraft:stone", false);
        source.setUnsigned(6, 1, 2, explicitAir);
        source.setUnsigned(4, 1, 1, stone);
        IrisJigsawPiece piece = endPiece(IrisDirection.EAST_POSITIVE_X, new IrisPosition(6, 1, 2));

        JigsawStudioObjectResizer.PlanarPieceObjectResize result =
                JigsawStudioObjectResizer.resizePlanarPieceObject(
                        source,
                        piece,
                        JigsawPlanarArchetype.END,
                        new JigsawStudioCellDimensions(9, 3, 11),
                        "resize/rotated-east");

        IrisObject resized = result.object();
        assertEquals(11, resized.getW());
        assertEquals(3, resized.getH());
        assertEquals(9, resized.getD());
        assertEquals(1, result.relocatedConnectors());
        assertEquals(new IrisPosition(10, 1, 4), piece.getConnectors().getFirst().getPosition());
        assertSame(explicitAir, blockAt(resized, 10, 1, 4));
        assertFalse(resized.getBlocks().containsKey(resized.getSigned(10, 1, 2)));
        assertSame(stone, blockAt(resized, 8, 1, 1));
    }

    @Test
    public void rejectsExplicitAirOutsideShrunkBoundsWithoutMutatingTheSource() {
        IrisObject source = new IrisObject(8, 4, 8);
        PlatformBlockState explicitAir = state("minecraft:air", true);
        source.setUnsigned(7, 1, 1, explicitAir);
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject("resize/blank");

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioObjectResizer.resizePlanarPieceObject(
                        source,
                        piece,
                        JigsawPlanarArchetype.BLANK,
                        new JigsawStudioCellDimensions(6, 4, 6),
                        "resize/blank"));

        assertTrue(failure.getMessage().contains("including explicit air"));
        assertEquals(8, source.getW());
        assertSame(explicitAir, blockAt(source, 7, 1, 1));
    }

    @Test
    public void rejectsConnectorDestinationCollisionsBeforeChangingThePiece() {
        IrisObject source = new IrisObject(5, 3, 5);
        PlatformBlockState underlay = state("minecraft:air", true);
        PlatformBlockState collision = state("minecraft:stone", false);
        source.setUnsigned(2, 1, 0, underlay);
        source.setUnsigned(3, 1, 0, collision);
        IrisPosition originalPosition = new IrisPosition(2, 1, 0);
        IrisJigsawPiece piece = endPiece(IrisDirection.NORTH_NEGATIVE_Z, originalPosition.copy());

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioObjectResizer.resizePlanarPieceObject(
                        source,
                        piece,
                        JigsawPlanarArchetype.END,
                        new JigsawStudioCellDimensions(7, 3, 7),
                        "resize/collision"));

        assertTrue(failure.getMessage().contains("destination contains a stored block"));
        assertEquals(originalPosition, piece.getConnectors().getFirst().getPosition());
        assertSame(underlay, blockAt(source, 2, 1, 0));
        assertSame(collision, blockAt(source, 3, 1, 0));
    }

    private Path createPlanarProjectWithVariantFamily() throws Exception {
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

    private static IrisJigsawPiece endPiece(IrisDirection direction, IrisPosition position) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject("resize/end");
        piece.getConnectors().add(new IrisJigsawConnector()
                .setDirection(direction)
                .setPosition(position));
        return piece;
    }

    private static Map<String, String> planarSources(String structureKey) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            sources.put(
                    archetype.stableId(),
                    structureKey + "/" + archetype.name().toLowerCase(Locale.ROOT));
        }
        return sources;
    }

    private static PlatformBlockState state(String key, boolean air) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.isAir()).thenReturn(air);
        return state;
    }

    private static PlatformBlockState blockAt(IrisObject object, int x, int y, int z) {
        return object.getBlocks().get(object.getSigned(x, y, z));
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject workcell(JsonArray workcells, String archetype) {
        for (int index = 0; index < workcells.size(); index++) {
            JsonObject workcell = workcells.get(index).getAsJsonObject();
            if (archetype.equals(workcell.get("archetype").getAsString())) {
                return workcell;
            }
        }
        throw new AssertionError("Missing planar workcell " + archetype);
    }

    private static IrisPosition connectorPosition(JsonArray connectors, String direction) {
        for (int index = 0; index < connectors.size(); index++) {
            JsonObject connector = connectors.get(index).getAsJsonObject();
            if (!direction.equals(connector.get("direction").getAsString())) {
                continue;
            }
            JsonObject position = connector.getAsJsonObject("position");
            return new IrisPosition(
                    position.get("x").getAsInt(),
                    position.get("y").getAsInt(),
                    position.get("z").getAsInt());
        }
        throw new AssertionError("Missing connector " + direction);
    }
}
