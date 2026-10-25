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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveMatterTest {
    private static final String SAVED_CAVE_CELLS = "000000020000000200000002010000000001a16ef850cf00010000007400376172742e617263616e652e697269732e656e67696e652e687964726f6c6f67792e636176652e487964726f6c6f67794361766543656c6c08040100057269766572000c697269733a666c6f6f6465640200057269766572000003000764656661756c7400000400046c61766100009180b004";

    @Test
    public void caveCellsSavedByEarlierWorldsStillLoad() throws IOException {
        IrisMatterSupport.ensureRegistered();
        Matter matter = Matter.read(new ByteArrayInputStream(HexFormat.of().parseHex(SAVED_CAVE_CELLS)));
        MatterSlice<HydrologyCaveCell> cells = matter.getSlice(HydrologyCaveCell.class);

        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river", "iris:flooded"), cells.get(0, 0, 0));
        assertEquals(new HydrologyCaveCell(HydrologyCaveAction.FALLING_FLUID, "river", ""), cells.get(1, 0, 0));
        assertEquals(HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), cells.get(0, 1, 1));
        assertEquals(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD, "lava"), cells.get(1, 1, 1));
        assertNull(cells.get(1, 1, 0));
    }

    @Test
    public void everyActionAndBiomeRoundTrips() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        for (HydrologyCaveAction action : HydrologyCaveAction.values()) {
            for (String profileKey : List.of("default", "underworld", "deep_lava")) {
                HydrologyCaveCell expected = new HydrologyCaveCell(
                        action,
                        profileKey,
                        "iris:flooded_grotto"
                );
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                matter.writeNode(expected, new DataOutputStream(bytes));

                HydrologyCaveCell actual = matter.readNode(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

                assertEquals(expected, actual);
            }
        }
    }

    @Test
    public void savedActionCodesOnlyEverGrowByAppending() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        List<HydrologyCaveAction> order = List.of(HydrologyCaveAction.WET_SOURCE, HydrologyCaveAction.FALLING_FLUID,
                HydrologyCaveAction.DRY_AIR, HydrologyCaveAction.SEAL_GUARD, HydrologyCaveAction.POOL_SOURCE);
        assertEquals(order, List.of(HydrologyCaveAction.values()));
        for (int index = 0; index < order.size(); index++) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            matter.writeNode(HydrologyCaveCell.of(order.get(index)), new DataOutputStream(bytes));
            assertEquals(index + 1, bytes.toByteArray()[0]);
        }
        HydrologyCaveCell pool = new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "water", "");
        assertTrue(pool.isWet());
        assertTrue(pool.carves());
        assertTrue(pool.asCavern().isWater());
    }

    @Test
    public void emptyBiomeAndInvalidActionRemainUnambiguous() throws IOException {
        HydrologyCaveMatter matter = new HydrologyCaveMatter();
        HydrologyCaveCell expected = HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.writeNode(expected, new DataOutputStream(bytes));

        assertEquals(expected, matter.readNode(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IOException.class, () -> matter.readNode(
                new DataInputStream(new ByteArrayInputStream(new byte[]{99}))));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyCaveCell(
                HydrologyCaveAction.WET_SOURCE,
                " ",
                "iris:flooded_grotto"
        ));
    }

    @Test
    public void everyPlannedCellProtectsItsAcceptedVolume() {
        for (HydrologyCaveAction action : HydrologyCaveAction.values()) {
            assertTrue(HydrologyCaveCell.of(action).protectsPlacement());
        }
    }
}
