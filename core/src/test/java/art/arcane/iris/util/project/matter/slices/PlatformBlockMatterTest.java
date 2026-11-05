package art.arcane.iris.util.project.matter.slices;

import art.arcane.iris.spi.PlatformBlockState;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the mantle saves for a block; reading a key back goes through {@code B.getState}, which resolves provider ids. */
public class PlatformBlockMatterTest {
    @Test
    public void aCustomBlockIsSavedUnderItsCustomIdNotItsBaseBlock() throws IOException {
        PlatformBlockState customBlock = mock(PlatformBlockState.class);
        when(customBlock.isCustom()).thenReturn(true);
        when(customBlock.key()).thenReturn("minecraft:note_block[instrument=harp,note=3,powered=false]");
        when(customBlock.deferredPlacementKey()).thenReturn("nexo:ruby_block");

        assertEquals("nexo:ruby_block", written(customBlock));
    }

    @Test
    public void aPlainBlockIsSavedUnderItsKey() throws IOException {
        PlatformBlockState stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");

        assertEquals("minecraft:stone", written(stone));
    }

    private static String written(PlatformBlockState state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PlatformBlockMatter().writeNode(state, new DataOutputStream(bytes));
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())).readUTF();
    }
}
