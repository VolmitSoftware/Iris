package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.structure.StructureCarveShape;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisStructureComponentCarveWaterTest {
    private static final int RIVER_BED = 60;
    private static final int RIVER_HEAD = 64;
    private static final int LAND = 70;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private IrisStructureComponent component;
    private MantleWriter writer;
    private IrisSettings previousSettings;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        EngineMantle engineMantle = mock(EngineMantle.class);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<WaterRole> roles = mock(ProceduralStream.class);
        ProceduralStream<Integer> heights = mock(ProceduralStream.class);
        ProceduralStream<Double> heads = mock(ProceduralStream.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getComplex()).thenReturn(complex);
        when(engineMantle.getData()).thenReturn(mock(IrisData.class));
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(roles.get(anyDouble(), anyDouble())).thenAnswer(call -> river(call.getArgument(0)) ? WaterRole.CHANNEL : WaterRole.NONE);
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(call -> river(call.getArgument(0)) ? RIVER_BED : LAND);
        when(heads.getDouble(anyDouble(), anyDouble())).thenAnswer(call -> river(call.getArgument(0)) ? (double) RIVER_HEAD : 63D);
        when(complex.getWaterRoleStream()).thenReturn(roles);
        when(complex.getRoundedHeighteightStream()).thenReturn(heights);
        when(complex.getWaterHeadStream()).thenReturn(heads);
        component = new IrisStructureComponent(engineMantle);
        writer = mock(MantleWriter.class);
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void boreSkipsRiverColumnsAndTheirBanks() {
        component.boreStructure(writer, box(), terrain(StructureTerrainMode.BORE, StructureCarveShape.BOX));

        assertRiverKept();
    }

    @Test
    public void forceCarveBoxSkipsRiverColumnsAndTheirBanks() {
        component.forceCarveStructure(writer, box(), terrain(StructureTerrainMode.FORCE_CARVE, StructureCarveShape.BOX));

        assertRiverKept();
    }

    @Test
    public void roundedForceCarveSkipsRiverColumnsAndTheirBanks() {
        component.forceCarveStructure(writer, slab(), terrain(StructureTerrainMode.FORCE_CARVE, StructureCarveShape.ROUNDED));

        verify(writer, never()).setForcedCarve(eq(3), eq(60), eq(1), any(MatterCavern.class));
        verify(writer, never()).setForcedCarve(eq(1), eq(60), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(3), eq(55), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(0), eq(60), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(8), eq(60), eq(1), any(MatterCavern.class));
    }

    @Test
    public void piecesPlaceInsideAWaterAwareObjectPlacement() {
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        IrisStructurePlacement placement = new IrisStructurePlacement();

        component.placePieces(writer, placement, structure, slab(), new RNG(3L));
        placement.setAllowWater(true);
        component.placePieces(writer, placement, structure, slab(), new RNG(3L));

        InOrder order = inOrder(writer);
        order.verify(writer).beginObjectPlacement(ObjectWaterRule.KEEP_OUT);
        order.verify(writer, calls(1)).set(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        order.verify(writer).endObjectPlacement();
        order.verify(writer).beginObjectPlacement(ObjectWaterRule.ENTERS_WATER);
        order.verify(writer, calls(1)).set(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        order.verify(writer).endObjectPlacement();
    }

    @Test
    public void aPieceAtWorldYMinusOneKeepsItsExplicitHeight() {
        KList<PlacedStructurePiece> pieces = slabAt(-1);
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);

        component.placePieces(writer, new IrisStructurePlacement(), structure, pieces, new RNG(3L));

        verify(writer, atLeastOnce()).set(anyInt(), eq(63), anyInt(), any(NativeBlockState.class));
        verify(writer, never()).set(anyInt(), intThat(y -> y != 63), anyInt(), any(NativeBlockState.class));
    }

    @Test
    public void aFailureInsidePlacePiecesStillEndsTheObjectPlacement() {
        IllegalStateException failure = new IllegalStateException("piece failed");
        doThrow(failure).when(writer).set(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> component.placePieces(writer, new IrisStructurePlacement(), structure, slab(), new RNG(3L)));

        assertSame(failure, thrown);
        InOrder order = inOrder(writer);
        order.verify(writer).beginObjectPlacement(ObjectWaterRule.KEEP_OUT);
        order.verify(writer).endObjectPlacement();
    }

    private void assertRiverKept() {
        verify(writer, never()).setForcedCarve(eq(3), eq(62), eq(1), any(MatterCavern.class));
        verify(writer, never()).setForcedCarve(eq(4), eq(68), eq(1), any(MatterCavern.class));
        verify(writer, never()).setForcedCarve(eq(1), eq(58), eq(1), any(MatterCavern.class));
        verify(writer, never()).setForcedCarve(eq(6), eq(64), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(3), eq(55), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(1), eq(66), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(0), eq(62), eq(1), any(MatterCavern.class));
        verify(writer).setForcedCarve(eq(7), eq(62), eq(1), any(MatterCavern.class));
    }

    private static boolean river(Object x) {
        int blockX = (int) Math.floor(((Number) x).doubleValue());
        return blockX == 3 || blockX == 4;
    }

    private static IrisStructureTerrain terrain(StructureTerrainMode mode, StructureCarveShape shape) {
        return new IrisStructureTerrain()
                .setMode(mode)
                .setShape(shape)
                .setFloorPadding(10)
                .setCeilingPadding(4);
    }

    private static KList<PlacedStructurePiece> box() {
        return new KList<>(new PlacedStructurePiece(null, null, 4, -4, 1, IrisObjectRotation.of(0, 0, 0),
                0, -4, 0, 8, 0, 2));
    }

    private static KList<PlacedStructurePiece> slab() {
        return slabAt(-4);
    }

    private static KList<PlacedStructurePiece> slabAt(int y) {
        NativeBlockState stone = mock(NativeBlockState.class);
        when(stone.isSolid()).thenReturn(true);
        when(stone.key()).thenReturn("minecraft:stone");
        when(stone.materialKey()).thenReturn("minecraft:stone");
        IrisObject object = new IrisObject(9, 1, 3);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 3; z++) {
                object.setUnsigned(x, 0, z, stone);
            }
        }
        return new KList<>(new PlacedStructurePiece(null, object, 4, y, 1, IrisObjectRotation.of(0, 0, 0),
                0, y, 0, 8, y, 2));
    }
}
