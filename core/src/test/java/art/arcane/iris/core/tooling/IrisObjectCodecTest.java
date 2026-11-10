package art.arcane.iris.core.tooling;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectCodecTest {
    private final IrisObjectCodec codec = new IrisObjectCodec();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void aFileWrittenAgainKeepsItsDeclaredSize() throws IOException {
        for (int[] size : new int[][]{{1, 1, 1}, {2, 3, 4}, {16, 8, 16}, {15, 15, 15}, {9, 6, 7}}) {
            byte[] written = codec.encode(codec.decode(file(size[0], size[1], size[2], 6)));

            assertArrayEquals(size[0] + "x" + size[1] + "x" + size[2], List.of(size[0], size[1], size[2]).toArray(),
                    declaredSizeOf(written));
        }
    }

    @Test
    public void anObjectWithoutBlocksKeepsItsDeclaredSize() throws IOException {
        GeneratedObject decoded = codec.decode(file(5, 3, 5, 0));

        assertEquals(new BlockBox(-2, -1, -2, 3, 2, 3), decoded.bounds());
        assertEquals(new BlockBox(-2, -1, -2, 3, 2, 3), codec.decode(codec.encode(decoded)).bounds());
    }

    @Test
    public void aFileWrittenAgainHoldsTheSameBlocksAtTheSamePlaces() throws IOException {
        for (int blocks : new int[]{1, 7, 25, 60}) {
            GeneratedObject decoded = codec.decode(file(11, 9, 13, blocks));

            GeneratedObject again = codec.decode(codec.encode(decoded));

            assertEquals(blocks + " blocks", blocksOf(decoded), blocksOf(again));
            assertEquals(decoded.bounds(), again.bounds());
        }
    }

    private static Map<BlockPosition, BlockState> blocksOf(GeneratedObject object) {
        Map<BlockPosition, BlockState> blocks = new HashMap<>();
        object.blocks().forEach(block -> blocks.put(block.position(), block.state()));
        return blocks;
    }

    private static Object[] declaredSizeOf(byte[] file) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
        return new Object[]{in.readInt(), in.readInt(), in.readInt()};
    }

    private static final String[] PALETTE = {"minecraft:stone", "minecraft:oak_planks",
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"};

    /** A file as Iris writes them: the declared size, the palette, the blocks at their offsets from the center, and no tiles. */
    private static byte[] file(int width, int height, int depth, int blockCount) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(depth);
        out.writeUTF("Iris V2 IOB;");
        out.writeShort(PALETTE.length);
        for (String key : PALETTE) {
            out.writeUTF(key);
        }
        out.writeInt(blockCount);
        for (int index = 0; index < blockCount; index++) {
            out.writeShort(index % width - width / 2);
            out.writeShort((index / 2) % height - height / 2);
            out.writeShort((index * 3) % depth - depth / 2);
            out.writeShort(index % PALETTE.length);
        }
        out.writeInt(0);
        return bytes.toByteArray();
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.namespace()).thenReturn(key.substring(0, key.indexOf(':')));
        return state;
    }
}
