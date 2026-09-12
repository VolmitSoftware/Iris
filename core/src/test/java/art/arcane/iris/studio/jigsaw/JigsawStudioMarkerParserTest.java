package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import org.bukkit.block.Orientation;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JigsawStudioMarkerParserTest {
    @Test
    public void mapsEveryBukkitJigsawOrientation() {
        assertDirections(Orientation.DOWN_EAST, IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.EAST_POSITIVE_X);
        assertDirections(Orientation.DOWN_NORTH, IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
        assertDirections(Orientation.DOWN_SOUTH, IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
        assertDirections(Orientation.DOWN_WEST, IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.WEST_NEGATIVE_X);
        assertDirections(Orientation.UP_EAST, IrisDirection.UP_POSITIVE_Y, IrisDirection.EAST_POSITIVE_X);
        assertDirections(Orientation.UP_NORTH, IrisDirection.UP_POSITIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
        assertDirections(Orientation.UP_SOUTH, IrisDirection.UP_POSITIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
        assertDirections(Orientation.UP_WEST, IrisDirection.UP_POSITIVE_Y, IrisDirection.WEST_NEGATIVE_X);
        assertDirections(Orientation.WEST_UP, IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y);
        assertDirections(Orientation.EAST_UP, IrisDirection.EAST_POSITIVE_X, IrisDirection.UP_POSITIVE_Y);
        assertDirections(Orientation.NORTH_UP, IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.UP_POSITIVE_Y);
        assertDirections(Orientation.SOUTH_UP, IrisDirection.SOUTH_POSITIVE_Z, IrisDirection.UP_POSITIVE_Y);
    }

    @Test
    public void parsesMarkerMetadataAndSignedPriorities() {
        Map<String, Object> nbt = markerNbt();
        nbt.put("channel", "castle/door");
        nbt.put("selection_priority", -4);
        nbt.put("placement_priority", 17L);

        IrisJigsawConnector connector = JigsawStudioMarkerParser.parse(
                nbt, Orientation.NORTH_UP, 3, 5, 7);

        assertEquals(3, connector.getPosition().getX());
        assertEquals(5, connector.getPosition().getY());
        assertEquals(7, connector.getPosition().getZ());
        assertEquals(IrisDirection.NORTH_NEGATIVE_Z, connector.getDirection());
        assertEquals(IrisDirection.UP_POSITIVE_Y, connector.getTop());
        assertEquals("fort/pool", connector.getPool());
        assertEquals("iris:door", connector.getName());
        assertEquals("iris:door", connector.getTargetName());
        assertEquals("castle/door", connector.getChannel());
        assertEquals(JigsawJoint.ALIGNED, connector.getJoint());
        assertEquals("minecraft:stone", connector.getFinalState());
        assertEquals(-4, connector.getSelectionPriority());
        assertEquals(17, connector.getPlacementPriority());
    }

    @Test
    public void defaultsMissingOptionalMetadata() {
        IrisJigsawConnector connector = JigsawStudioMarkerParser.parse(
                markerNbt(), Orientation.SOUTH_UP, 0, 0, 0);

        assertEquals("", connector.getChannel());
        assertEquals(0, connector.getSelectionPriority());
        assertEquals(0, connector.getPlacementPriority());
    }

    @Test
    public void rejectsMissingRequiredMarkerMetadata() {
        Map<String, Object> nbt = markerNbt();
        nbt.remove("pool");

        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMarkerParser.parse(nbt, Orientation.NORTH_UP, 0, 0, 0));
    }

    @Test
    public void rejectsNonStudioPoolNamespaces() {
        Map<String, Object> nbt = markerNbt();
        nbt.put("pool", "minecraft:fort/pool");

        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMarkerParser.parse(nbt, Orientation.NORTH_UP, 0, 0, 0));
    }

    private static Map<String, Object> markerNbt() {
        Map<String, Object> nbt = new LinkedHashMap<>();
        nbt.put("name", "iris:door");
        nbt.put("target", "iris:door");
        nbt.put("pool", "iris:fort/pool");
        nbt.put("final_state", "minecraft:stone");
        nbt.put("joint", "aligned");
        return nbt;
    }

    private static void assertDirections(
            Orientation orientation,
            IrisDirection expectedFront,
            IrisDirection expectedTop
    ) {
        JigsawStudioMarkerParser.Directions directions = JigsawStudioMarkerParser.directions(orientation);
        assertEquals(expectedFront, directions.front());
        assertEquals(expectedTop, directions.top());
    }
}
