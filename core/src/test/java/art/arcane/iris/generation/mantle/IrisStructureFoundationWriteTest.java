package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisStructureFoundationWriteTest {
    @Test
    public void resolvesWorldCoordinatesThenClearsCavernBeforeWritingSupport() {
        MantleWriter writer = mock(MantleWriter.class);
        IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
        RNG rng = new RNG(1337L);
        IrisData data = mock(IrisData.class);
        PlatformBlockState deepslateBricks = mock(PlatformBlockState.class);
        when(palette.get(rng, 12, -247, -18, data)).thenReturn(deepslateBricks);

        IrisStructureComponent.writeFoundationSupport(
                writer, palette, rng, data, 12, 9, -18, -256);

        InOrder order = inOrder(palette, writer);
        order.verify(palette).get(rng, 12, -247, -18, data);
        order.verify(writer).clearData(12, 9, -18, MatterCavern.class);
        order.verify(writer).set(12, 9, -18, deepslateBricks);
    }

    @Test
    public void unresolvedPaletteFailsBeforeChangingCavernOrBlocks() {
        MantleWriter writer = mock(MantleWriter.class);
        IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
        RNG rng = new RNG(1337L);
        IrisData data = mock(IrisData.class);

        try {
            IrisStructureComponent.writeFoundationSupport(
                    writer, palette, rng, data, 12, 9, -18, -256);
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage().contains("12,-247,-18"));
            verifyNoInteractions(writer);
            return;
        }
        throw new AssertionError("Expected an unresolved structure stilt palette to fail");
    }
}
