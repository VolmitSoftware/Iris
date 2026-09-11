package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisObjectIoWriteLimitsTest {
    private static IrisObject oversizedPaletteObject;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void buildSharedObjects() {
        oversizedPaletteObject = objectWithDistinctStates(32_768);
    }

    private static PlatformBlockState state(String key) {
        return new KeyedBlockState(key);
    }

    private static IrisObject objectWithDistinctStates(int paletteSize) {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        int placed = 0;
        outer:
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                for (int z = 0; z < 64; z++) {
                    if (placed >= paletteSize) {
                        break outer;
                    }
                    object.blocks.put(new IrisBlockVector(x, y, z), state("iris:test_" + placed));
                    placed++;
                }
            }
        }
        return object;
    }

    @Test
    public void preservesV2HeaderSignedCoordinatesAndTilePayload() throws IOException {
        IrisObject object = new IrisObject(7, 9, 11);
        object.blocks.put(new IrisBlockVector(-3, 2, -1), state("minecraft:chest"));
        KMap<String, Object> properties = new KMap<>();
        properties.put("marker", "stored");
        object.states.put(new IrisBlockVector(-3, 2, -1), new TileData("minecraft:chest", properties));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();

        IrisObjectIO.write(object, encoded);

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()))) {
            assertEquals(7, input.readInt());
            assertEquals(9, input.readInt());
            assertEquals(11, input.readInt());
            assertEquals("Iris V2 IOB;", input.readUTF());
            assertEquals(1, input.readShort());
            assertEquals("minecraft:chest", input.readUTF());
            assertEquals(1, input.readInt());
            assertEquals(-3, input.readShort());
            assertEquals(2, input.readShort());
            assertEquals(-1, input.readShort());
            assertEquals(0, input.readShort());
            assertEquals(1, input.readInt());
            assertEquals(-3, input.readShort());
            assertEquals(2, input.readShort());
            assertEquals(-1, input.readShort());
            assertEquals("minecraft:chest", input.readUTF());
            assertEquals("{\"marker\":\"stored\"}", input.readUTF());
            assertEquals(-1, input.read());
        }
    }

    @Test
    public void rejectsPaletteOverflowInsteadOfWrappingTheShort() {
        IrisObject object = oversizedPaletteObject;

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the oversized palette to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("limits-test"));
            assertTrue(e.getMessage(), e.getMessage().contains("32768"));
        }
    }

    @Test
    public void acceptsPaletteAtExactlyTheCap() throws IOException {
        IrisObject object = objectWithDistinctStates(32_767);
        File file = folder.newFile("cap.iob");

        IrisObjectIO.write(object, file);

        assertEquals(32_767, IrisObjectIO.readPaletteKeys(file).size());
    }

    @Test
    public void rejectsCoordinateBeyondShortRange() {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        object.blocks.put(new IrisBlockVector(40_000, 0, 0), state("iris:test"));

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the out-of-range coordinate to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("40000"));
            assertTrue(e.getMessage(), e.getMessage().contains("x"));
        }
    }

    @Test
    public void failedWriteLeavesExistingFileIntact() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObject valid = objectWithDistinctStates(3);
        IrisObjectIO.write(valid, file);
        byte[] before = Files.readAllBytes(file.toPath());

        IrisObject oversized = oversizedPaletteObject;
        try {
            IrisObjectIO.write(oversized, file);
            fail("expected the oversized write to be rejected");
        } catch (IOException expected) {
        }

        assertArrayEquals("a rejected write must not truncate the previous object", before,
                Files.readAllBytes(file.toPath()));
    }
}
