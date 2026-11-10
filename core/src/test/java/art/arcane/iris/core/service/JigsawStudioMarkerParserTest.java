package art.arcane.iris.core.service;

import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JigsawStudioMarkerParserTest {
    @Test
    public void mapsEveryJigsawOrientation() {
        assertDirections("down_east", JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.EAST_POSITIVE_X);
        assertDirections("down_north", JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.NORTH_NEGATIVE_Z);
        assertDirections("down_south", JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.SOUTH_POSITIVE_Z);
        assertDirections("down_west", JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.WEST_NEGATIVE_X);
        assertDirections("up_east", JigsawDirection.UP_POSITIVE_Y, JigsawDirection.EAST_POSITIVE_X);
        assertDirections("up_north", JigsawDirection.UP_POSITIVE_Y, JigsawDirection.NORTH_NEGATIVE_Z);
        assertDirections("up_south", JigsawDirection.UP_POSITIVE_Y, JigsawDirection.SOUTH_POSITIVE_Z);
        assertDirections("up_west", JigsawDirection.UP_POSITIVE_Y, JigsawDirection.WEST_NEGATIVE_X);
        assertDirections("west_up", JigsawDirection.WEST_NEGATIVE_X, JigsawDirection.UP_POSITIVE_Y);
        assertDirections("east_up", JigsawDirection.EAST_POSITIVE_X, JigsawDirection.UP_POSITIVE_Y);
        assertDirections("north_up", JigsawDirection.NORTH_NEGATIVE_Z, JigsawDirection.UP_POSITIVE_Y);
        assertDirections("south_up", JigsawDirection.SOUTH_POSITIVE_Z, JigsawDirection.UP_POSITIVE_Y);
    }

    @Test
    public void rejectsAnOrientationNoJigsawHas() {
        assertThrows(IllegalArgumentException.class, () -> JigsawStudioMarkerParser.directions("sideways"));
    }

    @Test
    public void parsesMarkerMetadataAndSignedPriorities() {
        Map<String, Object> nbt = markerNbt();
        nbt.put("channel", "castle/door");
        nbt.put("selection_priority", -4);
        nbt.put("placement_priority", 17L);

        IrisJigsawConnector connector = JigsawStudioMarkerParser.parse(
                nbt, "north_up", 3, 5, 7);

        assertEquals(3, connector.getPosition().x());
        assertEquals(5, connector.getPosition().y());
        assertEquals(7, connector.getPosition().z());
        assertEquals(JigsawDirection.NORTH_NEGATIVE_Z, connector.getDirection());
        assertEquals(JigsawDirection.UP_POSITIVE_Y, connector.getTop());
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
                markerNbt(), "south_up", 0, 0, 0);

        assertEquals("", connector.getChannel());
        assertEquals(0, connector.getSelectionPriority());
        assertEquals(0, connector.getPlacementPriority());
    }

    @Test
    public void rejectsMissingRequiredMarkerMetadata() {
        Map<String, Object> nbt = markerNbt();
        nbt.remove("pool");

        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMarkerParser.parse(nbt, "north_up", 0, 0, 0));
    }

    @Test
    public void rejectsNonStudioPoolNamespaces() {
        Map<String, Object> nbt = markerNbt();
        nbt.put("pool", "minecraft:fort/pool");

        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMarkerParser.parse(nbt, "north_up", 0, 0, 0));
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
            String orientation,
            JigsawDirection expectedFront,
            JigsawDirection expectedTop
    ) {
        JigsawStudioMarkerParser.Directions directions = JigsawStudioMarkerParser.directions(orientation);
        assertEquals(expectedFront, directions.front());
        assertEquals(expectedTop, directions.top());
    }
}
