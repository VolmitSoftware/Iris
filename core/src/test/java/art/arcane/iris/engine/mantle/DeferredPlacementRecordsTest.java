package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.DeferredPlacementRecords.WrittenBlocks;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DeferredPlacementRecordsTest {
    private static final int MIN_HEIGHT = -64;
    private static final int SECTION_COUNT = 24;

    private Engine engine;
    private Mantle<Matter> mantle;
    private MantleChunk<Matter> chunk;
    private Matter section;
    private MatterSlice<Identifier> identifiers;
    private MatterSlice<TileWrapper> tiles;

    @After
    public void tearDown() {
        IrisPlatforms.unbind();
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(registries.block(anyString())).thenReturn(mock(PlatformBlockState.class));
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        mantle = mock(Mantle.class);
        chunk = mock(MantleChunk.class);
        section = mock(Matter.class);
        identifiers = mock(MatterSlice.class);
        tiles = mock(MatterSlice.class);

        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(chunk.sectionCount()).thenReturn(SECTION_COUNT);
        when(chunk.exists(anyInt())).thenReturn(true);
        when(chunk.get(anyInt())).thenReturn(section);
        when(section.hasSlice(Identifier.class)).thenReturn(true);
        when(section.hasSlice(TileWrapper.class)).thenReturn(true);
        when(section.getSlice(Identifier.class)).thenReturn(identifiers);
        when(section.getSlice(TileWrapper.class)).thenReturn(tiles);
    }

    @Test
    public void writtenPositionsLoseTheirCustomAndTileRecords() {
        WrittenBlocks written = new WrittenBlocks();
        written.add(19, 70, 5);

        DeferredPlacementRecords.clearWritten(engine, written);

        int mantleY = 70 - MIN_HEIGHT;
        verify(chunk).get(mantleY >> 4);
        verify(identifiers).set(3, mantleY & 15, 5, null);
        verify(tiles).set(3, mantleY & 15, 5, null);
        verify(mantle).getChunk(1, 0);
        verify(chunk).release();
    }

    @Test
    public void eachTouchedChunkIsAcquiredOnceAndReleasedOnce() {
        WrittenBlocks written = new WrittenBlocks();
        written.add(1, 70, 1);
        written.add(2, 71, 2);
        written.add(-1, 70, 1);

        DeferredPlacementRecords.clearWritten(engine, written);

        verify(mantle).getChunk(0, 0);
        verify(mantle).getChunk(-1, 0);
        verify(chunk, times(2)).release();
    }

    @Test
    public void positionsBelowTheWorldOrInMissingSectionsAreIgnored() {
        when(chunk.exists(anyInt())).thenReturn(false);
        WrittenBlocks written = new WrittenBlocks();
        written.add(1, MIN_HEIGHT - 1, 1);
        written.add(1, 70, 1);

        DeferredPlacementRecords.clearWritten(engine, written);

        verify(identifiers, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(tiles, never()).set(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void slicesThatWereNeverCreatedAreNotCreatedByClearing() {
        when(section.hasSlice(Identifier.class)).thenReturn(false);
        WrittenBlocks written = new WrittenBlocks();
        written.add(1, 70, 1);

        DeferredPlacementRecords.clearWritten(engine, written);

        verify(section, never()).getSlice(Identifier.class);
        verify(tiles).set(1, (70 - MIN_HEIGHT) & 15, 1, null);
    }

    @Test
    public void noWritesLeaveTheMantleUntouched() {
        DeferredPlacementRecords.clearWritten(engine, new WrittenBlocks());

        verifyNoInteractions(mantle);
    }
}
