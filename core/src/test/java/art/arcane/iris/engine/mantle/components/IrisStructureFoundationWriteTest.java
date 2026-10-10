package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSampler;
import org.junit.After;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisStructureFoundationWriteTest {
    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void resolvesWorldCoordinatesThenClearsCavernBeforeWritingSupport() {
        UsableBlockPlatform.bind();
        MantleWriter writer = mock(MantleWriter.class);
        List<String> asked = new ArrayList<>();
        PaletteSampler picker = (x, y, z) -> {
            asked.add((int) x + "," + (int) y + "," + (int) z);
            return new BlockState("minecraft:deepslate_bricks");
        };

        StructurePlacing.writeFoundationSupport(writer, picker, 12, 9, -18, -256);

        assertEquals(List.of("12,-247,-18"), asked);
        InOrder order = inOrder(writer);
        order.verify(writer).clearData(12, 9, -18, MatterCavern.class);
        order.verify(writer).set(eq(12), eq(9), eq(-18), any(art.arcane.iris.spi.PlatformBlockState.class));
    }

    @Test
    public void unresolvedPaletteFailsBeforeChangingCavernOrBlocks() {
        UsableBlockPlatform.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(mock(PlatformRegistries.class));
        IrisPlatforms.bind(platform);
        MantleWriter writer = mock(MantleWriter.class);

        try {
            StructurePlacing.writeFoundationSupport(writer, (x, y, z) -> new BlockState("test:unknown"), 12, 9, -18, -256);
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage().contains("12,-247,-18"));
            verifyNoInteractions(writer);
            return;
        }
        throw new AssertionError("Expected an unresolved structure stilt palette to fail");
    }
}
