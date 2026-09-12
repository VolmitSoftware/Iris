package art.arcane.iris.world.tree;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeMarkerTraversalTest {
    @Test
    public void traversalUsesExactMarkerAndIncludesTriggerOnce() {
        String marker = "trees/oak@7";
        TreeMarkerTraversal.Position trigger = new TreeMarkerTraversal.Position(0, 64, 0);
        Map<TreeMarkerTraversal.Position, String> markers = new HashMap<>();
        markers.put(trigger, marker);
        markers.put(new TreeMarkerTraversal.Position(1, 65, 1), marker);
        markers.put(new TreeMarkerTraversal.Position(2, 65, 1), "trees/oak@8");

        TreeMarkerTraversal.Discovery discovery = TreeMarkerTraversal.discover(
                trigger,
                marker,
                -64,
                320,
                (x, y, z) -> markers.get(new TreeMarkerTraversal.Position(x, y, z))
        );

        assertTrue(discovery.complete());
        assertEquals(2, discovery.members().size());
        assertEquals(trigger, discovery.members().getFirst());
        assertEquals(1, discovery.members().stream().filter(trigger::equals).count());
    }

    @Test
    public void matchingOwnershipBeyondAxisBoundReportsIncomplete() {
        String marker = "trees/giant@99";
        TreeMarkerTraversal.Position trigger = new TreeMarkerTraversal.Position(0, 64, 0);

        TreeMarkerTraversal.Discovery discovery = TreeMarkerTraversal.discover(
                trigger,
                marker,
                -64,
                320,
                (x, y, z) -> y == 64 && z == 0 && x >= 0 && x <= TreeMarkerTraversal.MAX_AXIS_DISTANCE + 1
                        ? marker
                        : null
        );

        assertFalse(discovery.complete());
        assertEquals(TreeMarkerTraversal.MAX_AXIS_DISTANCE + 1, discovery.members().size());
    }

    @Test
    public void connectedMembersAreDiscoveredInOutwardErosionOrder() {
        String marker = "trees/giant@12";
        TreeMarkerTraversal.Position trigger = new TreeMarkerTraversal.Position(0, 64, 0);
        TreeMarkerTraversal.Position near = new TreeMarkerTraversal.Position(1, 64, 0);
        TreeMarkerTraversal.Position far = new TreeMarkerTraversal.Position(2, 64, 0);
        Map<TreeMarkerTraversal.Position, String> markers = new HashMap<>();
        markers.put(trigger, marker);
        markers.put(near, marker);
        markers.put(far, marker);

        TreeMarkerTraversal.Discovery discovery = TreeMarkerTraversal.discover(
                trigger,
                marker,
                -64,
                320,
                (x, y, z) -> markers.get(new TreeMarkerTraversal.Position(x, y, z))
        );

        assertEquals(List.of(trigger, near, far), discovery.members());
    }
}
