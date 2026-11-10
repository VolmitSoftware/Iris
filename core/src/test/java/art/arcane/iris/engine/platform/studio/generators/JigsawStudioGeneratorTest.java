package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarDirection;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.core.service.JigsawStudioRequest;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.ChunkCanvas;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JigsawStudioGeneratorTest {
    private static final String PACK_NAMESPACE = "test";

    @Test
    public void connectorBlocksAreHiddenByDefaultAndCanBeShownPerWorkcell() {
        PackFixture pack = new PackFixture();
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new BlockPosition(1, 1, 1))
                .setDirection(JigsawDirection.NORTH_NEGATIVE_Z)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("test/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:stone");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("test/room")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        BlockState stone = BlockState.parse("minecraft:stone");
        pack.piece("test/room", piece);
        pack.object("test/room", object(3, 3, 3, new PlacedBlock(1, 1, 1, stone, null)));
        JigsawStudioVariant variant = new JigsawStudioVariant(
                "test/room",
                "test/room",
                "",
                Optional.of(new JigsawStudioCellDimensions(3, 3, 3)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
        GeneratorFixture fixture = fixture(
                pack,
                JigsawStudioMode.SPATIAL_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));
        JigsawStudioBay workcell = fixture.layout().bays().getFirst();
        int worldX = workcell.bounds().originX() + 1;
        int worldY = workcell.bounds().originY() + 1;
        int worldZ = workcell.bounds().originZ() + 1;

        assertFalse(fixture.generator().getSession().workcellSnapshot(
                workcell.stableId()).connectorsVisible());
        assertSame(stone, stateAt(fixture.generator(), worldX, worldY, worldZ));

        BlockState marker = BlockState.parse("minecraft:jigsaw[orientation=north_up]");
        when(pack.blocks.find("minecraft:jigsaw[orientation=north_up]")).thenReturn(Optional.of(marker));
        fixture.generator().getSession().setConnectorsVisible(workcell.stableId(), true);
        assertSame(marker, stateAt(fixture.generator(), worldX, worldY, worldZ));
    }

    @Test
    public void paintsCanonicalWoolGlyphsBelowCaptureAndOneControlChest() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(7, 5, 7);
        GeneratorFixture fixture = fixture(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        JigsawStudioBay corner = fixture.layout().get("workcell/corner");
        int glyphY = corner.bounds().originY() - 1;
        int centerX = dimensions.width() / 2;
        int centerZ = dimensions.depth() / 2;

        assertSame(fixture.connectorCap(), stateAt(
                fixture.generator(),
                corner.bounds().originX() + centerX,
                glyphY,
                corner.bounds().originZ()));
        assertSame(fixture.connectorCap(), stateAt(
                fixture.generator(),
                corner.bounds().maxX(),
                glyphY,
                corner.bounds().originZ() + centerZ));
        assertSame(fixture.topologyPath(), stateAt(
                fixture.generator(),
                corner.bounds().originX() + centerX,
                glyphY,
                corner.bounds().originZ() + 1));
        assertSame(fixture.topologyPath(), stateAt(
                fixture.generator(),
                corner.bounds().originX() + centerX + 1,
                glyphY,
                corner.bounds().originZ() + centerZ));
        assertSame(fixture.topologyBase(), stateAt(
                fixture.generator(),
                corner.bounds().originX(),
                glyphY,
                corner.bounds().originZ()));
        assertFalse(fixture.topologyBase() == stateAt(
                fixture.generator(),
                corner.bounds().originX() + centerX,
                corner.bounds().originY(),
                corner.bounds().originZ() + centerZ));

        JigsawStudioBay blank = fixture.layout().get("workcell/blank");
        assertSame(fixture.topologyBase(), stateAt(
                fixture.generator(),
                blank.bounds().originX() + centerX,
                blank.bounds().originY() - 1,
                blank.bounds().originZ() + centerZ));
        JigsawStudioControlPosition control = fixture.layout().controlPosition();
        assertSame(fixture.controlChest(), stateAt(
                fixture.generator(), control.worldX(), control.worldY(), control.worldZ()));
    }

    @Test
    public void paintsEveryWorkcellAsAWhiteConcreteEdgeCuboid() {
        GeneratorFixture fixture = fixture(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(5, 4, 3),
                JigsawStudioVariantCatalog.empty());
        JigsawStudioBay workcell = fixture.layout().get("workcell/blank");
        int minimumX = workcell.bounds().originX() - 1;
        int maximumX = workcell.bounds().maxX() + 1;
        int minimumZ = workcell.bounds().originZ() - 1;
        int maximumZ = workcell.bounds().maxZ() + 1;
        int bottomY = workcell.bounds().originY();
        int topY = workcell.bounds().maxY() + 1;

        assertSame(fixture.frame(), stateAt(fixture.generator(), minimumX, bottomY, minimumZ));
        assertSame(fixture.frame(), stateAt(fixture.generator(), maximumX, bottomY, maximumZ));
        assertSame(fixture.frame(), stateAt(fixture.generator(), minimumX, topY, maximumZ));
        assertSame(fixture.frame(), stateAt(fixture.generator(), maximumX, topY, minimumZ));
        assertSame(fixture.frame(), stateAt(fixture.generator(), minimumX, bottomY + 1, minimumZ));
        assertFalse(fixture.frame() == stateAt(
                fixture.generator(),
                workcell.bounds().originX(),
                bottomY + 1,
                workcell.bounds().originZ()));
    }

    @Test
    public void glyphMathHandlesAllArchetypesAndSmallEvenAndOddCells() {
        int[] sizes = {1, 2, 3, 4, 7, 16};
        for (int size : sizes) {
            JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(size, 1, size);
            int center = size / 2;
            for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
                JigsawPlanarTopology topology = archetype.canonicalTopology();
                boolean centerPath = JigsawStudioGenerator.isTopologyPath(
                        topology, center, center, center, center);
                assertEquals(archetype != JigsawPlanarArchetype.BLANK, centerPath);
                if (topology.connects(JigsawPlanarDirection.NORTH)) {
                    assertTrue(JigsawStudioGenerator.isConnectorCap(
                            topology, dimensions, center, 0, center, center));
                }
                if (topology.connects(JigsawPlanarDirection.EAST)) {
                    assertTrue(JigsawStudioGenerator.isConnectorCap(
                            topology, dimensions, size - 1, center, center, center));
                }
            }
        }
    }

    @Test
    public void connectorCapsOnlyOccupyConnectedFaceCenters() {
        int[] sizes = {3, 4, 7, 16};
        for (int size : sizes) {
            JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(size, 1, size);
            int center = size / 2;
            for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
                JigsawPlanarTopology topology = archetype.canonicalTopology();
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        boolean expected = topology.connects(JigsawPlanarDirection.NORTH)
                                && x == center && z == 0
                                || topology.connects(JigsawPlanarDirection.EAST)
                                && x == size - 1 && z == center
                                || topology.connects(JigsawPlanarDirection.SOUTH)
                                && x == center && z == size - 1
                                || topology.connects(JigsawPlanarDirection.WEST)
                                && x == 0 && z == center;
                        assertEquals(expected, JigsawStudioGenerator.isConnectorCap(
                                topology, dimensions, x, z, center, center));
                    }
                }
            }
        }
    }

    @Test
    public void canonicalFiveByFivePathsMatchEveryArchetype() {
        Map<JigsawPlanarArchetype, List<String>> expected = Map.of(
                JigsawPlanarArchetype.BLANK, List.of(".....", ".....", ".....", ".....", "....."),
                JigsawPlanarArchetype.END, List.of("..#..", "..#..", "..#..", ".....", "....."),
                JigsawPlanarArchetype.STRAIGHT, List.of("..#..", "..#..", "..#..", "..#..", "..#.."),
                JigsawPlanarArchetype.CORNER, List.of("..#..", "..#..", "..###", ".....", "....."),
                JigsawPlanarArchetype.TEE, List.of("..#..", "..#..", "#####", ".....", "....."),
                JigsawPlanarArchetype.CROSS, List.of("..#..", "..#..", "#####", "..#..", "..#.."));

        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            List<String> rows = expected.get(archetype);
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 5; x++) {
                    boolean path = JigsawStudioGenerator.isTopologyPath(
                            archetype.canonicalTopology(), x, z, 2, 2);
                    assertEquals(rows.get(z).charAt(x) == '#', path);
                }
            }
        }
    }

    @Test
    public void clipsLargeGlyphPaintingToOneChunkFootprint() {
        GeneratorFixture fixture = fixture(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(128, 1, 128),
                JigsawStudioVariantCatalog.empty());
        JigsawStudioBay cross = fixture.layout().get("workcell/cross");
        int chunkX = (cross.bounds().originX() + 32) >> 4;
        int chunkZ = (cross.bounds().originZ() + 32) >> 4;
        RecordingCanvas chunk = new RecordingCanvas(-64, 320);

        fixture.generator().paintChunk(chunk, chunkX, chunkZ);

        int glyphBlocks = chunk.countState(fixture.topologyBase())
                + chunk.countState(fixture.topologyPath())
                + chunk.countState(fixture.connectorCap());
        assertEquals(256, glyphBlocks);
    }

    @Test
    public void rendersSourceVariantInCanonicalOrientationAndRotatesFinalState() {
        PackFixture pack = new PackFixture();
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new BlockPosition(2, 1, 1))
                .setDirection(JigsawDirection.EAST_POSITIVE_X)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("village/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("village/east")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        BlockState sourceBlock = BlockState.parse("minecraft:chest");
        BlockEntityData sourceTile = new BlockEntityData(new ResourceId("iris", "tile"), 1, new byte[]{1, 2, 3});
        pack.piece("village/east", piece);
        pack.object("village/east", object(3, 3, 3, new PlacedBlock(0, 0, 0, sourceBlock, sourceTile)));
        JigsawStudioVariant variant = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        GeneratorFixture fixture = fixture(
                pack,
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));

        JigsawStudioGenerator.RenderedBay rendered = fixture.generator().renderVariant(
                fixture.layout().get("workcell/end"), variant);

        assertTrue(rendered.valid());
        JigsawStudioGenerator.RenderedConnector rotated = rendered.connectors().getFirst();
        assertEquals(1, rotated.x());
        assertEquals(0, rotated.z());
        assertEquals(JigsawDirection.NORTH_NEGATIVE_Z, rotated.connector().getDirection());
        assertEquals("north_up", rotated.orientation());
        assertTrue(rotated.connector().getFinalState().contains("facing=north"));
        JigsawStudioGenerator.RenderedBlock rotatedBlock = rendered.blocks().getFirst();
        assertEquals(sourceTile, rotatedBlock.blockEntity());
    }

    @Test
    public void invalidRotatedFinalStateFailsRenderPrecisely() {
        PackFixture pack = new PackFixture();
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new BlockPosition(2, 1, 1))
                .setDirection(JigsawDirection.EAST_POSITIVE_X)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("village/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:not_a_real_block");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("village/east")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        pack.piece("village/east", piece);
        pack.object("village/east", object(3, 3, 3));
        JigsawStudioVariant variant = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        GeneratorFixture fixture = fixture(
                pack,
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));

        JigsawStudioGenerator.RenderedBay rendered = fixture.generator().renderVariant(
                fixture.layout().get("workcell/end"), variant);

        assertFalse(rendered.valid());
        assertTrue(rendered.failure().contains("final state 'minecraft:not_a_real_block' cannot be parsed and rotated"));
    }

    @Test
    public void renderCacheIncludesLoadGenerationAndSupportsExplicitInvalidation() {
        PackFixture pack = new PackFixture();
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("stronghold/hall")
                .setConnectors(new KList<>());
        pack.piece("stronghold/hall", piece);
        pack.object("stronghold/hall", object(3, 3, 3));
        JigsawStudioVariant variant = new JigsawStudioVariant(
                "stronghold/hall",
                "stronghold/hall",
                "",
                Optional.of(new JigsawStudioCellDimensions(3, 3, 3)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                false,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
        GeneratorFixture fixture = fixture(
                pack,
                JigsawStudioMode.SPATIAL_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));
        JigsawStudioBay workcell = fixture.layout().get(JigsawStudioLayout.SPATIAL_WORKCELL_ID);

        JigsawStudioGenerator.RenderedBay first = fixture.generator().renderVariant(workcell, variant, 4L);
        assertSame(first, fixture.generator().renderVariant(workcell, variant, 4L));
        JigsawStudioGenerator.RenderedBay nextGeneration = fixture.generator().renderVariant(workcell, variant, 5L);
        assertNotSame(first, nextGeneration);
        fixture.generator().invalidateRender(workcell.stableId(), variant.pieceKey());
        assertNotSame(nextGeneration, fixture.generator().renderVariant(workcell, variant, 5L));
    }

    @Test
    public void mapsAllVanillaJigsawOrientations() {
        assertEquals("north_up", JigsawStudioGenerator.orientation(
                JigsawDirection.NORTH_NEGATIVE_Z, JigsawDirection.UP_POSITIVE_Y));
        assertEquals("up_east", JigsawStudioGenerator.orientation(
                JigsawDirection.UP_POSITIVE_Y, JigsawDirection.EAST_POSITIVE_X));
        assertEquals("down_west", JigsawStudioGenerator.orientation(
                JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.WEST_NEGATIVE_X));
    }

    private static BlockState stateAt(
            JigsawStudioGenerator generator,
            int worldX,
            int worldY,
            int worldZ
    ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        RecordingCanvas chunk = new RecordingCanvas(-64, 320);
        generator.paintChunk(chunk, chunkX, chunkZ);
        return chunk.blockAt(worldX - (chunkX << 4), worldY, worldZ - (chunkZ << 4));
    }

    private static GeneratorFixture fixture(
            JigsawStudioMode mode,
            JigsawStudioCellDimensions dimensions,
            JigsawStudioVariantCatalog catalog
    ) {
        return fixture(new PackFixture(), mode, dimensions, catalog);
    }

    private static GeneratorFixture fixture(
            PackFixture pack,
            JigsawStudioMode mode,
            JigsawStudioCellDimensions dimensions,
            JigsawStudioVariantCatalog catalog
    ) {
        UUID requestId = UUID.randomUUID();
        JigsawStudioLayout layout = JigsawStudioLayout.create(mode, dimensions, catalog);
        JigsawStudioRequest request = new JigsawStudioRequest(
                requestId,
                "overworld",
                "test/structure",
                mode,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                pack.pack,
                Path.of("pack"),
                null);
        JigsawStudioSession session = new JigsawStudioSession(
                requestId,
                "overworld",
                "test/structure",
                layout);
        BlockState lightFloor = BlockState.parse("minecraft:smooth_stone");
        BlockState darkFloor = BlockState.parse("minecraft:polished_deepslate");
        BlockState frame = BlockState.parse("minecraft:white_concrete");
        BlockState topologyBase = BlockState.parse("minecraft:light_gray_wool");
        BlockState topologyPath = BlockState.parse("minecraft:red_wool");
        BlockState connectorCap = BlockState.parse("minecraft:sea_lantern");
        BlockState invalidMarker = BlockState.parse("minecraft:red_stained_glass");
        BlockState controlChest = BlockState.parse("minecraft:chest[facing=south,type=single,waterlogged=false]");
        JigsawStudioGenerator generator = new JigsawStudioGenerator(
                pack.pack,
                request,
                session,
                lightFloor,
                darkFloor,
                frame,
                topologyBase,
                topologyPath,
                connectorCap,
                invalidMarker,
                controlChest,
                JigsawStudioGeneratorTest::renderFinalState);
        return new GeneratorFixture(
                generator,
                layout,
                frame,
                topologyBase,
                topologyPath,
                connectorCap,
                controlChest);
    }

    private static JigsawStudioVariant planarVariant(String key, JigsawPlanarTopology topology) {
        return new JigsawStudioVariant(
                key,
                key,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(topology),
                true,
                false,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }

    private static String renderFinalState(
            String finalState,
            PieceTurn rotation,
            int quarterTurns
    ) {
        if (finalState.equals("minecraft:not_a_real_block")) {
            return null;
        }
        if (Math.floorMod(quarterTurns, 4) == 3) {
            return finalState.replace("facing=east", "facing=north");
        }
        return finalState;
    }

    private static TestObject object(int width, int height, int depth, PlacedBlock... placed) {
        BlockPosition center = new BlockPosition(width / 2, height / 2, depth / 2);
        List<ObjectBlock> blocks = new ArrayList<>();
        for (PlacedBlock block : placed) {
            blocks.add(new ObjectBlock(
                    new BlockPosition(block.x() - center.x(), block.y() - center.y(), block.z() - center.z()),
                    block.state(),
                    Optional.ofNullable(block.blockEntity())));
        }
        BlockBox bounds = new BlockBox(
                -center.x(), -center.y(), -center.z(),
                width - center.x(), height - center.y(), depth - center.z());
        GeneratedObject geometry = new GeneratedObject(bounds, new BlockPosition(0, 0, 0), blocks, List.of());
        return new TestObject(width, height, depth, center, geometry);
    }

    /** A block at its coordinates inside the object, counted from the object's own corner. */
    private record PlacedBlock(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
    }

    private record TestObject(int width, int height, int depth, BlockPosition center, GeneratedObject geometry)
            implements PlaceableObject {
        @Override
        public String name() {
            return "test-object";
        }
    }

    /** A pack whose pieces and objects a test sets by key, and whose block registry knows every block. */
    private static final class PackFixture {
        final ToolPack pack = mock(ToolPack.class);
        final PackResources resources = mock(PackResources.class);
        final PackObjects objects = mock(PackObjects.class);
        final BlockRegistry blocks = mock(BlockRegistry.class);

        PackFixture() {
            when(pack.resources()).thenReturn(resources);
            when(resources.objects()).thenReturn(objects);
            when(resources.blocks()).thenReturn(blocks);
            when(resources.idOf(anyString())).thenAnswer(invocation -> idOf(invocation.getArgument(0)));
            when(blocks.find(anyString())).thenAnswer(invocation ->
                    Optional.of(BlockState.parse(invocation.getArgument(0))));
            when(blocks.rotate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(blocks.tryRotate(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        }

        void piece(String key, IrisJigsawPiece piece) {
            when(resources.find(StructureResources.PIECES, idOf(key))).thenReturn(Optional.of(piece));
        }

        void object(String key, PlaceableObject object) {
            when(objects.object(idOf(key))).thenReturn(Optional.of(object));
        }

        private static ResourceId idOf(String key) {
            return ResourceId.ofPackKey(PACK_NAMESPACE, key);
        }
    }

    private record GeneratorFixture(
            JigsawStudioGenerator generator,
            JigsawStudioLayout layout,
            BlockState frame,
            BlockState topologyBase,
            BlockState topologyPath,
            BlockState connectorCap,
            BlockState controlChest
    ) {
    }

    private static final class RecordingCanvas implements ChunkCanvas {
        private final int minY;
        private final int maxY;
        private final Map<String, BlockState> blocks = new HashMap<>();

        private RecordingCanvas(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int maxY() {
            return maxY;
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            blocks.put(key(x, y, z), state);
        }

        @Override
        public void set(int x, int y, int z, BlockState state, BlockEntityData data) {
            set(x, y, z, state);
        }

        private BlockState blockAt(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        private int countState(BlockState state) {
            int count = 0;
            for (BlockState block : blocks.values()) {
                if (block == state) {
                    count++;
                }
            }
            return count;
        }

        private String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }
}
