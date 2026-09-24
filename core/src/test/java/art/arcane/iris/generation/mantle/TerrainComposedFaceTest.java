package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.storage.PaletteOrHunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerrainComposedFaceTest {
    @BeforeClass
    public static void registerMatter() {
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void allFacesMatchScalarCompositionAcrossSectionsAndCapturedNulls() {
        Matter[] sections = {new IrisMatter(16, 16, 16), null, new IrisMatter(16, 16, 16)};
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.exists(anyInt())).thenAnswer(call -> (int) call.getArgument(0) != 1);
        when(chunk.get(anyInt())).thenAnswer(call -> {
            assertTrue(Thread.holdsLock(chunk));
            return sections[(int) call.getArgument(0)];
        });
        MatterCavern natural = new MatterCavern(true, "natural", (byte) 0);
        MatterCavern original = new MatterCavern(false, "original", (byte) 2);
        HydrologyCaveCell[] hydrology = new HydrologyCaveCell[HydrologyCaveAction.values().length + 1];
        for (int index = 1; index < hydrology.length; index++) {
            hydrology[index] = new HydrologyCaveCell(HydrologyCaveAction.values()[index - 1], "river", "flooded");
        }
        PreObjectMatterCell[] journal = {null, PreObjectMatterCell.string(null),
                PreObjectMatterCell.cavern(null), PreObjectMatterCell.cavern(original),
                PreObjectMatterCell.hydrology(null), PreObjectMatterCell.hydrology(hydrology[1]),
                PreObjectMatterCell.cavern(null).captureHydrology(null),
                PreObjectMatterCell.cavern(original).captureHydrology(null)};
        for (Matter matter : sections) {
            if (matter == null) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int index = x + y * 17 + z * 31;
                        matter.slice(MatterCavern.class).set(x, y, z, index % 3 == 0 ? null : natural);
                        matter.slice(HydrologyCaveCell.class).set(x, y, z, hydrology[index % hydrology.length]);
                        matter.slice(PreObjectMatterCell.class).set(x, y, z, journal[index % journal.length]);
                    }
                }
            }
        }
        for (TerrainMatterView.Face face : TerrainMatterView.Face.values()) {
            MatterCavern[] copied = TerrainMatterView.getComposedFace(chunk, face, 43);
            assertEquals(43 * 16, copied.length);
            for (int y = 0; y < 43; y++) {
                for (int offset = 0; offset < 16; offset++) {
                    int x = face == TerrainMatterView.Face.WEST ? 0 : face == TerrainMatterView.Face.EAST ? 15 : offset;
                    int z = face == TerrainMatterView.Face.NORTH ? 0 : face == TerrainMatterView.Face.SOUTH ? 15 : offset;
                    assertSame(TerrainMatterView.getComposedCavern(chunk, x, y, z), copied[y * 16 + offset]);
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void paletteFacesUseOneBulkReadAndDoNotLeakAcrossAbsentSlices() {
        Matter first = new IrisMatter(16, 16, 16);
        Matter second = new IrisMatter(16, 16, 16);
        MatterCavern cavern = new MatterCavern(true, "cave", (byte) 0);
        PaletteOrHunk<MatterCavern> slice = (PaletteOrHunk<MatterCavern>) first.<MatterCavern>slice(MatterCavern.class);
        slice.setRaw(15, 4, 7, cavern);
        DataContainer<MatterCavern> palette = spy(slice.palette());
        slice.setPalette(palette);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.exists(anyInt())).thenReturn(true);
        when(chunk.get(0)).thenReturn(first);
        when(chunk.get(1)).thenReturn(second);
        doAnswer(call -> {
            assertTrue(Thread.holdsLock(chunk));
            return call.callRealMethod();
        }).when(palette).copyTo(any(int[].class), any(MatterCavern[].class));
        MatterCavern[] copied = TerrainMatterView.getComposedFace(chunk, TerrainMatterView.Face.EAST, 32);
        assertSame(cavern, copied[4 * 16 + 7]);
        for (int index = 16 * 16; index < copied.length; index++) {
            assertNull(copied[index]);
        }
        verify(palette, times(1)).copyTo(any(int[].class), any(MatterCavern[].class));
        verify(palette, never()).get(anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nonstandardPaletteDimensionsRetainScalarCoordinatesAndBounds() {
        for (int[] dimensions : new int[][]{{8, 16, 16}, {16, 8, 16}, {16, 16, 8}}) {
            Matter matter = new IrisMatter(dimensions[0], dimensions[1], dimensions[2]);
            MatterCavern cavern = new MatterCavern(true, "small", (byte) 0);
            for (int x = 0; x < dimensions[0]; x++) {
                for (int y = 0; y < dimensions[1]; y++) {
                    for (int z = 0; z < dimensions[2]; z++) {
                        if ((x + y + z) % 3 == 0) {
                            matter.slice(MatterCavern.class).set(x, y, z, cavern);
                        }
                    }
                }
            }
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(chunk.exists(0)).thenReturn(true);
            when(chunk.get(0)).thenReturn(matter);
            for (TerrainMatterView.Face face : TerrainMatterView.Face.values()) {
                MatterCavern[] copied = TerrainMatterView.getComposedFace(chunk, face, 16);
                for (int y = 0; y < 16; y++) {
                    for (int offset = 0; offset < 16; offset++) {
                        int x = face == TerrainMatterView.Face.WEST ? 0 : face == TerrainMatterView.Face.EAST ? 15 : offset;
                        int z = face == TerrainMatterView.Face.NORTH ? 0 : face == TerrainMatterView.Face.SOUTH ? 15 : offset;
                        assertSame(TerrainMatterView.getComposedCavern(chunk, x, y, z), copied[y * 16 + offset]);
                    }
                }
            }
        }
    }

    @Test
    public void absentFacesAndInvalidHeightsAreBounded() {
        assertEquals(0, TerrainMatterView.getComposedFace(null, TerrainMatterView.Face.WEST, 0).length);
        for (MatterCavern cavern : TerrainMatterView.getComposedFace(null, TerrainMatterView.Face.WEST, 17)) {
            assertNull(cavern);
        }
        assertThrows(IllegalArgumentException.class, () -> TerrainMatterView.getComposedFace(null, TerrainMatterView.Face.WEST, -1));
        assertThrows(ArithmeticException.class, () -> TerrainMatterView.getComposedFace(null, TerrainMatterView.Face.WEST, Integer.MAX_VALUE));
    }
}
