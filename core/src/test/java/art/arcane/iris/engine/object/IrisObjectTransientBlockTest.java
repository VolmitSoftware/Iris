package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectTransientBlockTest {
    private static final List<String> PALETTE = List.of(
            "minecraft:moving_piston[facing=down,type=normal]", "minecraft:oak_log[axis=y]",
            "minecraft:oak_leaves[persistent=true]", "minecraft:chest[facing=north,type=single,waterlogged=false]",
            "minecraft:piston[facing=north,extended=false]", "minecraft:sticky_piston[facing=north,extended=false]",
            "minecraft:jigsaw[orientation=north_up]");
    private static final TypeToken<KMap<String, Object>> TILE_PROPERTIES = new TypeToken<>() { };

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private TileData.TileReader previousReader;

    @Before
    public void bindStatesAndTiles() {
        when(platform.registries().blockOrNull(anyString(), eq(false)))
                .thenAnswer(invocation -> state(invocation.getArgument(0)));
        Gson gson = new Gson();
        previousReader = TileData.bindPlatformReader(input -> new TileData(input.readUTF(),
                gson.fromJson(input.readUTF(), TILE_PROPERTIES)));
    }

    @After
    public void restoreTiles() {
        TileData.restorePlatformReader(previousReader);
        IrisObjectScale.invalidate(null);
    }

    @Test
    public void bothObjectFormatsOmitTransientCellsAndTheirTiles() throws Throwable {
        for (boolean legacy : new boolean[]{false, true}) {
            IrisObject object = loadFixture(legacy);

            assertEquals(5, object.getBlocks().size());
            assertNull(object.getBlocks().get(new IrisBlockVector(0, 0, 0)));
            assertNull(object.getBlocks().get(new IrisBlockVector(6, 0, 0)));
            assertEquals("minecraft:oak_log", object.getBlocks().get(new IrisBlockVector(1, 0, 0)).materialKey());
            assertEquals("minecraft:oak_leaves", object.getBlocks().get(new IrisBlockVector(2, 0, 0)).materialKey());
            assertEquals("minecraft:piston", object.getBlocks().get(new IrisBlockVector(4, 0, 0)).materialKey());
            assertEquals("minecraft:sticky_piston", object.getBlocks().get(new IrisBlockVector(5, 0, 0)).materialKey());
            assertEquals(1, object.getStates().size());
            assertEquals("chest contents", object.getStates().get(new IrisBlockVector(3, 0, 0))
                    .getProperties().get("payload"));
        }
    }

    @Test
    public void scalingLoadedObjectsKeepsRealBlockEntitiesWithoutRevivingTransientCells() throws Throwable {
        IrisObject source = loadFixture(false);
        IrisObjectPlacement placement = new IrisObjectPlacement();
        for (double factor : new double[]{0.5D, 1D, 2D}) {
            IrisObject object = placement.scaleObject(new RNG(1337), source,
                    new IrisDimension().setAllObjectScaleFactor(factor));
            assertEquals(factor == 2D ? 8 : 1, object.getStates().size());
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
                assertFalse(entry.getValue().materialKey().equals("minecraft:moving_piston"));
                if (entry.getValue().materialKey().equals("minecraft:chest")) {
                    assertNotNull(object.getStates().get(entry.getKey()));
                    assertEquals("chest contents", object.getStates().get(entry.getKey()).getProperties().get("payload"));
                }
            }
        }
        assertEquals(5, source.getBlocks().size());
        assertEquals(1, source.getStates().size());
    }

    private static IrisObject loadFixture(boolean legacy) throws Throwable {
        IrisObject object = new IrisObject();
        ByteArrayInputStream input = new ByteArrayInputStream(fixture(legacy));
        if (legacy) {
            IrisObjectIO.readLegacy(object, input);
        } else {
            IrisObjectIO.read(object, input);
        }
        return object;
    }

    private static byte[] fixture(boolean legacy) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(16);
            output.writeInt(4);
            output.writeInt(4);
            if (!legacy) {
                output.writeUTF("Iris V2 IOB;");
                output.writeShort(PALETTE.size());
                for (String key : PALETTE) {
                    output.writeUTF(key);
                }
            }
            output.writeInt(PALETTE.size());
            for (int index = 0; index < PALETTE.size(); index++) {
                writePosition(output, index);
                if (legacy) {
                    output.writeUTF(PALETTE.get(index));
                } else {
                    output.writeShort(index);
                }
            }
            output.writeInt(4);
            writeTile(output, 0, "minecraft:moving_piston", "transient");
            writeTile(output, 3, "minecraft:chest", "chest contents");
            writeTile(output, 6, "minecraft:jigsaw", "marker");
            writeTile(output, 7, "minecraft:chest", "orphan");
        }
        return bytes.toByteArray();
    }

    private static void writeTile(DataOutputStream output, int x, String material, String payload) throws IOException {
        writePosition(output, x);
        output.writeUTF(material);
        output.writeUTF("{\"payload\":\"" + payload + "\"}");
    }

    private static void writePosition(DataOutputStream output, int x) throws IOException {
        output.writeShort(x);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static PlatformBlockState state(String key) {
        String material = key.substring(0, key.indexOf('['));
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(material);
        return state;
    }
}
