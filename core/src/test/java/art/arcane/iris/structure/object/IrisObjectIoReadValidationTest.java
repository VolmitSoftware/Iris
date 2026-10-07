package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.geometry.AxisAlignedBB;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class IrisObjectIoReadValidationTest {
    private static final IrisBlockVector RETAINED_POSITION = new IrisBlockVector(3, 2, -1);
    private static final TypeToken<KMap<String, Object>> TILE_PROPERTIES = new TypeToken<>() { };

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private TileData.TileReader previousReader;

    @Before
    public void bindStatesAndTiles() {
        when(platform.registries().blockOrNull(anyString(), eq(false)))
                .thenAnswer(invocation -> new KeyedBlockState(invocation.getArgument(0)));
        Gson gson = new Gson();
        previousReader = TileData.bindPlatformReader(input -> new TileData(input.readUTF(),
                gson.fromJson(input.readUTF(), TILE_PROPERTIES)));
    }

    @After
    public void restoreTileReader() {
        TileData.restorePlatformReader(previousReader);
    }

    @Test
    public void negativeCountsFailWithoutReplacingPriorObjectContents() throws IOException {
        for (boolean legacy : new boolean[]{false, true}) {
            assertRejected(legacy, emptyFixture(legacy, new Counts(0, -1, 0)));
            assertRejected(legacy, emptyFixture(legacy, new Counts(0, 0, -1)));
        }
        assertRejected(false, emptyFixture(false, new Counts(-1, 0, 0)));
    }

    @Test
    public void invalidDimensionsFailWithoutReplacingPriorObjectContents() throws IOException {
        for (boolean legacy : new boolean[]{false, true}) {
            for (int offset : new int[]{0, 4, 8}) {
                for (int dimension : new int[]{0, -1}) {
                    byte[] fixture = emptyFixture(legacy, new Counts(0, 0, 0));
                    ByteBuffer.wrap(fixture).putInt(offset, dimension);
                    assertRejected(legacy, fixture);
                }
            }
        }
    }

    @Test
    public void everyTruncatedModernReadRetainsPriorContents() throws IOException {
        byte[] complete = fixture(false, true);
        for (int length = 0; length < complete.length; length++) {
            assertRejected(false, Arrays.copyOf(complete, length));
        }
    }

    @Test
    public void truncatedLegacyTileDataFailsInsteadOfReturningPartialObject() throws IOException {
        byte[] complete = fixture(true, true);
        byte[] withoutTiles = fixture(true, false);
        for (int length = withoutTiles.length + 1; length < complete.length; length++) {
            assertRejected(true, Arrays.copyOf(complete, length));
        }
    }

    @Test
    public void legacyOptionalTilesUseEofRatherThanAvailableAndDoNotCloseCallerStream() throws IOException {
        for (boolean tiles : new boolean[]{false, true}) {
            CallerStream input = new CallerStream(fixture(true, tiles));
            IrisObject object = retainedObject();

            IrisObjectIO.readLegacy(object, input);

            assertEquals(2, object.getBlocks().size());
            assertEquals(tiles ? 1 : 0, object.getStates().size());
            assertFalse(input.closed);
            if (tiles) {
                assertEquals("kept", object.getStates().get(new IrisBlockVector(-1, 0, 0))
                        .getProperties().get("payload"));
            }
        }
    }

    @Test
    public void fileReadsSupportHeaderlessLegacyObjectsWithAndWithoutTiles() throws IOException {
        for (boolean tiles : new boolean[]{false, true}) {
            File file = temporary.newFile("legacy-" + tiles + ".iob");
            Files.write(file.toPath(), fixture(true, tiles));
            IrisObject object = retainedObject();

            IrisObjectIO.read(object, file);

            assertEquals(3, object.getW());
            assertEquals(2, object.getBlocks().size());
            assertEquals(tiles ? 1 : 0, object.getStates().size());
        }
    }

    @Test
    public void malformedModernFileReportsPaletteFailureWithoutLegacyRetry() throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(encoded)) {
            writeDimensions(output);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(0);
            output.writeInt(1);
            writePosition(output, 0);
            output.writeShort(0);
            output.writeInt(0);
        }
        File file = temporary.newFile("invalid-modern.iob");
        Files.write(file.toPath(), encoded.toByteArray());
        IrisObject object = retainedObject();

        try {
            IrisObjectIO.read(object, file);
            fail("expected invalid palette reference");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("palette index"));
        }

        assertRetained(object);
    }

    @Test
    public void successfulReadReplacesPriorContentsAndInvalidatesDerivedBounds() throws IOException {
        IrisObject object = retainedObject();
        AxisAlignedBB previousBounds = object.getAABB();
        CallerStream input = new CallerStream(fixture(false, true));

        IrisObjectIO.read(object, input);

        assertEquals(3, object.getW());
        assertEquals(3, object.getH());
        assertEquals(3, object.getD());
        assertEquals(2, object.getBlocks().size());
        assertEquals(1, object.getStates().size());
        assertFalse(object.getBlocks().containsKey(RETAINED_POSITION));
        assertFalse(object.isSmartBored());
        assertNotSame(previousBounds, object.getAABB());
        assertFalse(input.closed);
    }

    private static void assertRejected(boolean legacy, byte[] fixture) throws IOException {
        IrisObject object = retainedObject();
        AxisAlignedBB bounds = object.getAABB();
        try {
            if (legacy) {
                IrisObjectIO.readLegacy(object, new ByteArrayInputStream(fixture));
            } else {
                IrisObjectIO.read(object, new ByteArrayInputStream(fixture));
            }
            fail("expected malformed object to fail");
        } catch (IOException expected) {
        }
        assertRetained(object);
        assertSame(bounds, object.getAABB());
    }

    private static IrisObject retainedObject() {
        IrisObject object = new IrisObject(7, 9, 11);
        object.getBlocks().put(RETAINED_POSITION, new KeyedBlockState("minecraft:chest"));
        KMap<String, Object> properties = new KMap<>();
        properties.put("payload", "previous");
        object.getStates().put(RETAINED_POSITION, new TileData("minecraft:chest", properties));
        object.setSmartBored(true);
        return object;
    }

    private static void assertRetained(IrisObject object) {
        assertEquals(7, object.getW());
        assertEquals(9, object.getH());
        assertEquals(11, object.getD());
        assertEquals(1, object.getBlocks().size());
        assertEquals("minecraft:chest", object.getBlocks().get(RETAINED_POSITION).key());
        assertEquals(1, object.getStates().size());
        assertEquals("previous", object.getStates().get(RETAINED_POSITION).getProperties().get("payload"));
        assertTrue(object.isSmartBored());
    }

    private static byte[] emptyFixture(boolean legacy, Counts counts) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(encoded)) {
            writeDimensions(output);
            if (!legacy) {
                output.writeUTF("Iris V2 IOB;");
                output.writeShort(counts.palette());
            }
            output.writeInt(counts.blocks());
            output.writeInt(counts.tiles());
        }
        return encoded.toByteArray();
    }

    private static byte[] fixture(boolean legacy, boolean tiles) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(encoded)) {
            writeDimensions(output);
            if (!legacy) {
                output.writeUTF("Iris V2 IOB;");
                output.writeShort(2);
                output.writeUTF("minecraft:chest");
                output.writeUTF("minecraft:stone");
            }
            output.writeInt(2);
            for (int index = 0; index < 2; index++) {
                writePosition(output, index - 1);
                if (legacy) {
                    output.writeUTF(index == 0 ? "minecraft:chest" : "minecraft:stone");
                } else {
                    output.writeShort(index);
                }
            }
            if (!legacy || tiles) {
                output.writeInt(tiles ? 1 : 0);
            }
            if (tiles) {
                writePosition(output, -1);
                output.writeUTF("minecraft:chest");
                output.writeUTF("{\"payload\":\"kept\"}");
            }
        }
        return encoded.toByteArray();
    }

    private static void writeDimensions(DataOutputStream output) throws IOException {
        output.writeInt(3);
        output.writeInt(3);
        output.writeInt(3);
    }

    private static void writePosition(DataOutputStream output, int x) throws IOException {
        output.writeShort(x);
        output.writeShort(0);
        output.writeShort(0);
    }

    private record Counts(int palette, int blocks, int tiles) {
    }

    private static final class CallerStream extends ByteArrayInputStream {
        private boolean closed;

        private CallerStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
