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
    public void forcedSinglePieceStructureStaysOutOfRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);

        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        assertFalse("the part of the piece above the water places", writes.isEmpty());
        assertNoWaterOverwritten();
    }

    @Test
    public void forcedSinglePieceStructureTurnedTowardsTheSlopeStaysOutOfRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(invocation -> (double) world.floorAt(
                block(invocation.getArgument(0)), block(invocation.getArgument(1))));
        when(world.complex().getHeightStream()).thenReturn(heights);
        IrisObject object = HydrologyWaterFixture.raft();
        IrisObjectPlacement config = IrisStructureComponent.piecePlacement(new IrisStructure(), piece(object), ObjectPlaceMode.CENTER_HEIGHT);
        config.setRotateTowardsSlope(true);

        writer.beginObjectPlacement(false);
        int result = object.place(X, Z, writer, config, new RNG(7L), world.data());
        writer.endObjectPlacement();

        assertTrue(result >= 0);
        assertFalse(writes.isEmpty());
        assertNoWaterOverwritten();
    }

    @Test
    public void aFewWetSupportColumnsDoNotStopAForcedSinglePiece() {
        world.column(X + 1, Z, WaterRole.CHANNEL, 15, 19);
        world.column(X - 1, Z, WaterRole.SHORE, 15, 19);
        world.column(X, Z + 1, WaterRole.DAM, 15, 19);

        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        int anchorLayer = lowestWriteAt(X, Z);
        assertEquals("the anchor column keeps its block", world.floorAt(X, Z), anchorLayer);
        assertTrue("the anchor layer sits inside the river column's water",
                anchorLayer > world.floorAt(X + 1, Z) && anchorLayer <= world.headAt(X + 1, Z));
        assertNoWaterOverwritten();
    }

    @Test
    public void replaceSourceKeepsAForcedSinglePieceOverWaterWithoutFailingTheChunk() {
        world.water(WaterRole.CHANNEL, 9, 13);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);

        int failed = placeSinglePiece(placement);

        IrisStructureComponent.requireAppliedPieces(resolved(placement), 0, 0, failed);
        assertEquals(0, failed);
        assertNoWaterOverwritten();
    }

    @Test
    public void replaceSourceKeepsAWaterPieceOnUnevenWaterWithoutFailingTheChunk() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(X + 1, Z, WaterRole.CHANNEL, 9, 16);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE)
                .setAllowWater(true);

        int failed = placeSinglePiece(placement);

        IrisStructureComponent.requireAppliedPieces(resolved(placement), 0, 0, failed);
        assertEquals(0, failed);
        assertEquals("a piece that allows water fills it", world.headAt(X - 1, Z - 1), lowestWriteAt(X - 1, Z - 1));
    }

    @Test
    public void forcedSinglePieceStructureThatAllowsWaterPlacesOnFlatRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);

        int failed = placeSinglePiece(new IrisStructurePlacement().setAllowWater(true));

        assertEquals(0, failed);
        assertEquals(world.headAt(X, Z), lowestWriteAt(X, Z));
    }

    @Test
    public void forcedSinglePieceStructureStillPlacesOnDryLand() {
        int failed = placeSinglePiece(new IrisStructurePlacement());

        assertEquals(0, failed);
        assertEquals(10, writes.size());
    }

    @Test
    public void objectsThatForcePlaceThemselvesStillStayOffUnevenWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(X + 1, Z, WaterRole.CHANNEL, 9, 16);
        IrisObject object = HydrologyWaterFixture.raft();

        int result = object.place(X, Z, writer, new IrisObjectPlacement().setForcePlace(true), new RNG(7L), world.data());

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
