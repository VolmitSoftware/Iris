package art.arcane.iris.pack.value;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class WeightedTest {
    @Test
    public void weightedEntriesCountAsCopiesOfThemselvesInARow() {
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("plains", 0));
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("plains", Weighted.MAX_WEIGHT + 1));
        assertThrows(NullPointerException.class, () -> new Weighted<String>(null, 1));

        List<Weighted<String>> entries = List.of(new Weighted<>("barren", 3), new Weighted<>("forest", 1));
        assertEquals("barren", Weighted.pick(entries, 0.74, Weighted.Edge.EARLIER));
        assertEquals("forest", Weighted.pick(entries, 0.76, Weighted.Edge.EARLIER));
        assertEquals("barren", Weighted.pick(entries, -0.5, Weighted.Edge.EARLIER));
        assertEquals("forest", Weighted.pick(entries, 1.5, Weighted.Edge.LATER));
        assertNull(Weighted.pick(List.<Weighted<String>>of(), 0.5, Weighted.Edge.EARLIER));
    }

    @Test
    public void anEdgeGoesWhereTheRuleSays() {
        List<Weighted<String>> entries = List.of(new Weighted<>("first", 1), new Weighted<>("second", 1));
        assertEquals("first", Weighted.pick(entries, 0.5, Weighted.Edge.EARLIER));
        assertEquals("second", Weighted.pick(entries, 0.5, Weighted.Edge.LATER));
        assertEquals("second", Weighted.pick(entries, Math.nextUp(0.5), Weighted.Edge.EARLIER));
    }

    @Test
    public void halfSlotWeightsMatchTheOldRoundedChildPick() {
        List<String> oldSlots = List.of("barren", "barren", "barren", "forest", "barren", "barren");
        List<Weighted<String>> converted = List.of(new Weighted<>("barren", 5), new Weighted<>("forest", 2), new Weighted<>("barren", 3));
        for (int step = 0; step <= 1000; step++) {
            double noise = step / 1000D;
            String oldPick = oldSlots.get((int) Math.round(noise * (oldSlots.size() - 1)));
            assertEquals("noise " + noise, oldPick, Weighted.pick(converted, noise, Weighted.Edge.LATER));
        }
    }

    @Test
    public void totalWeightAddsEveryEntry() {
        assertEquals(0, Weighted.totalWeight(List.of()));
        assertEquals(6, Weighted.totalWeight(List.of(new Weighted<>("a", 5), new Weighted<>("b", 1))));
    }
}
