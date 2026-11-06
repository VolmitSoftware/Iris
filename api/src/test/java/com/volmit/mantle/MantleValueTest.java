package com.volmit.mantle;

import com.volmit.geometry.BlockBox;
import com.volmit.resource.ResourceId;
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
        var owner = new MantleKey<>(ResourceId.parse("example:object-owner"), String.class);
        assertEquals(owner, new MantleKey<>(ResourceId.parse("example:object-owner"), String.class));
        assertNotEquals(owner, new MantleKey<>(ResourceId.parse("example:quest/location"), String.class));
        assertNotEquals(owner, new MantleKey<>(ResourceId.parse("example:object-owner"), Integer.class));
    }

    @Test
    void rejectsMissingPartsAndPrimitiveTypes() {
        var id = ResourceId.parse("a:b");
        assertThrows(NullPointerException.class, () -> new MantleKey<>(null, String.class));
        assertThrows(NullPointerException.class, () -> new MantleKey<>(id, null));
        assertThrows(IllegalArgumentException.class, () -> new MantleKey<>(id, int.class));
    }

    @Test
    void carvedVoidsOfAPlainFillAreTheSharedConstants() {
        for (var fill : CarvedVoid.Fill.values()) {
            assertSame(CarvedVoid.of(fill), CarvedVoid.of(fill));
            assertEquals(fill, CarvedVoid.of(fill).fill());
            assertTrue(CarvedVoid.of(fill).biome().isEmpty());
        }
        assertSame(CarvedVoid.LAVA, CarvedVoid.of(CarvedVoid.Fill.LAVA));
    }

    @Test
    void aCarvedVoidMayShowABiomeAndHasValueEquality() {
        var biome = new com.volmit.resource.ResourceId("pack", "cave/lush");
        var lush = CarvedVoid.AIR.withBiome(biome);
        assertEquals(CarvedVoid.Fill.AIR, lush.fill());
        assertEquals(biome, lush.biome().orElseThrow());
        assertEquals(CarvedVoid.AIR.withBiome(biome), lush);
        assertNotEquals(CarvedVoid.AIR, lush);
        assertThrows(NullPointerException.class, () -> new CarvedVoid(null, java.util.Optional.empty()));
        assertThrows(NullPointerException.class, () -> new CarvedVoid(CarvedVoid.Fill.OPEN, null));
    }
}
