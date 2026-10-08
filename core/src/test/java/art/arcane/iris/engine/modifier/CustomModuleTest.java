package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A custom block goes into the mantle as its placement key and is replaced by its base state, in a chunk the mantle flags as having some. */
public class CustomModuleTest {
    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;
    private static final int Y = 20;
    private static final String PLACEMENT_KEY = "example:crystal";

    @Test
    @SuppressWarnings("unchecked")
    public void aCustomBlockIsRecordedInTheMantleAndReplacedByItsBaseState() {
        MantleChunk<Matter> mantleChunk = mantleChunk(true);
        Matter section = mock(Matter.class);
        MatterSlice<Identifier> slice = mock(MatterSlice.class);
        when(mantleChunk.getOrCreate(Y >> 4)).thenReturn(section);
        when(section.<Identifier>slice(Identifier.class)).thenReturn(slice);
        PlatformBlockState base = mock(PlatformBlockState.class);
        PlatformBlockState custom = mock(PlatformBlockState.class);
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn(PLACEMENT_KEY);
        when(custom.placementBaseState()).thenReturn(base);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 32, 16);
        blocks.set(4, Y, 5, custom);

        new CustomModule.CustomBlockRecording(engine(mantleChunk)).record(CHUNK_X << 4, CHUNK_Z << 4, blocks, false);

        verify(slice).set(4, Y & 15, 5, Identifier.fromString(PLACEMENT_KEY));
        assertSame(base, blocks.get(4, Y, 5));
        verify(mantleChunk).release();
    }

    @Test
    public void aChunkTheMantleDoesNotFlagIsNotScanned() {
        MantleChunk<Matter> mantleChunk = mantleChunk(false);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 32, 16);

        new CustomModule.CustomBlockRecording(engine(mantleChunk)).record(CHUNK_X << 4, CHUNK_Z << 4, blocks, false);

        verify(mantleChunk, never()).use();
        verify(mantleChunk, never()).getOrCreate(anyInt());
        assertNull(blocks.get(0, 0, 0));
    }

    @SuppressWarnings("unchecked")
    private MantleChunk<Matter> mantleChunk(boolean flagged) {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        when(mantleChunk.isFlagged(MantleFlag.CUSTOM_ACTIVE)).thenReturn(flagged);
        return mantleChunk;
    }

    @SuppressWarnings("unchecked")
    private Engine engine(MantleChunk<Matter> mantleChunk) {
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.getChunk(CHUNK_X, CHUNK_Z)).thenReturn(mantleChunk);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        Engine engine = mock(Engine.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        return engine;
    }
}
