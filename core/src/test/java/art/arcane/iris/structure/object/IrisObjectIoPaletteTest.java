package art.arcane.iris.structure.object;

import art.arcane.iris.testsupport.KeyedBlockState;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IrisObjectIoPaletteTest {
    private static final int BLOCK_COUNT = 30_000;
    private static final int UNIQUE_KEYS = 3_000;
    private static final String PINNED_DIGEST = "9fbd42fe7686cc725e0acab8595b0223d71c1a45762b78ac0e6146230dbb8849";

    @Test
    public void writesTheSameBytesAsTheOrderedPaletteScan() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IrisObjectIO.write(representativeObject(), out);

        assertEquals(PINNED_DIGEST, digest(out.toByteArray()));
    }

    @Test
    public void paletteKeepsFirstSeenInsertionOrder() throws IOException {
        IrisObject object = representativeObject();
        List<String> firstSeen = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.blocks) {
            if (seen.add(entry.getValue().key())) {
                firstSeen.add(entry.getValue().key());
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IrisObjectIO.write(object, out);

        assertEquals(firstSeen, writtenPalette(out.toByteArray()));
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
