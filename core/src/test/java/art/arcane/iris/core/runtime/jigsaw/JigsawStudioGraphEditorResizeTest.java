package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.volmit.geometry.BlockPosition;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JigsawStudioGraphEditorResizeTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resizesOnlyTheRequestedVariantWithinWorkcellCapacity() throws Exception {
        Path packRoot = createPlanarProject("resize/atomic");
        JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "resize/atomic",
                planarSources("resize/atomic"),
                "variant-2");

        JigsawStudioGraphEditor.VariantResizeResult result =
                JigsawStudioGraphEditor.resizePieceObject(
                        packRoot,
                        "resize/atomic",
                        "resize/atomic/variants/corner/variant-2",
                        new JigsawStudioCellDimensions(7, 4, 9));

        assertTrue(result.writeResult().successful());
        assertEquals(new JigsawStudioCellDimensions(16, 16, 16), result.previousDimensions());
        assertEquals(new JigsawStudioCellDimensions(7, 4, 9), result.dimensions());
        assertEquals(2, result.relocatedConnectors());
        assertEquals(new IrisBlockVector(16, 16, 16), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/atomic/corner.iob").toFile()));
        assertEquals(new IrisBlockVector(7, 4, 9), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/atomic/variants/corner/variant-2.iob").toFile()));
        assertEquals(new IrisBlockVector(16, 16, 16), IrisObject.sampleSize(
                packRoot.resolve("objects/resize/atomic/end.iob").toFile()));

        JsonObject structure = readJson(packRoot.resolve("structures/resize/atomic.json"));
        JsonObject workcell = workcell(structure.getAsJsonArray("planarWorkcells"), "CORNER");
        assertEquals(16, workcell.get("width").getAsInt());
        assertEquals(16, workcell.get("height").getAsInt());
        assertEquals(16, workcell.get("depth").getAsInt());
        JsonArray connectors = readJson(packRoot.resolve(
                        "jigsaw-pieces/resize/atomic/variants/corner/variant-2.json"))
                .getAsJsonArray("connectors");
        assertEquals(new BlockPosition(3, 2, 0), connectorPosition(connectors, "NORTH_NEGATIVE_Z"));
        assertEquals(new BlockPosition(6, 2, 4), connectorPosition(connectors, "EAST_POSITIVE_X"));
    }

    @Test
    public void capacityChangesNeverRewriteVariantObjects() throws Exception {
        Path packRoot = createPlanarProject("resize/capacity");
        JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "resize/capacity",
                planarSources("resize/capacity"),
                "variant-2");
        Path sourceObject = packRoot.resolve("objects/resize/capacity/corner.iob");
        Path familyObject = packRoot.resolve("objects/resize/capacity/variants/corner/variant-2.iob");
        byte[] sourceBefore = Files.readAllBytes(sourceObject);
        byte[] familyBefore = Files.readAllBytes(familyObject);

        IOException rejected = assertThrows(
                IOException.class,
                () -> JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                        packRoot,
                        "resize/capacity",
                        JigsawPlanarArchetype.CORNER,
                        new JigsawStudioCellDimensions(7, 4, 9)));

        assertTrue(rejected.getMessage().contains("fit") || rejected.getMessage().contains("capacity"));
        assertArrayEquals(sourceBefore, Files.readAllBytes(sourceObject));
        assertArrayEquals(familyBefore, Files.readAllBytes(familyObject));

        JigsawStudioGraphEditor.WorkcellCapacityResult result =
                JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                        packRoot,
                        "resize/capacity",
                        JigsawPlanarArchetype.CORNER,
                        new JigsawStudioCellDimensions(24, 18, 20));

        assertTrue(result.writeResult().successful());
        assertEquals(2, result.checkedVariants());
        assertArrayEquals(sourceBefore, Files.readAllBytes(sourceObject));
        assertArrayEquals(familyBefore, Files.readAllBytes(familyObject));
        JsonObject structure = readJson(packRoot.resolve("structures/resize/capacity.json"));
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
        IrisJigsawPiece piece = endPiece(JigsawDirection.EAST_POSITIVE_X, new BlockPosition(6, 1, 2));

        JigsawStudioGraphEditor.PlanarPieceObjectResize result =
                JigsawStudioGraphEditor.resizePlanarPieceObject(
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
        assertEquals(new BlockPosition(10, 1, 4), piece.getConnectors().getFirst().getPosition());
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
                () -> JigsawStudioGraphEditor.resizePlanarPieceObject(
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
        BlockPosition originalPosition = new BlockPosition(2, 1, 0);
        IrisJigsawPiece piece = endPiece(JigsawDirection.NORTH_NEGATIVE_Z, originalPosition);

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioGraphEditor.resizePlanarPieceObject(
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

    private Path createPlanarProject(String structureKey) throws Exception {
        Path packRoot = temporaryFolder.newFolder(structureKey.replace('/', '-')).toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                structureKey,
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16));
        assertTrue(JigsawStudioProjectCreator.create(packRoot, options).successful());
        return packRoot;
    }

    private static IrisJigsawPiece endPiece(JigsawDirection direction, BlockPosition position) {
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

    private static BlockPosition connectorPosition(JsonArray connectors, String direction) {
        for (int index = 0; index < connectors.size(); index++) {
            JsonObject connector = connectors.get(index).getAsJsonObject();
            if (!direction.equals(connector.get("direction").getAsString())) {
                continue;
            }
            JsonObject position = connector.getAsJsonObject("position");
            return new BlockPosition(
                    position.get("x").getAsInt(),
                    position.get("y").getAsInt(),
                    position.get("z").getAsInt());
        }
        throw new AssertionError("Missing connector " + direction);
    }
}
