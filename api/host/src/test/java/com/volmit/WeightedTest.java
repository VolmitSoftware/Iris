package com.volmit;

import com.volmit.world.Weighted;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightedTest {
    @Test
    void weightedEntriesCountAsCopiesOfThemselvesInARow() {
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("plains", 0));
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("plains", Weighted.MAX_WEIGHT + 1));
        assertThrows(NullPointerException.class, () -> new Weighted<String>(null, 1));

        var entries = List.of(new Weighted<>("barren", 3), new Weighted<>("forest", 1));
        assertEquals("barren", Weighted.pick(entries, 0.74, Weighted.Edge.EARLIER));
        assertEquals("forest", Weighted.pick(entries, 0.76, Weighted.Edge.EARLIER));
        assertEquals("barren", Weighted.pick(entries, -0.5, Weighted.Edge.EARLIER));
        assertEquals("forest", Weighted.pick(entries, 1.5, Weighted.Edge.LATER));
        assertNull(Weighted.pick(List.<Weighted<String>>of(), 0.5, Weighted.Edge.EARLIER));
    }

    @Test
    void anEdgeGoesWhereTheRuleSays() {
        var entries = List.of(new Weighted<>("first", 1), new Weighted<>("second", 1));
        assertEquals("first", Weighted.pick(entries, 0.5, Weighted.Edge.EARLIER));
        assertEquals("second", Weighted.pick(entries, 0.5, Weighted.Edge.LATER));
        assertEquals("second", Weighted.pick(entries, Math.nextUp(0.5), Weighted.Edge.EARLIER));
    }

    @Test
    void halfSlotWeightsMatchTheOldRoundedChildPick() {
        var oldSlots = List.of("barren", "barren", "barren", "forest", "barren", "barren");
        var converted = List.of(new Weighted<>("barren", 5), new Weighted<>("forest", 2), new Weighted<>("barren", 3));
        for (int step = 0; step <= 1000; step++) {
            double noise = step / 1000D;
            String oldPick = oldSlots.get((int) Math.round(noise * (oldSlots.size() - 1)));
            assertEquals(oldPick, Weighted.pick(converted, noise, Weighted.Edge.LATER), "noise " + noise);
        }
    }
}
