package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.structure.object.HydrologyWaterFixture;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisStructureComponentWaterTest {
    private static final int X = 4;
    private static final int Z = 4;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private final HydrologyWaterFixture world = new HydrologyWaterFixture();
    private final List<Write> writes = new ArrayList<>();
    private final Map<Integer, Matter> sections = new HashMap<>();
    private IrisSettings previousSettings;
    private MantleWriter writer;
    private IrisStructureComponent component;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        Engine engine = world.data().getEngine();
        IrisComplex complex = world.complex();
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        ProceduralStream<Integer> floors = mock(ProceduralStream.class);
        when(floors.get(anyDouble(), anyDouble())).thenAnswer(call -> world.floorAt(block(call.getArgument(0)), block(call.getArgument(1))));
        when(complex.getRoundedHeighteightStream()).thenReturn(floors);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.raster(anyInt(), anyInt())).thenReturn(raster);
        when(raster.role(anyInt(), anyInt())).thenAnswer(call -> complex.getWaterRoleStream().get((double) call.<Integer>getArgument(0), (double) call.<Integer>getArgument(1)));
        when(raster.waterHead(anyInt(), anyInt())).thenAnswer(call -> world.headAt(call.getArgument(0), call.getArgument(1)));
        when(raster.terrainHeight(anyInt(), anyInt())).thenAnswer(call -> world.floorAt(call.getArgument(0), call.getArgument(1)));
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getComplex()).thenReturn(complex);
        when(engineMantle.getData()).thenReturn(world.data());
        HydrologyWaterFixture.Placer heights = world.placer();
        when(engineMantle.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenAnswer(call -> heights.getHighest(call.getArgument(0), call.getArgument(1), world.data(), call.<Boolean>getArgument(3)));
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getWorldHeight()).thenReturn(256);
        when(mantle.getChunk(0, 0)).thenReturn(chunk);
        when(mantle.useChunk(0, 0)).thenAnswer(call -> chunk.use());
        when(chunk.use()).thenReturn(chunk);
        when(chunk.getOrCreate(anyInt())).thenAnswer(call -> section(call.getArgument(0)));
        writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
        component = new IrisStructureComponent(engineMantle);
    }

    @After
    public void tearDown() {
        writer.close();
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void aForcedPieceFullyInsideARiverWritesEveryBlock() {
        world.water(WaterRole.CHANNEL, 9, 13);

        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        assertEquals("every block of the raft lands", 10, writes.size());
        assertEquals(world.headAt(X, Z), lowestWriteAt(X, Z));
        assertEquals(world.headAt(X - 1, Z - 1), lowestWriteAt(X - 1, Z - 1));
    }

    @Test
    public void aForcedPieceTurnedTowardsTheSlopeWritesThroughTheRiver() {
        world.water(WaterRole.CHANNEL, 9, 13);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(invocation -> (double) world.floorAt(
                block(invocation.getArgument(0)), block(invocation.getArgument(1))));
        when(world.complex().getHeightStream()).thenReturn(heights);
        IrisObject object = HydrologyWaterFixture.raft();
        IrisObjectPlacement config = IrisStructureComponent.piecePlacement(new IrisStructure(), piece(object), ObjectPlaceMode.CENTER_HEIGHT);
        config.setRotateTowardsSlope(true);

        writer.beginObjectPlacement(config.waterRule());
        int result = object.place(X, Z, writer, config, new RNG(7L), world.data());
        writer.endObjectPlacement();

        assertTrue(result >= 0);
        assertEquals(10, writes.size());
    }

    @Test
    public void aForcedPieceWritesAcrossWetSupportColumns() {
        world.column(X + 1, Z, WaterRole.CHANNEL, 15, 19);
        world.column(X - 1, Z, WaterRole.SHORE, 15, 19);
        world.column(X, Z + 1, WaterRole.DAM, 15, 19);

        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        int anchorLayer = lowestWriteAt(X, Z);
        assertEquals(world.floorAt(X, Z), anchorLayer);
        assertEquals("the river column keeps the piece's block", anchorLayer, lowestWriteAt(X + 1, Z));
    }

    @Test
    public void replaceSourceKeepsAForcedPieceOverWaterWithoutFailingTheChunk() {
        world.water(WaterRole.CHANNEL, 9, 13);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);

        int failed = placeSinglePiece(placement);

        IrisStructureComponent.requireAppliedPieces(resolved(placement), 0, 0, failed);
        assertEquals(0, failed);
        assertEquals(10, writes.size());
    }

    @Test
    public void aForcedPieceWritesAcrossAFallWhetherOrNotItAllowsWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(X + 1, Z, WaterRole.CHANNEL, 9, 16);

        assertEquals(0, placeSinglePiece(new IrisStructurePlacement()));
        assertEquals(10, writes.size());
        writes.clear();

        assertEquals(0, placeSinglePiece(new IrisStructurePlacement().setAllowWater(true)));
        assertEquals(10, writes.size());
    }

    @Test
    public void aForcedPiecePlacesOnDryLand() {
        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        assertEquals(10, writes.size());
    }

    @Test
    public void objectsThatForcePlaceThemselvesWriteThroughUnevenWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(X + 1, Z, WaterRole.CHANNEL, 9, 16);
        IrisObject object = HydrologyWaterFixture.raft();
        IrisObjectPlacement config = new IrisObjectPlacement().setForcePlace(true);

        writer.beginObjectPlacement(config.waterRule());
        int result = object.place(X, Z, writer, config, new RNG(7L), world.data());
        writer.endObjectPlacement();

        assertTrue(result >= 0);
        assertEquals("every block lands over the channel", 10, writes.size());
        assertEquals(world.headAt(X, Z), lowestWriteAt(X, Z));
    }

    @Test
    public void unforcedPiecesKeepTheVeto() {
        world.water(WaterRole.CHANNEL, 9, 13);
        IrisObject object = HydrologyWaterFixture.raft();
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        int head = world.headAt(X, Z);
        PlacedStructurePiece explicit = new PlacedStructurePiece(null, object, X, head + 1, Z, IrisObjectRotation.of(0, 0, 0),
                X - 1, head, Z - 1, X + 1, head + 2, Z + 1);

        assertEquals(0, component.placePieces(writer, new IrisStructurePlacement(), structure, new KList<>(explicit), new RNG(7L)));

        assertFalse("the block above the water places", writes.isEmpty());
        assertNoWaterOverwritten();
        writes.clear();

        assertEquals(0, component.placePieces(writer, new IrisStructurePlacement().setAllowWater(true), structure, new KList<>(explicit), new RNG(7L)));
        assertTrue("allowWater fills the water", writes.stream().anyMatch(write -> write.y() == head));
    }

    @Test
    public void undergroundPiecesKeepTheVeto() {
        world.water(WaterRole.CHANNEL, 9, 13);
        IrisObject object = HydrologyWaterFixture.raft();
        int head = world.headAt(X, Z);
        PlacedStructurePiece explicit = new PlacedStructurePiece(null, object, X, head + 1, Z, IrisObjectRotation.of(0, 0, 0),
                X - 1, head, Z - 1, X + 1, head + 2, Z + 1);
        for (ObjectPlaceMode mode : List.of(ObjectPlaceMode.ORGANIC_STILT, ObjectPlaceMode.CEILING_HANG)) {
            writes.clear();
            IrisStructure structure = new IrisStructure().setPlaceMode(mode);
            IrisStructurePlacement placement = new IrisStructurePlacement().setUnderground(true);

            assertEquals(mode.name(), 0, component.placePieces(writer, placement, structure, new KList<>(explicit), new RNG(7L)));

            assertFalse(mode.name(), writes.isEmpty());
            assertNoWaterOverwritten();
        }
    }

    @Test
    public void unforcedObjectsStayOffRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        IrisObject object = HydrologyWaterFixture.raft();

        int result = object.place(X, Z, writer, new IrisObjectPlacement(), new RNG(7L), world.data());

        assertEquals(-1, result);
        assertTrue(writes.isEmpty());
    }

    private int placeSinglePiece(IrisStructurePlacement placement) {
        IrisObject object = HydrologyWaterFixture.raft();
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        return component.placePieces(writer, placement, structure, new KList<>(piece(object)), new RNG(7L));
    }

    private IrisStructureLocator.ResolvedPlacement resolved(IrisStructurePlacement placement) {
        IrisObject object = HydrologyWaterFixture.raft();
        return new IrisStructureLocator.ResolvedPlacement(placement, "test/raft", new IrisStructure(),
                new KList<>(piece(object)), new RNG(7L), X, 0, Z, false);
    }

    private void assertNoWaterOverwritten() {
        for (Write write : writes) {
            WaterRole role = world.complex().getWaterRoleStream().get((double) write.x(), (double) write.z());
            if (role.wet()) {
                assertFalse(write + " overwrites river water",
                        write.y() > world.floorAt(write.x(), write.z()) && write.y() <= world.headAt(write.x(), write.z()));
            }
        }
    }

    private int lowestWriteAt(int x, int z) {
        int lowest = Integer.MAX_VALUE;
        for (Write write : writes) {
            if (write.x() == x && write.z() == z) {
                lowest = Math.min(lowest, write.y());
            }
        }
        return lowest;
    }

    @SuppressWarnings("unchecked")
    private Matter section(int index) {
        return sections.computeIfAbsent(index, ignored -> {
            Matter matter = mock(Matter.class);
            MatterSlice<NativeBlockState> blocks = mock(MatterSlice.class);
            doAnswer(call -> {
                writes.add(new Write(call.getArgument(0), (index << 4) | call.<Integer>getArgument(1), call.getArgument(2)));
                return null;
            }).when(blocks).set(anyInt(), anyInt(), anyInt(), any());
            when(matter.getClass(any())).thenAnswer(call -> call.getArgument(0).getClass());
            doReturn(blocks).when(matter).slice(NativeBlockState.class);
            doReturn(mock(MatterSlice.class)).when(matter).getSlice(Identifier.class);
            return matter;
        });
    }

    private static PlacedStructurePiece piece(IrisObject object) {
        return new PlacedStructurePiece(null, object, X, 0, Z, IrisObjectRotation.of(0, 0, 0), X - 1, 0, Z - 1, X + 1, 2, Z + 1);
    }

    private static int block(Object coordinate) {
        return (int) Math.floor(((Number) coordinate).doubleValue());
    }

    private record Write(int x, int y, int z) {
    }
}
