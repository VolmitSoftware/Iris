package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveMatterTest {
    private static final String SAVED_CAVE_CELLS = "000000020000000200000002010000000001a16ef850cf00010000007200376172742e617263616e652e697269732e656e67696e652e687964726f6c6f67792e636176652e487964726f6c6f67794361766543656c6c08040100057269766572000c697269733a666c6f6f64656402000572697665720000030005776174657200000400046c61766100009180b004";

    @Test
    public void caveCellsSavedByEarlierWorldsStillLoad() throws IOException {
        IrisMatterSupport.ensureRegistered();
        Matter matter = Matter.read(new ByteArrayInputStream(HexFormat.of().parseHex(SAVED_CAVE_CELLS)));
        MatterSlice<HydrologyCaveCell> cells = matter.getSlice(HydrologyCaveCell.class);

        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river"), cells.get(0, 0, 0));
        assertEquals("a saved falling-fluid cell loads as a wet source",
                new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river"), cells.get(1, 0, 0));
        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"), cells.get(0, 1, 1));
        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "lava"), cells.get(1, 1, 1));
        assertNull(cells.get(1, 1, 0));
    }

    @Test
    public void everyActionAndProfileRoundTrips() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        for (HydrologyCaveAction action : HydrologyCaveAction.values()) {
            for (String profileKey : List.of("water", "underworld", "deep_lava")) {
                HydrologyCaveCell expected = new HydrologyCaveCell(action, profileKey);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                matter.writeNode(expected, new DataOutputStream(bytes));

                HydrologyCaveCell actual = matter.readNode(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

                assertEquals(expected, actual);
            }
        }
    }

    @Test
    public void savedActionCodesStayFixedWithTheFallingFluidCodeReserved() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        Map<HydrologyCaveAction, Integer> codes = new LinkedHashMap<>();
        codes.put(HydrologyCaveAction.WET_SOURCE, 1);
        codes.put(HydrologyCaveAction.DRY_AIR, 3);
        codes.put(HydrologyCaveAction.SEAL_GUARD, 4);
        codes.put(HydrologyCaveAction.POOL_SOURCE, 5);
        assertEquals(List.copyOf(codes.keySet()), List.of(HydrologyCaveAction.values()));
        for (Map.Entry<HydrologyCaveAction, Integer> entry : codes.entrySet()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            matter.writeNode(new HydrologyCaveCell(entry.getKey(), "water"), new DataOutputStream(bytes));
            assertEquals((int) entry.getValue(), bytes.toByteArray()[0]);
        }
        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river"),
                matter.readNode(new DataInputStream(new ByteArrayInputStream(new byte[]{2, 0, 5, 'r', 'i', 'v', 'e', 'r', 0, 0}))));
        HydrologyCaveCell pool = new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "water");
        assertTrue(pool.isWet());
        assertTrue(pool.carves());
        assertTrue(pool.asCavern().isWater());
    }

    @Test
    public void retiredBiomeSlotAndInvalidActionRemainUnambiguous() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        HydrologyCaveCell expected = new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.writeNode(expected, new DataOutputStream(bytes));

        assertArrayEquals(new byte[]{3, 0, 5, 'w', 'a', 't', 'e', 'r', 0, 0}, bytes.toByteArray());
        assertEquals(expected, matter.readNode(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals("a biome key saved by earlier worlds is skipped", expected, matter.readNode(new DataInputStream(
                new ByteArrayInputStream(new byte[]{3, 0, 5, 'w', 'a', 't', 'e', 'r', 0, 4, 'i', 'r', 'i', 's'}))));
        assertThrows(IOException.class, () -> matter.readNode(
                new DataInputStream(new ByteArrayInputStream(new byte[]{99}))));
        assertThrows(IOException.class, () -> matter.readNode(
                new DataInputStream(new ByteArrayInputStream(new byte[]{0, 0, 5, 'w', 'a', 't', 'e', 'r', 0, 0}))));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, " "));
    }
}
