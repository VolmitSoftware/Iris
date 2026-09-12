package art.arcane.iris.studio.generation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.studio.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.studio.jigsaw.JigsawPlanarDirection;
import art.arcane.iris.studio.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.studio.jigsaw.JigsawStudioActivation;
import art.arcane.iris.studio.jigsaw.JigsawStudioBay;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.studio.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioMode;
import art.arcane.iris.studio.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.studio.jigsaw.JigsawStudioSession;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariant;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.generation.chunk.TerrainChunk;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class JigsawStudioGeneratorTest {
    @Test
    public void checkerboardFloorUsesTheSameFourBlockTilesAtNegativeCoordinates() {
        assertTrue(JigsawStudioGenerator.isLightFloor(0, 0));
        assertTrue(JigsawStudioGenerator.isLightFloor(3, 3));
        assertFalse(JigsawStudioGenerator.isLightFloor(4, 0));
        assertFalse(JigsawStudioGenerator.isLightFloor(-1, 0));
        assertTrue(JigsawStudioGenerator.isLightFloor(-1, -1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void connectorBlocksAreHiddenByDefaultAndCanBeShownPerWorkcell() {
        IrisData source = mock(IrisData.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new IrisPosition(1, 1, 1))
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("test/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:stone");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("test/room")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        PlatformBlockState stone = mock(PlatformBlockState.class);
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 1, 1, stone);
        when(pieceLoader.load("test/room", false)).thenReturn(piece);
        when(objectLoader.load("test/room", false)).thenReturn(object);
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
                source,
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

        PlatformBlockState marker = mock(PlatformBlockState.class);
        fixture.generator().getSession().setConnectorsVisible(workcell.stableId(), true);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("minecraft:jigsaw[orientation=north_up]")).thenReturn(marker);
            assertSame(marker, stateAt(fixture.generator(), worldX, worldY, worldZ));
        }
    }

    @Test
    public void serviceRegistrationIsPublishedAfterTheRegistrationFinishes() throws Exception {
        GeneratorFixture fixture = fixture(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                JigsawStudioVariantCatalog.empty());
        CountDownLatch registrationStarted = new CountDownLatch(1);
        CountDownLatch releaseRegistration = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        AtomicInteger registrations = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> fixture.generator().publishServiceRegistration(() -> {
                registrations.incrementAndGet();
                registrationStarted.countDown();
                await(releaseRegistration);
            }));
            assertTrue(registrationStarted.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                fixture.generator().publishServiceRegistration(registrations::incrementAndGet);
                secondFinished.countDown();
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondFinished.await(250, TimeUnit.MILLISECONDS));

            releaseRegistration.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertEquals(1, registrations.get());
            assertEquals(0L, secondFinished.getCount());
        } finally {
            releaseRegistration.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
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
        RecordingTerrainChunk chunk = new RecordingTerrainChunk(-64, 320);

        fixture.generator().paintChunk(chunk, chunkX, chunkZ);

        int glyphBlocks = chunk.countState(fixture.topologyBase())
                + chunk.countState(fixture.topologyPath())
                + chunk.countState(fixture.connectorCap());
        assertEquals(256, glyphBlocks);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rendersSourceVariantInCanonicalOrientationAndRotatesFinalState() {
        IrisData source = mock(IrisData.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new IrisPosition(2, 1, 1))
                .setDirection(IrisDirection.EAST_POSITIVE_X)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("village/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("village/east")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        IrisObject object = new IrisObject(3, 3, 3);
        PlatformBlockState sourceBlock = mock(PlatformBlockState.class);
        when(sourceBlock.nativeHandle()).thenReturn(mock(BlockData.class));
        KMap<String, Object> tileProperties = new KMap<>();
        tileProperties.put("CustomName", "QA Chest");
        TileData sourceTile = new TileData("minecraft:chest", tileProperties);
        object.setUnsigned(0, 0, 0, sourceBlock);
        object.setUnsignedTile(0, 0, 0, sourceTile);
        when(pieceLoader.load("village/east", false)).thenReturn(piece);
        when(objectLoader.load("village/east", false)).thenReturn(object);
        JigsawStudioVariant variant = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        GeneratorFixture fixture = fixture(
                source,
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));

        JigsawStudioGenerator.RenderedBay rendered = fixture.generator().renderVariant(
                fixture.layout().get("workcell/end"), variant);

        assertTrue(rendered.valid());
        JigsawStudioGenerator.RenderedConnector rotated = rendered.connectors().getFirst();
        assertEquals(1, rotated.x());
        assertEquals(0, rotated.z());
        assertEquals(IrisDirection.NORTH_NEGATIVE_Z, rotated.connector().getDirection());
        assertEquals("north_up", rotated.orientation());
        assertTrue(rotated.connector().getFinalState().contains("facing=north"));
        JigsawStudioGenerator.RenderedBlock rotatedBlock = rendered.blocks().getFirst();
        assertEquals(sourceTile, rotatedBlock.tileData());
        assertNotSame(sourceTile, rotatedBlock.tileData());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void invalidRotatedFinalStateFailsRenderPrecisely() {
        IrisData source = mock(IrisData.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new IrisPosition(2, 1, 1))
                .setDirection(IrisDirection.EAST_POSITIVE_X)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("village/start")
                .setName("door")
                .setTargetName("door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:not_a_real_block");
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("village/east")
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        when(pieceLoader.load("village/east", false)).thenReturn(piece);
        when(objectLoader.load("village/east", false)).thenReturn(new IrisObject(3, 3, 3));
        JigsawStudioVariant variant = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        GeneratorFixture fixture = fixture(
                source,
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(3, 3, 3),
                new JigsawStudioVariantCatalog(List.of(variant)));

        JigsawStudioGenerator.RenderedBay rendered = fixture.generator().renderVariant(
                fixture.layout().get("workcell/end"), variant);

        assertFalse(rendered.valid());
        assertTrue(rendered.failure().contains("final state 'minecraft:not_a_real_block' cannot be parsed and rotated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void renderCacheIncludesLoadGenerationAndSupportsExplicitInvalidation() {
        IrisData source = mock(IrisData.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject("stronghold/hall")
                .setConnectors(new KList<>());
        when(pieceLoader.load("stronghold/hall", false)).thenReturn(piece);
        when(objectLoader.load("stronghold/hall", false)).thenReturn(new IrisObject(3, 3, 3));
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
                source,
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
                IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.UP_POSITIVE_Y));
        assertEquals("up_east", JigsawStudioGenerator.orientation(
                IrisDirection.UP_POSITIVE_Y, IrisDirection.EAST_POSITIVE_X));
        assertEquals("down_west", JigsawStudioGenerator.orientation(
                IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.WEST_NEGATIVE_X));
    }

    private static PlatformBlockState stateAt(
            JigsawStudioGenerator generator,
            int worldX,
            int worldY,
            int worldZ
    ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        RecordingTerrainChunk chunk = new RecordingTerrainChunk(-64, 320);
        generator.paintChunk(chunk, chunkX, chunkZ);
        return chunk.getBlockData(worldX - (chunkX << 4), worldY, worldZ - (chunkZ << 4));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for Jigsaw Studio registration");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Jigsaw Studio registration", exception);
        }
    }

    private static GeneratorFixture fixture(
            JigsawStudioMode mode,
            JigsawStudioCellDimensions dimensions,
            JigsawStudioVariantCatalog catalog
    ) {
        return fixture(mock(IrisData.class), mode, dimensions, catalog);
    }

    private static GeneratorFixture fixture(
            IrisData source,
            JigsawStudioMode mode,
            JigsawStudioCellDimensions dimensions,
            JigsawStudioVariantCatalog catalog
    ) {
        Engine engine = mock(Engine.class);
        UUID requestId = UUID.randomUUID();
        JigsawStudioLayout layout = JigsawStudioLayout.create(mode, dimensions, catalog);
        JigsawStudioActivation.Request request = new JigsawStudioActivation.Request(
                requestId,
                "overworld",
                "test/structure",
                mode,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                null);
        JigsawStudioSession session = new JigsawStudioSession(
                requestId,
                "overworld",
                "test/structure",
                layout);
        PlatformBlockState lightFloor = mock(PlatformBlockState.class);
        PlatformBlockState darkFloor = mock(PlatformBlockState.class);
        PlatformBlockState frame = mock(PlatformBlockState.class);
        PlatformBlockState topologyBase = mock(PlatformBlockState.class);
        PlatformBlockState topologyPath = mock(PlatformBlockState.class);
        PlatformBlockState connectorCap = mock(PlatformBlockState.class);
        PlatformBlockState invalidMarker = mock(PlatformBlockState.class);
        PlatformBlockState controlChest = mock(PlatformBlockState.class);
        JigsawStudioGenerator generator = new JigsawStudioGenerator(
                engine,
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
            IrisObjectRotation rotation,
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

    private record GeneratorFixture(
            JigsawStudioGenerator generator,
            JigsawStudioLayout layout,
            PlatformBlockState frame,
            PlatformBlockState topologyBase,
            PlatformBlockState topologyPath,
            PlatformBlockState connectorCap,
            PlatformBlockState controlChest
    ) {
    }

    private static final class RecordingTerrainChunk implements TerrainChunk {
        private final int minHeight;
        private final int maxHeight;
        private final Map<String, PlatformBlockState> blocks = new HashMap<>();

        private RecordingTerrainChunk(int minHeight, int maxHeight) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        @Override
        public PlatformBiome getBiome(int x, int y, int z) {
            return null;
        }

        @Override
        public void setBiome(int x, int y, int z, PlatformBiome biome) {
        }

        @Override
        public int getMinHeight() {
            return minHeight;
        }

        @Override
        public int getMaxHeight() {
            return maxHeight;
        }

        @Override
        public void setBlock(int x, int y, int z, PlatformBlockState blockData) {
            blocks.put(key(x, y, z), blockData);
        }

        @Override
        public void setRegion(
                int xMin,
                int yMin,
                int zMin,
                int xMax,
                int yMax,
                int zMax,
                PlatformBlockState blockData
        ) {
            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; y++) {
                    for (int z = zMin; z < zMax; z++) {
                        setBlock(x, y, z, blockData);
                    }
                }
            }
        }

        @Override
        public PlatformBlockState getBlockData(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        @Override
        public ChunkData getChunkData() {
            return null;
        }

        private int countState(PlatformBlockState state) {
            int count = 0;
            for (PlatformBlockState block : blocks.values()) {
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
