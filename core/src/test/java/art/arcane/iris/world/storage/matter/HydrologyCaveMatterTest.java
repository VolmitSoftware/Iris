package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveMatterTest {
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
