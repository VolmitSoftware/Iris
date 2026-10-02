package art.arcane.iris.structure.object;

import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.PlatformBinding;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class IrisObjectIoPaletteTest {
    private static final int BLOCK_COUNT = 30_000;
    private static final int UNIQUE_KEYS = 3_000;
    private static final String PINNED_DIGEST = "eee0bae5702dc3d8c79b5ba28d0782cdfa581662237629bb13a3adee6f757fc7";

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void pinsCanonicalPaletteAndBlockOrder() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IrisObjectIO.write(representativeObject(), out);

        assertEquals(PINNED_DIGEST, digest(out.toByteArray()));
    }

    @Test
    public void paletteKeepsFirstSeenCanonicalTraversalOrder() throws IOException {
        IrisObject object = representativeObject();
        List<String> firstSeen = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : object.blocks) {
            if (seen.add(entry.getValue().key())) {
                firstSeen.add(entry.getValue().key());
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IrisObjectIO.write(object, out);

        assertEquals(firstSeen, writtenPalette(out.toByteArray()));
    }

    @Test
    public void insertionPermutationsWriteIdenticalBytesAndReadBackEveryBlock() throws Throwable {
        when(platform.registries().blockOrNull(anyString(), eq(false)))
                .thenAnswer(invocation -> new KeyedBlockState(invocation.getArgument(0)));
        IrisObject source = representativeObject();
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        IrisObjectIO.write(source, canonical);
        byte[] expected = canonical.toByteArray();
        List<Map.Entry<IrisBlockVector, NativeBlockState>> blocks = new ArrayList<>();
        source.blocks.forEach((position, state) -> blocks.add(Map.entry(position, state)));
        for (int fixture = 0; fixture < 8; fixture++) {
            Collections.shuffle(blocks, new Random(fixture));
            IrisObject permuted = new IrisObject(source.w, source.h, source.d);
            for (Map.Entry<IrisBlockVector, NativeBlockState> entry : blocks) {
                permuted.blocks.put(entry.getKey(), entry.getValue());
            }
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            IrisObjectIO.write(permuted, encoded);
            assertArrayEquals("fixture " + fixture, expected, encoded.toByteArray());
            IrisObject decoded = new IrisObject();
            IrisObjectIO.read(decoded, new ByteArrayInputStream(encoded.toByteArray()));
            assertEquals(source.w, decoded.w);
            assertEquals(source.h, decoded.h);
            assertEquals(source.d, decoded.d);
            assertEquals(source.blocks.size(), decoded.blocks.size());
            assertEquals(source.states.size(), decoded.states.size());
            for (Map.Entry<IrisBlockVector, NativeBlockState> entry : blocks) {
                assertEquals(entry.getValue().key(), decoded.blocks.get(entry.getKey()).key());
            }
        }
    }

    private static List<String> writtenPalette(byte[] bytes) throws IOException {
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(bytes));
        din.readInt();
        din.readInt();
        din.readInt();
        din.readUTF();
        int count = din.readShort();
        List<String> palette = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            palette.add(din.readUTF());
        }
        return palette;
    }

    private static IrisObject representativeObject() {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("palette-order");
        int placed = 0;
        int distinct = 0;
        outer:
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                for (int z = 0; z < 64; z++) {
                    if (placed >= BLOCK_COUNT) {
                        break outer;
                    }
                    String key;
                    if (placed % 7 == 0 && distinct < UNIQUE_KEYS) {
                        key = "iris:unique_" + distinct;
                        distinct++;
                    } else {
                        key = "iris:shared_" + (placed % 11);
                    }
                    object.blocks.put(new IrisBlockVector(x, y, z), new KeyedBlockState(key));
                    placed++;
                }
            }
        }
        return object;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
