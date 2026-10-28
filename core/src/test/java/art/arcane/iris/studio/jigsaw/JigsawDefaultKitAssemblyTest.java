package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.graph.StructureAssemblyStatus;
import art.arcane.iris.structure.graph.StructureGraphCompilation;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JigsawDefaultKitAssemblyTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final Gson GSON = new Gson();
    private static final int SEEDS = 160;
    private static final int SPATIAL_IRIS_PIECE_BOUND = 23 * 4 + 1;
    private static final int SPATIAL_PORTABLE_PIECE_BOUND = 129;
    private static final List<JigsawStudioCellDimensions> PLANAR_DIMENSIONS = List.of(
            new JigsawStudioCellDimensions(16, 16, 16),
            new JigsawStudioCellDimensions(15, 9, 15));
    private static final List<JigsawStudioCellDimensions> SPATIAL_DIMENSIONS = List.of(
            new JigsawStudioCellDimensions(15, 15, 15),
            new JigsawStudioCellDimensions(16, 12, 16));

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void planarDefaultKitOpensEveryOccupiedNeighborOnTheCellGrid() throws IOException {
        for (JigsawStudioCellDimensions dimensions : PLANAR_DIMENSIONS) {
            for (JigsawStudioCompatibilityTarget target : JigsawStudioCompatibilityTarget.values()) {
                StructureGraphCompilation compilation = compile(JigsawStudioMode.PLANAR_JIGSAW, target, dimensions);
                int closedFaces = 0;
                for (long seed = 0; seed < SEEDS; seed++) {
                    List<PlacedStructurePiece> pieces = complete(compilation, seed);
                    for (PlacedStructurePiece piece : pieces) {
                        for (IrisJigsawConnector connector : piece.getPiece().getConnectors()) {
                            IrisDirection facing = piece.getRotation().rotate(connector.getDirection());
                            IrisPosition targetCell = offset(planarSocket(piece, facing), facing);
                            PlacedStructurePiece neighbor = containing(pieces, targetCell);
                            if (neighbor == null) {
                                continue;
                            }
                            String context = target + " " + dimensions + " seed " + seed + ": "
                                    + piece.getPiece().getObject() + " faces " + facing + " into "
                                    + neighbor.getPiece().getObject();
                            assertTrue(context, hasPlanarSocket(
                                    neighbor,
                                    facing.reverse(),
                                    targetCell,
                                    piece.getRotation().rotate(connector.getTop())));
                            assertSharedFace(context, piece, neighbor, facing);
                            closedFaces++;
                        }
                    }
                }
                assertTrue(closedFaces > SEEDS);
            }
        }
    }

    @Test
    public void planarDefaultKitTurnsBothWaysThroughCornersAndTees() throws IOException {
        StructureGraphCompilation compilation = compile(
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                PLANAR_DIMENSIONS.getFirst());
        Map<String, Set<Integer>> attachments = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            List<PlacedStructurePiece> pieces = complete(compilation, seed);
            for (int index = 1; index < pieces.size(); index++) {
                PlacedStructurePiece piece = pieces.get(index);
                int attachment = soleEarlierPlanarAttachment(pieces, index);
                if (attachment >= 0) {
                    attachments.computeIfAbsent(baseName(piece), ignored -> new HashSet<>()).add(attachment);
                }
            }
        }
        assertEquals(attachments.toString(), Set.of(0, 1), attachments.get("corner"));
        Set<Integer> teeAttachments = attachments.get("tee");
        assertNotNull(attachments.toString(), teeAttachments);
        assertTrue(attachments.toString(), teeAttachments.contains(0));
        assertTrue(attachments.toString(), teeAttachments.contains(1) || teeAttachments.contains(2));
    }

    @Test
    public void connectorlessBlankPiecesGenerateAsStandaloneStructures() throws IOException {
        for (JigsawStudioMode mode : JigsawStudioMode.values()) {
            JigsawStudioCellDimensions dimensions = mode == JigsawStudioMode.PLANAR_JIGSAW
                    ? PLANAR_DIMENSIONS.getFirst()
                    : SPATIAL_DIMENSIONS.getFirst();
            StructureGraphCompilation compilation = compile(
                    mode,
                    JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                    dimensions);
            int standaloneBlanks = 0;
            int multiPieceAssemblies = 0;
            for (long seed = 0; seed < SEEDS * 8 && (standaloneBlanks == 0 || seed < SEEDS); seed++) {
                List<PlacedStructurePiece> pieces = complete(compilation, seed);
                if (pieces.size() == 1 && pieces.getFirst().getPiece().getConnectors().isEmpty()) {
                    assertEquals("blank", baseName(pieces.getFirst()));
                    standaloneBlanks++;
                    continue;
                }
                multiPieceAssemblies++;
                for (PlacedStructurePiece piece : pieces) {
                    assertFalse(mode + " seed " + seed, piece.getPiece().getConnectors().isEmpty());
                }
            }
            assertTrue(mode + " never generated its connectorless piece", standaloneBlanks > 0);
            assertTrue(mode + " generated only standalone pieces", multiPieceAssemblies > standaloneBlanks);
        }

        StructureResourceBundle planar = JigsawStudioProjectCreator.bundle(options(
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                PLANAR_DIMENSIONS.getLast()));
        IrisJigsawPool start = pool(planar, "kit/test/start");
        assertEquals(2, start.getPieces().size());
        assertEquals("kit/test/cross", start.getPieces().get(0).getPiece());
        assertEquals(3, start.getPieces().get(0).getWeight());
        assertEquals("kit/test/blank", start.getPieces().get(1).getPiece());
        assertEquals(1, start.getPieces().get(1).getWeight());
        for (IrisJigsawPieceEntry entry : pool(planar, "kit/test/pieces").getPieces()) {
            assertFalse("kit/test/blank".equals(entry.getPiece()));
        }
    }

    @Test
    public void spatialDefaultKitCoversEveryYRotationTopologyOnce() throws IOException {
        for (JigsawStudioCompatibilityTarget target : JigsawStudioCompatibilityTarget.values()) {
            JigsawStudioCellDimensions dimensions = SPATIAL_DIMENSIONS.getLast();
            StructureResourceBundle bundle = JigsawStudioProjectCreator.bundle(
                    options(JigsawStudioMode.SPATIAL_JIGSAW, target, dimensions));
            Map<String, IrisJigsawPiece> pieces = pieces(bundle);
            Map<Integer, String> bySignature = new HashMap<>();
            for (Map.Entry<String, IrisJigsawPiece> entry : pieces.entrySet()) {
                IrisJigsawPiece piece = entry.getValue();
                int horizontal = 0;
                int vertical = 0;
                for (IrisJigsawConnector connector : piece.getConnectors()) {
                    IrisDirection direction = connector.getDirection();
                    assertEquals(spatialFaceCenter(dimensions, direction), connector.getPosition());
                    assertEquals(direction.isVertical() ? IrisDirection.NORTH_NEGATIVE_Z : IrisDirection.UP_POSITIVE_Y,
                            connector.getTop());
                    assertEquals(JigsawJoint.ROLLABLE, connector.getJoint());
                    assertEquals("kit/test/pieces", connector.getPool());
                    horizontal |= horizontalBit(direction);
                    vertical |= verticalBit(direction);
                }
                assertTrue(piece.isRotatable());
                assertEquals(entry.getKey(), piece.getObject());
                assertEquals(expectedSpatialKey(canonicalMask(horizontal), vertical), entry.getKey());
                String previous = bySignature.put(canonicalMask(horizontal) | vertical, entry.getKey());
                assertEquals("duplicate topology " + entry.getKey() + " and " + previous, null, previous);
                if (target == JigsawStudioCompatibilityTarget.IRIS_EXTENDED) {
                    assertEquals(List.of("variant-1"), List.copyOf(piece.getThemes()));
                    assertEquals(4, piece.resolvedRules().getMaximumPlacements());
                } else {
                    assertTrue(piece.getThemes().isEmpty());
                    assertEquals(0, piece.resolvedRules().getMaximumPlacements());
                }
            }
            assertEquals(24, pieces.size());
            assertEquals(24, bySignature.size());

            IrisJigsawPool start = pool(bundle, "kit/test/start");
            assertEquals(24, start.getPieces().size());
            IrisJigsawPool piecePool = pool(bundle, "kit/test/pieces");
            assertEquals(24, piecePool.getPieces().size());
            assertTrue(piecePool.getPieces().getLast().isEmpty());
            for (IrisJigsawPieceEntry entry : piecePool.getPieces()) {
                assertFalse("kit/test/blank".equals(entry.getPiece()));
            }
        }
    }

    @Test
    public void spatialDefaultKitAttachesEveryPieceThroughAlignedConnectorsAndTurnsBothWays() throws IOException {
        for (JigsawStudioCellDimensions dimensions : SPATIAL_DIMENSIONS) {
            for (JigsawStudioCompatibilityTarget target : JigsawStudioCompatibilityTarget.values()) {
                StructureGraphCompilation compilation = compile(JigsawStudioMode.SPATIAL_JIGSAW, target, dimensions);
                int bound = target == JigsawStudioCompatibilityTarget.IRIS_EXTENDED
                        ? SPATIAL_IRIS_PIECE_BOUND
                        : SPATIAL_PORTABLE_PIECE_BOUND;
                Set<String> placedKinds = new HashSet<>();
                Set<Integer> cornerAttachments = new HashSet<>();
                for (long seed = 0; seed < SEEDS; seed++) {
                    List<PlacedStructurePiece> pieces = complete(compilation, seed);
                    String context = target + " " + dimensions + " seed " + seed;
                    assertTrue(context + " placed " + pieces.size(), pieces.size() <= bound);
                    for (int index = 0; index < pieces.size(); index++) {
                        placedKinds.add(baseName(pieces.get(index)));
                        if (index == 0) {
                            continue;
                        }
                        List<Integer> attachments = earlierSpatialAttachments(pieces, index);
                        assertFalse(context + " left " + baseName(pieces.get(index)) + " unattached",
                                attachments.isEmpty());
                        if (attachments.size() == 1 && baseName(pieces.get(index)).startsWith("corner")
                                && attachments.getFirst() < 2) {
                            cornerAttachments.add(attachments.getFirst());
                        }
                    }
                }
                assertEquals(target + " " + dimensions, Set.of(0, 1), cornerAttachments);
                assertFalse(placedKinds.contains("blank"));
                assertEquals(target + " " + dimensions + " placed " + placedKinds, 23, placedKinds.size());
            }
        }
    }

    @Test
    public void vanillaPortableSpatialProjectIsCreatedWithAViableGraph() throws IOException {
        Path packRoot = temporaryFolder.getRoot().toPath();
        JigsawStudioProjectCreator.Options options = options(
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                SPATIAL_DIMENSIONS.getFirst());

        StructureWriteResult result = JigsawStudioProjectCreator.create(packRoot, options);

        assertTrue(result.successful());
        IrisStructure structure = GSON.fromJson(Files.readString(
                packRoot.resolve("structures/kit/test.json"), StandardCharsets.UTF_8), IrisStructure.class);
        assertEquals(4, structure.getMaxDepth());
        assertTrue(Files.isRegularFile(packRoot.resolve("jigsaw-pieces/kit/test/corner-up-down.json")));
        assertTrue(Files.isRegularFile(packRoot.resolve("objects/kit/test/blank.iob")));
    }

    private static JigsawStudioProjectCreator.Options options(
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget target,
            JigsawStudioCellDimensions dimensions
    ) {
        return new JigsawStudioProjectCreator.Options("kit/test", mode, target, dimensions);
    }

    private static StructureGraphCompilation compile(
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget target,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        StructureGraphCompilation compilation = StructureResourceBundleGraphCompiler.compile(
                JigsawStudioProjectCreator.bundle(options(mode, target, dimensions))).getFirst();
        assertFalse(compilation.getDiagnostics().toString(), compilation.hasErrors());
        return compilation;
    }

    private static List<PlacedStructurePiece> complete(StructureGraphCompilation compilation, long seed) {
        StructureAssemblyResult result = StructureAssembler.forCompilation(
                compilation,
                new IrisPosition(0, 64, 0)).assemble(new RNG(seed));
        assertEquals("seed " + seed + ": " + result.detail(), StructureAssemblyStatus.COMPLETE, result.status());
        return result.pieces();
    }

    private static int soleEarlierPlanarAttachment(List<PlacedStructurePiece> pieces, int index) {
        PlacedStructurePiece piece = pieces.get(index);
        int attachment = -1;
        int pairings = 0;
        for (int connectorIndex = 0; connectorIndex < piece.getPiece().getConnectors().size(); connectorIndex++) {
            IrisJigsawConnector connector = piece.getPiece().getConnectors().get(connectorIndex);
            IrisDirection facing = piece.getRotation().rotate(connector.getDirection());
            IrisPosition targetCell = offset(planarSocket(piece, facing), facing);
            for (int earlier = 0; earlier < index; earlier++) {
                PlacedStructurePiece neighbor = pieces.get(earlier);
                if (contains(neighbor, targetCell) && hasPlanarSocket(
                        neighbor,
                        facing.reverse(),
                        targetCell,
                        piece.getRotation().rotate(connector.getTop()))) {
                    attachment = connectorIndex;
                    pairings++;
                }
            }
        }
        return pairings == 1 ? attachment : -1;
    }

    private static List<Integer> earlierSpatialAttachments(List<PlacedStructurePiece> pieces, int index) {
        PlacedStructurePiece piece = pieces.get(index);
        List<Integer> attachments = new ArrayList<>();
        for (int connectorIndex = 0; connectorIndex < piece.getPiece().getConnectors().size(); connectorIndex++) {
            IrisJigsawConnector connector = piece.getPiece().getConnectors().get(connectorIndex);
            IrisPosition socket = spatialSocket(piece, connector);
            IrisDirection facing = piece.getRotation().rotate(connector.getDirection());
            for (int earlier = 0; earlier < index; earlier++) {
                PlacedStructurePiece neighbor = pieces.get(earlier);
                for (IrisJigsawConnector other : neighbor.getPiece().getConnectors()) {
                    IrisDirection otherFacing = neighbor.getRotation().rotate(other.getDirection());
                    if (otherFacing == facing.reverse()
                            && offset(spatialSocket(neighbor, other), otherFacing).equals(socket)) {
                        attachments.add(connectorIndex);
                    }
                }
            }
        }
        return attachments;
    }

    private static boolean hasPlanarSocket(
            PlacedStructurePiece piece,
            IrisDirection facing,
            IrisPosition position,
            IrisDirection top
    ) {
        if (!planarSocket(piece, facing).equals(position)) {
            return false;
        }
        for (IrisJigsawConnector connector : piece.getPiece().getConnectors()) {
            if (piece.getRotation().rotate(connector.getDirection()) == facing
                    && piece.getRotation().rotate(connector.getTop()) == top) {
                return true;
            }
        }
        return false;
    }

    private static void assertSharedFace(
            String context,
            PlacedStructurePiece piece,
            PlacedStructurePiece neighbor,
            IrisDirection facing
    ) {
        assertEquals(context, piece.getMinY(), neighbor.getMinY());
        assertEquals(context, piece.getMaxY(), neighbor.getMaxY());
        switch (facing) {
            case EAST_POSITIVE_X -> assertEquals(context, piece.getMaxX() + 1, neighbor.getMinX());
            case WEST_NEGATIVE_X -> assertEquals(context, piece.getMinX() - 1, neighbor.getMaxX());
            case SOUTH_POSITIVE_Z -> assertEquals(context, piece.getMaxZ() + 1, neighbor.getMinZ());
            case NORTH_NEGATIVE_Z -> assertEquals(context, piece.getMinZ() - 1, neighbor.getMaxZ());
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new AssertionError(context + " uses a vertical socket");
        }
        if (facing.x() != 0) {
            assertEquals(context, piece.getMinZ(), neighbor.getMinZ());
            assertEquals(context, piece.getMaxZ(), neighbor.getMaxZ());
        } else {
            assertEquals(context, piece.getMinX(), neighbor.getMinX());
            assertEquals(context, piece.getMaxX(), neighbor.getMaxX());
        }
    }

    private static IrisPosition planarSocket(PlacedStructurePiece piece, IrisDirection facing) {
        IrisPosition size = new IrisPosition(
                piece.getMaxX() - piece.getMinX() + 1,
                piece.getMaxY() - piece.getMinY() + 1,
                piece.getMaxZ() - piece.getMinZ() + 1);
        IrisPosition local = IrisJigsawConnector.canonicalPlanarPosition(size, facing);
        return new IrisPosition(
                piece.getMinX() + local.getX(),
                piece.getMinY() + local.getY(),
                piece.getMinZ() + local.getZ());
    }

    private static IrisPosition spatialSocket(PlacedStructurePiece piece, IrisJigsawConnector connector) {
        IrisObject object = piece.getObject();
        IrisPosition relative = new IrisPosition(
                connector.getPosition().getX() - object.getW() / 2,
                connector.getPosition().getY() - object.getH() / 2,
                connector.getPosition().getZ() - object.getD() / 2);
        IrisPosition rotated = piece.getRotation().rotate(relative);
        return new IrisPosition(
                piece.getX() + rotated.getX(),
                piece.getY() + rotated.getY(),
                piece.getZ() + rotated.getZ());
    }

    private static IrisPosition spatialFaceCenter(JigsawStudioCellDimensions dimensions, IrisDirection direction) {
        int centerX = dimensions.width() / 2;
        int centerY = dimensions.height() / 2;
        int centerZ = dimensions.depth() / 2;
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> new IrisPosition(centerX, centerY, 0);
            case SOUTH_POSITIVE_Z -> new IrisPosition(centerX, centerY, dimensions.depth() - 1);
            case EAST_POSITIVE_X -> new IrisPosition(dimensions.width() - 1, centerY, centerZ);
            case WEST_NEGATIVE_X -> new IrisPosition(0, centerY, centerZ);
            case UP_POSITIVE_Y -> new IrisPosition(centerX, dimensions.height() - 1, centerZ);
            case DOWN_NEGATIVE_Y -> new IrisPosition(centerX, 0, centerZ);
        };
    }

    private static IrisPosition offset(IrisPosition position, IrisDirection direction) {
        return new IrisPosition(
                position.getX() + direction.x(),
                position.getY() + direction.y(),
                position.getZ() + direction.z());
    }

    private static PlacedStructurePiece containing(List<PlacedStructurePiece> pieces, IrisPosition position) {
        for (PlacedStructurePiece piece : pieces) {
            if (contains(piece, position)) {
                return piece;
            }
        }
        return null;
    }

    private static boolean contains(PlacedStructurePiece piece, IrisPosition position) {
        return position.getX() >= piece.getMinX() && position.getX() <= piece.getMaxX()
                && position.getY() >= piece.getMinY() && position.getY() <= piece.getMaxY()
                && position.getZ() >= piece.getMinZ() && position.getZ() <= piece.getMaxZ();
    }

    private static String baseName(PlacedStructurePiece piece) {
        String key = piece.getPiece().getObject();
        return key.substring(key.lastIndexOf('/') + 1);
    }

    private static int horizontalBit(IrisDirection direction) {
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> 1;
            case EAST_POSITIVE_X -> 2;
            case SOUTH_POSITIVE_Z -> 4;
            case WEST_NEGATIVE_X -> 8;
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> 0;
        };
    }

    private static int verticalBit(IrisDirection direction) {
        return switch (direction) {
            case UP_POSITIVE_Y -> 16;
            case DOWN_NEGATIVE_Y -> 32;
            case NORTH_NEGATIVE_Z, EAST_POSITIVE_X, SOUTH_POSITIVE_Z, WEST_NEGATIVE_X -> 0;
        };
    }

    private static int canonicalMask(int mask) {
        int canonical = mask;
        for (int quarterTurns = 1; quarterTurns < 4; quarterTurns++) {
            canonical = Math.min(canonical, ((mask << quarterTurns) | (mask >>> (4 - quarterTurns))) & 15);
        }
        return canonical;
    }

    private static String expectedSpatialKey(int canonicalHorizontal, int vertical) {
        String horizontal = switch (canonicalHorizontal) {
            case 0 -> "blank";
            case 1 -> "end";
            case 3 -> "corner";
            case 5 -> "straight";
            case 7 -> "tee";
            case 15 -> "cross";
            default -> throw new AssertionError("Unexpected horizontal topology " + canonicalHorizontal);
        };
        String suffix = switch (vertical) {
            case 0 -> "";
            case 16 -> "-up";
            case 32 -> "-down";
            case 48 -> "-up-down";
            default -> throw new AssertionError("Unexpected vertical topology " + vertical);
        };
        return "kit/test/" + horizontal + suffix;
    }

    private static Map<String, IrisJigsawPiece> pieces(StructureResourceBundle bundle) {
        Map<String, IrisJigsawPiece> pieces = new TreeMap<>();
        for (StructureResourceBundle.Resource resource : bundle.resources().values()) {
            String path = resource.relativePath();
            if (path.startsWith("jigsaw-pieces/") && path.endsWith(".json")) {
                pieces.put(
                        path.substring("jigsaw-pieces/".length(), path.length() - ".json".length()),
                        GSON.fromJson(new String(resource.content(), StandardCharsets.UTF_8), IrisJigsawPiece.class));
            }
        }
        return pieces;
    }

    private static IrisJigsawPool pool(StructureResourceBundle bundle, String key) {
        StructureResourceBundle.Resource resource = bundle.resources().get("jigsaw-pools/" + key + ".json");
        assertNotNull(key, resource);
        return GSON.fromJson(new String(resource.content(), StandardCharsets.UTF_8), IrisJigsawPool.class);
    }
}
