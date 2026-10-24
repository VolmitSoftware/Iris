package com.volmit.mantle;

import com.volmit.geometry.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MantleValueTest {
    @Test
    void boundsUseWorldXZHeightsAboveTheBottomAndExclusiveMaximum() {
        var box = new BlockBox(-16, 0, -16, 16, 384, 16);
        assertTrue(box.contains(-16, 0, -16));
        assertTrue(box.contains(15, 383, 15));
        assertFalse(box.contains(16, 0, 0));
        assertFalse(box.contains(0, 384, 0));
        assertFalse(box.contains(0, 0, 16));
        assertFalse(box.contains(0, -1, 0));
        assertTrue(box.contains(box));
        assertTrue(box.contains(new BlockBox(0, 0, 0, 1, 1, 1)));
        assertFalse(box.contains(new BlockBox(-17, 0, 0, 1, 1, 1)));
        assertThrows(NullPointerException.class, () -> box.contains(null));
    }

    @Test
    void rejectsInversionButAllowsEmptyBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(1, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(0, 1, 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(0, 0, 1, 1, 1, 0));
        assertFalse(new BlockBox(0, 0, 0, 0, 0, 0).contains(0, 0, 0));
        var extremes = new BlockBox(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 384, 1);
        assertTrue(extremes.contains(Integer.MIN_VALUE, 0, 0));
    }

    @Test
    void keysSeparateSameTypedLayersAndHaveValueEquality() {
        var owner = new MantleKey<>("example:object-owner", String.class);
        assertEquals(owner, new MantleKey<>("example:object-owner", String.class));
        assertNotEquals(owner, new MantleKey<>("example:quest/location", String.class));
        assertNotEquals(owner, new MantleKey<>("example:object-owner", Integer.class));
    }

    @Test
    void rejectsInvalidKeys() {
        for (String id : new String[]{"", "owner", ":owner", "example:", "Example:owner", "a:b:c", "a: b"}) {
            assertThrows(IllegalArgumentException.class, () -> new MantleKey<>(id, String.class));
        }
        assertThrows(NullPointerException.class, () -> new MantleKey<>(null, String.class));
        assertThrows(NullPointerException.class, () -> new MantleKey<>("a:b", null));
        assertThrows(IllegalArgumentException.class, () -> new MantleKey<>("a:b", int.class));
    }
}
