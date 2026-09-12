package art.arcane.iris.generation.mantle;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleWriterOverlayTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private static final int X = 1;
    private static final int Y = 2;
    private static final int Z = 3;

    private MantleWriter writer;
    private Matter matter;
    private MantleChunk<Matter> chunk;
    private MatterSlice<PlatformBlockState> blockSlice;
    private MatterSlice<Identifier> identifierSlice;
    private MatterSlice<MatterCavern> cavernSlice;
    private MatterSlice<HydrologyCaveCell> hydrologySlice;
    private IrisDimension dimension;
    private EngineMantle engineMantle;
    private Mantle<Matter> mantle;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        engineMantle = mock(EngineMantle.class);
        when(engineMantle.getComplex()).thenReturn(mock(IrisComplex.class));
        Engine engine = mock(Engine.class);
        dimension = mock(IrisDimension.class);
        mantle = mock(Mantle.class);
        chunk = mock(MantleChunk.class);
        matter = mock(Matter.class);
        blockSlice = mock(MatterSlice.class);
        identifierSlice = mock(MatterSlice.class);
        cavernSlice = mock(MatterSlice.class);
        hydrologySlice = mock(MatterSlice.class);

        when(mantle.getWorldHeight()).thenReturn(64);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engine.getDimension()).thenReturn(dimension);
        when(mantle.getChunk(0, 0)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(chunk.getOrCreate(0)).thenReturn(matter);
        when(chunk.exists(0)).thenReturn(true);
        when(chunk.get(0)).thenReturn(matter);
        when(matter.hasSlice(PlatformBlockState.class)).thenReturn(true);
        when(matter.hasSlice(Identifier.class)).thenReturn(true);
        when(matter.hasSlice(MatterCavern.class)).thenReturn(true);
        when(matter.getSlice(PlatformBlockState.class)).thenReturn(blockSlice);
        when(matter.getSlice(Identifier.class)).thenReturn(identifierSlice);
        when(matter.getSlice(MatterCavern.class)).thenReturn(cavernSlice);
        doReturn(blockSlice).when(matter).slice(PlatformBlockState.class);
        doReturn(identifierSlice).when(matter).slice(Identifier.class);
        doReturn(cavernSlice).when(matter).slice(MatterCavern.class);
        doReturn(hydrologySlice).when(matter).getSlice(HydrologyCaveCell.class);

        writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
    }

    @Test
    public void existingCavernStillClearsBlockAndDeferredPlacement() {
        MatterCavern existing = new MatterCavern(true, "", (byte) 3);
        MatterCavern requested = new MatterCavern(true, "", (byte) 3);
        when(cavernSlice.get(X, Y, Z)).thenReturn(existing);

        assertFalse(writer.carveDataIfAbsent(X, Y, Z, requested));

        verify(blockSlice).set(X, Y, Z, null);
        verify(identifierSlice).set(X, Y, Z, null);
        verify(cavernSlice, never()).set(X, Y, Z, requested);
    }

    @Test
    public void absentCavernClearsOverlaysAndWritesMask() {
        MatterCavern requested = new MatterCavern(true, "", (byte) 3);

        assertTrue(writer.carveDataIfAbsent(X, Y, Z, requested));

        verify(blockSlice).set(X, Y, Z, null);
        verify(identifierSlice).set(X, Y, Z, null);
        verify(cavernSlice).set(X, Y, Z, requested);
    }

    @Test
    public void normalBlockReplacementClearsDeferredPlacement() {
        PlatformBlockState replacement = mock(PlatformBlockState.class);

        writer.setData(X, Y, Z, replacement);

        verify(identifierSlice).set(X, Y, Z, null);
        verify(blockSlice).set(X, Y, Z, replacement);
    }

    @Test
    public void customBlockWritesBaseAndIdentifierTogether() {
        PlatformBlockState base = mock(PlatformBlockState.class);
        PlatformBlockState custom = customState(base);
        Identifier identifier = Identifier.fromString("iris:custom_block");

        writer.set(X, Y, Z, custom);

        verify(blockSlice).set(X, Y, Z, base);
        verify(identifierSlice).set(X, Y, Z, identifier);
    }

    @Test
    public void protectedHydrologyRejectsBothPartsOfCustomBlock() {
        PlatformBlockState base = mock(PlatformBlockState.class);
        PlatformBlockState custom = customState(base);
        when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
        when(hydrologySlice.get(X, Y, Z))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));

        writer.set(X, Y, Z, custom);

        verify(blockSlice, never()).set(X, Y, Z, base);
        verify(identifierSlice, never()).set(anyInt(), anyInt(), anyInt(), any(Identifier.class));
    }

    @Test
    public void bedrockAndWorldBoundsRejectBothPartsOfCustomBlock() {
        PlatformBlockState base = mock(PlatformBlockState.class);
        PlatformBlockState custom = customState(base);
        when(dimension.isBedrock()).thenReturn(true);

        writer.set(X, 0, Z, custom);
        writer.set(X, 64, Z, custom);

        verify(blockSlice, never()).set(anyInt(), anyInt(), anyInt(), any(PlatformBlockState.class));
        verify(identifierSlice, never()).set(anyInt(), anyInt(), anyInt(), any(Identifier.class));
    }

    @Test
    public void protectedHydrologyRejectsLaterBlockAndCavernWrites() {
        PlatformBlockState replacement = mock(PlatformBlockState.class);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 3);
        when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
        when(hydrologySlice.get(X, Y, Z))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));

        writer.setData(X, Y, Z, replacement);
        assertFalse(writer.carveDataIfAbsent(X, Y, Z, cavern));
        writer.setForcedCarve(X, Y, Z, cavern);

        verify(blockSlice, never()).set(X, Y, Z, replacement);
        verify(blockSlice, never()).set(X, Y, Z, null);
        verify(identifierSlice, never()).set(X, Y, Z, null);
        verify(cavernSlice, never()).set(X, Y, Z, cavern);
    }

    @Test
    public void historicalChunkRejectsAllMutationKinds() {
        writer.close();
        IrisComplex complex = mock(IrisComplex.class);
        TransitionGenerationPlan transition = mock(TransitionGenerationPlan.class);
        when(engineMantle.getComplex()).thenReturn(complex);
        when(complex.getTransitionGenerationPlan()).thenReturn(transition);
        when(transition.isHistoricalBlock(X, Z)).thenReturn(true);
        writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
        PlatformBlockState replacement = mock(PlatformBlockState.class);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 3);

        writer.setData(X, Y, Z, replacement);
        assertFalse(writer.setDataIfAbsent(X, Y, Z, cavern));
        assertFalse(writer.carveDataIfAbsent(X, Y, Z, cavern));
        writer.setForcedCarve(X, Y, Z, cavern);
        writer.clearBlock(X, Y, Z);
        writer.clearData(X, Y, Z, PlatformBlockState.class);

        verify(blockSlice, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(cavernSlice, never()).set(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void hydrologyOverridesBaselineCarvedQueriesWithoutChangingIt() {
        MatterCavern baseline = new MatterCavern(true, "", (byte) 0);
        when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
        when(cavernSlice.get(X, Y, Z)).thenReturn(baseline);
        when(hydrologySlice.get(X, Y, Z))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
        when(hydrologySlice.get(X, Y + 1, Z))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR));

        assertFalse(writer.isCarved(X, Y, Z));
        assertTrue(writer.isCarved(X, Y + 1, Z));

        byte[] column = writer.getCarvedColumn(X, Z, Y + 2);
        assertEquals(0, column[Y]);
        assertEquals(1, column[Y + 1]);
        assertEquals(0, baseline.getLiquid());
    }

    private static PlatformBlockState customState(PlatformBlockState base) {
        PlatformBlockState custom = mock(PlatformBlockState.class);
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn("iris:custom_block");
        when(custom.placementBaseState()).thenReturn(base);
        return custom;
    }
}
