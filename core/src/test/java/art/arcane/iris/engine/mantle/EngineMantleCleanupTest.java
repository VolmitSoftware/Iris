package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineMantleCleanupTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @After
    public void unbindPlatform() {
        MantleSliceRetention.clearForTesting();
    }

    @Test
    public void retainedSlicesSurviveCoveredCleanup() {
        MantleSliceRetention.retain(MatterCavern.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();
        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);

        fixture.engineMantle().cleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(MatterCavern.class);
        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void retainedSlicesSurvivePregenForceCleanup() {
        MantleSliceRetention.retain(TreeBlockMaterial.class.getCanonicalName());
        MantleSliceRetention.retain(String.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(TreeBlockMaterial.class);
        verify(fixture.chunk(), never()).deleteSlices(String.class);
        verify(fixture.chunk()).deleteSlices(MatterCavern.class);
        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void bukkitBootRetentionsSurvivePregen() {
        // Exactly the slice types the Bukkit plugin registers at boot: markers and tree materials
        // must keep working in pregenerated chunks.
        MantleSliceRetention.retain(String.class.getCanonicalName());
        MantleSliceRetention.retain(TreeBlockMaterial.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(String.class);
        verify(fixture.chunk(), never()).deleteSlices(TreeBlockMaterial.class);
    }

    @Test
    public void blockStateSliceIsNeverRetainable() {
        MantleSliceRetention.retain(PlatformBlockState.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void preObjectJournalIsReclaimedEvenWhenRetentionWasRequested() {
        MantleSliceRetention.retain(PreObjectMatterCell.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        assertTrue(fixture.engineMantle().forceCleanupChunk(7, -4));

        verify(fixture.chunk()).deleteSlices(PreObjectMatterCell.class);
    }

    @Test
    public void coveredCleanupRetainsDeferredMaterializationSlices() {
        CleanupFixture fixture = cleanupFixture();
        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);

        fixture.engineMantle().cleanupChunk(7, -4);

        verifyCleanup(fixture.chunk());
    }

    @Test
    public void forcedCleanupRetainsDeferredMaterializationSlices() {
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verifyCleanup(fixture.chunk());
    }

    @Test
    public void forceCleanupWaitsForTheFullGenerationHalo() {
        CleanupFixture fixture = cleanupFixture();
        doReturn(false).when(fixture.engineMantle()).isCovered(7, -4);

        assertFalse(fixture.engineMantle().forceCleanupChunk(7, -4));

        verify(fixture.chunk(), never()).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        verify(fixture.chunk(), never()).deleteSlices(PreObjectMatterCell.class);
    }

    @Test
    public void laterRealNeighborRetriesCoveredFrontierAndReportsOnlyActualCleanup() {
        CleanupFixture fixture = cleanupFixture();
        doReturn(1).when(fixture.engineMantle()).getRadius();
        doReturn(false).when(fixture.engineMantle()).isCovered(7, -4);
        when(fixture.mantle().hasFlag(6, -4, MantleFlag.CLEANED)).thenReturn(true);
        List<String> cleaned = new ArrayList<>();

        fixture.engineMantle().cleanupChunksCoveredBy(
                6,
                -4,
                false,
                (x, z) -> cleaned.add(x + "," + z)
        );
        assertTrue(cleaned.isEmpty());
        verify(fixture.engineMantle(), never()).isCovered(6, -4);

        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);
        fixture.engineMantle().cleanupChunksCoveredBy(
                8,
                -4,
                false,
                (x, z) -> cleaned.add(x + "," + z)
        );

        assertEquals(List.of("7,-4"), cleaned);
        verify(fixture.chunk()).deleteSlices(PreObjectMatterCell.class);
    }

    @SuppressWarnings("unchecked")
    private CleanupFixture cleanupFixture() {
        EngineMantle engineMantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        doReturn(true).when(engineMantle).isCovered(7, -4);
        when(mantle.getChunk(7, -4)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable cleanup = invocation.getArgument(1);
            cleanup.run();
            return null;
        }).when(chunk).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        return new CleanupFixture(engineMantle, mantle, chunk);
    }

    private void verifyCleanup(MantleChunk<Matter> chunk) {
        verify(chunk).deleteSlices(PlatformBlockState.class);
        verify(chunk).deleteSlices(MatterCavern.class);
        verify(chunk).deleteSlices(PreObjectMatterCell.class);
        verify(chunk, never()).deleteSlices(TileWrapper.class);
        verify(chunk, never()).deleteSlices(Identifier.class);
        verify(chunk, never()).deleteSlices(HydrologyCaveCell.class);
        InOrder order = inOrder(chunk);
        order.verify(chunk).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        order.verify(chunk).release();
    }

    private record CleanupFixture(
            EngineMantle engineMantle,
            Mantle<Matter> mantle,
            MantleChunk<Matter> chunk
    ) {
    }
}
