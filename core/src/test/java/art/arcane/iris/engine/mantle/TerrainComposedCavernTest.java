package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.iris.util.project.matter.slices.HydrologyCaveMatter;
import art.arcane.iris.util.project.matter.slices.PreObjectMatter;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class TerrainComposedCavernTest {
    @BeforeClass
    public static void registerMatter() {
        IrisMatter.registerSliceType(new PreObjectMatter());
        IrisMatter.registerSliceType(new HydrologyCaveMatter());
    }

    @Test
    public void allHydrologyActionsAndPartialJournalCapturesMatchTheExistingComposition() {
        MatterCavern natural = new MatterCavern(true, "iris:natural", (byte) 0);
        MatterCavern tagged = new MatterCavern(false, "iris:tagged", (byte) 2);
        List<MatterCavern> caverns = Arrays.asList(null, natural, tagged);
        List<HydrologyCaveCell> hydrology = new ArrayList<>();
        hydrology.add(null);
        for (HydrologyCaveAction action : HydrologyCaveAction.values()) {
            hydrology.add(new HydrologyCaveCell(action, "river", "iris:flooded"));
        }
        List<PreObjectMatterCell> journals = new ArrayList<>();
        journals.add(null);
        journals.add(PreObjectMatterCell.string(null));
        journals.add(PreObjectMatterCell.cavern(null));
        journals.add(PreObjectMatterCell.cavern(natural));
        for (HydrologyCaveCell cell : hydrology) {
            journals.add(PreObjectMatterCell.hydrology(cell));
            journals.add(PreObjectMatterCell.cavern(null).captureHydrology(cell));
            journals.add(PreObjectMatterCell.cavern(natural).captureHydrology(cell));
        }
        Matter matter = new IrisMatter(16, 16, 16);
        MantleChunk<Matter> chunk = chunk(matter);
        for (MatterCavern rawCavern : caverns) {
            for (HydrologyCaveCell rawHydrology : hydrology) {
                for (PreObjectMatterCell journal : journals) {
                    matter.<MatterCavern>slice(MatterCavern.class).set(15, 3, 2, rawCavern);
                    matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class).set(15, 3, 2, rawHydrology);
                    matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(15, 3, 2, journal);
                    MatterCavern effectiveCavern = journal != null && journal.cavernCaptured()
                            ? journal.cavern() : rawCavern;
                    HydrologyCaveCell effectiveHydrology = journal != null && journal.hydrologyCaptured()
                            ? journal.hydrology() : rawHydrology;
                    MatterCavern expected = effectiveHydrology == null
                            ? effectiveCavern : effectiveHydrology.asCavern();
                    assertSame(expected, legacy(chunk, -1, 35, 18));
                    assertSame(expected, TerrainMatterView.getComposedCavern(chunk, -1, 35, 18));
                }
            }
        }
    }

    @Test
    public void absentChunksSectionsAndVerticalBoundsRemainEmpty() {
        MantleChunk<Matter> chunk = chunk(null);
        assertNull(TerrainMatterView.getComposedCavern(null, 1, 2, 3));
        assertNull(TerrainMatterView.getComposedCavern(chunk, 1, -1, 3));
        assertNull(TerrainMatterView.getComposedCavern(chunk, 1, Integer.MIN_VALUE, 3));
        verifyNoInteractions(chunk);
        assertNull(TerrainMatterView.getComposedCavern(chunk, 1, 0, 3));
        assertNull(TerrainMatterView.getComposedCavern(chunk, 1, Integer.MAX_VALUE, 3));
        assertNull(TerrainMatterView.getComposedCavern(chunk, 1, 35, 3));
        assertNull(legacy(chunk, 1, 35, 3));
    }

    @Test
    public void composedReadUsesOneLockedSectionAndJournalLookup() {
        Matter matter = spy(new IrisMatter(16, 16, 16));
        MatterCavern natural = new MatterCavern(true, "iris:natural", (byte) 0);
        HydrologyCaveCell seal = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        matter.<MatterCavern>slice(MatterCavern.class).set(15, 3, 2, natural);
        matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class).set(15, 3, 2, seal);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(15, 3, 2,
                PreObjectMatterCell.string("iris:owner"));
        MatterSlice<PreObjectMatterCell> journal = spy(matter.<PreObjectMatterCell>getSlice(PreObjectMatterCell.class));
        MatterSlice<HydrologyCaveCell> hydrology = spy(matter.<HydrologyCaveCell>getSlice(HydrologyCaveCell.class));
        matter.getSliceMap().put(PreObjectMatterCell.class, journal);
        matter.getSliceMap().put(HydrologyCaveCell.class, hydrology);
        MantleChunk<Matter> chunk = chunk(matter);
        clearInvocations(matter);

        assertNull(TerrainMatterView.getComposedCavern(chunk, -1, 35, 18));

        verify(chunk, times(1)).exists(2);
        verify(chunk, times(1)).get(2);
        verify(journal, times(1)).get(15, 3, 2);
        verify(hydrology, times(1)).get(15, 3, 2);
        verify(matter, never()).hasSlice(MatterCavern.class);
        verify(matter, never()).getSlice(MatterCavern.class);
    }

    @Test
    public void lookupFailuresRetainTheirOriginalCause() {
        MantleChunk<Matter> chunk = chunk(null);
        IllegalStateException failure = new IllegalStateException("section unavailable");
        doThrow(failure).when(chunk).get(2);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> TerrainMatterView.getComposedCavern(chunk, 1, 35, 3)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> legacy(chunk, 1, 35, 3)));
    }

    private static MatterCavern legacy(MantleChunk<Matter> chunk, int x, int y, int z) {
        MatterCavern cavern = TerrainMatterView.get(chunk, x, y, z, MatterCavern.class);
        HydrologyCaveCell hydrology = TerrainMatterView.get(chunk, x, y, z, HydrologyCaveCell.class);
        return hydrology == null ? cavern : hydrology.asCavern();
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> chunk(Matter matter) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(chunk));
            return true;
        }).when(chunk).exists(2);
        doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(chunk));
            return matter;
        }).when(chunk).get(2);
        return chunk;
    }
}
