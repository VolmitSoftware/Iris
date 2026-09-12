package art.arcane.iris.generation.hydrology.cave;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HydrologyCaveStorageTest {
    @Test
    @SuppressWarnings("unchecked")
    public void presentReadDoesNotMaterializeASectionOrSlice() {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter matter = mock(Matter.class);
        MatterSlice<HydrologyCaveCell> slice = mock(MatterSlice.class);
        HydrologyCaveCell expected = HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE);
        when(chunk.exists(2)).thenReturn(true);
        when(chunk.get(2)).thenReturn(matter);
        when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
        when(matter.getSlice(HydrologyCaveCell.class)).thenReturn(slice);
        when(slice.get(3, 1, 5)).thenReturn(expected);

        assertSame(expected, HydrologyCaveStorage.getIfPresent(chunk, 3, 33, 5));
        verify(chunk, never()).getOrCreate(2);
        verify(matter, never()).slice(HydrologyCaveCell.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void absentSliceReturnsWithoutCreatingIt() {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter matter = mock(Matter.class);
        when(chunk.exists(2)).thenReturn(true);
        when(chunk.get(2)).thenReturn(matter);

        assertNull(HydrologyCaveStorage.getIfPresent(chunk, 3, 33, 5));
        verify(matter, never()).slice(HydrologyCaveCell.class);
        verify(matter, never()).getSlice(HydrologyCaveCell.class);
    }
}
