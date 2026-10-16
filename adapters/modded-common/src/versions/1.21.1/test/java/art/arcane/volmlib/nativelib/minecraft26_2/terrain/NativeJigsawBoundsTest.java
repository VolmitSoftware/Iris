package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NativeJigsawBoundsTest {
    @Test
    public void verticalDistanceUsesInclusiveBlocksWithinExclusiveHeightLimits() {
        try (NativeJigsawBounds bounds = NativeJigsawBounds.open(OptionalInt.of(12))) {
            NativeJigsawBounds.heightLimits(-64, 320);
            NativeJigsawBounds.center(100);
            assertEquals(88, NativeJigsawBounds.minimum(40), 0);
            assertEquals(113, NativeJigsawBounds.maximum(160), 0);

            NativeJigsawBounds.center(-60);
            assertEquals(-64, NativeJigsawBounds.minimum(-120), 0);
            assertEquals(-47, NativeJigsawBounds.maximum(0), 0);

            NativeJigsawBounds.center(315);
            assertEquals(303, NativeJigsawBounds.minimum(255), 0);
            assertEquals(320, NativeJigsawBounds.maximum(375), 0);
        }
    }

    @Test
    public void nestedScopesRestoreTheOuterDistanceAndCenter() {
        try (NativeJigsawBounds outer = NativeJigsawBounds.open(OptionalInt.of(10))) {
            NativeJigsawBounds.heightLimits(-64, 320);
            NativeJigsawBounds.center(80);
            try (NativeJigsawBounds inner = NativeJigsawBounds.open(OptionalInt.of(2))) {
                NativeJigsawBounds.heightLimits(0, 256);
                NativeJigsawBounds.center(200);
                assertEquals(198, NativeJigsawBounds.minimum(0), 0);
                assertEquals(203, NativeJigsawBounds.maximum(256), 0);
            }
            assertEquals(70, NativeJigsawBounds.minimum(0), 0);
            assertEquals(91, NativeJigsawBounds.maximum(256), 0);
            try (NativeJigsawBounds unbounded = NativeJigsawBounds.open(OptionalInt.empty())) {
                assertEquals(-20, NativeJigsawBounds.minimum(-20), 0);
                assertEquals(400, NativeJigsawBounds.maximum(400), 0);
            }
            assertEquals(70, NativeJigsawBounds.minimum(0), 0);
        }
        assertEquals(-20, NativeJigsawBounds.minimum(-20), 0);
        assertEquals(400, NativeJigsawBounds.maximum(400), 0);
    }

    @Test
    public void failedGenerationClosesTheScopeAndRejectsNegativeDistance() {
        assertThrows(IllegalStateException.class, () -> {
            try (NativeJigsawBounds bounds = NativeJigsawBounds.open(OptionalInt.of(0))) {
                NativeJigsawBounds.heightLimits(-64, 320);
                NativeJigsawBounds.center(40);
                assertEquals(40, NativeJigsawBounds.minimum(-100), 0);
                assertEquals(41, NativeJigsawBounds.maximum(100), 0);
                throw new IllegalStateException("generation failed");
            }
        });
        assertEquals(-100, NativeJigsawBounds.minimum(-100), 0);
        assertEquals(100, NativeJigsawBounds.maximum(100), 0);
        assertThrows(IllegalArgumentException.class, () -> NativeJigsawBounds.open(OptionalInt.of(-1)));
        assertEquals(-100, NativeJigsawBounds.minimum(-100), 0);
    }
}
