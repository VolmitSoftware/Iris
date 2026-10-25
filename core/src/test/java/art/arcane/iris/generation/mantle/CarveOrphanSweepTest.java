package art.arcane.iris.generation.mantle;

import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CarveOrphanSweepTest {
    private static final int WORLD_HEIGHT = 64;
    private static final int WORLD_FLOOR_Y = 0;
    private static final int WORLD_CEILING_Y = WORLD_HEIGHT - 1;
    private static final int SURFACE_Y = 40;
    private static final int SURFACE_BREAK_DEPTH = 18;
    private static final int BAND_FLOOR_Y = SURFACE_Y - SURFACE_BREAK_DEPTH - 4;

    @Test
    public void interiorClumpsOfOneTwoAndFiveCellsAreMarkedCarved() {
        Fixture fixture = new Fixture();
        fixture.carveBox(1, 14, 25, 35, 1, 14);
        fixture.uncarve(5, 30, 5);
        fixture.uncarve(8, 30, 8);
        fixture.uncarve(9, 30, 8);
        fixture.uncarve(5, 33, 10);
        fixture.uncarve(4, 33, 10);
        fixture.uncarve(6, 33, 10);
        fixture.uncarve(5, 33, 9);
        fixture.uncarve(5, 33, 11);

        int marked = fixture.sweep();

        assertEquals(8, marked);
        assertTrue(fixture.wasMarked(5, 30, 5));
        assertTrue(fixture.wasMarked(8, 30, 8));
        assertTrue(fixture.wasMarked(9, 30, 8));
        assertTrue(fixture.wasMarked(5, 33, 10));
        assertTrue(fixture.wasMarked(4, 33, 10));
        assertTrue(fixture.wasMarked(6, 33, 10));
        assertTrue(fixture.wasMarked(5, 33, 9));
        assertTrue(fixture.wasMarked(5, 33, 11));
    }

    @Test
    public void stalactiteHangingFromSurfaceCrustIsKept() {
        Fixture fixture = new Fixture();
        fixture.carveBox(1, 14, 25, 35, 1, 14);
        fixture.uncarve(7, 35, 7);
        fixture.uncarve(7, 34, 7);
        fixture.uncarve(7, 33, 7);

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertFalse(fixture.wasMarked(7, 35, 7));
        assertFalse(fixture.wasMarked(7, 34, 7));
        assertFalse(fixture.wasMarked(7, 33, 7));
    }

    @Test
    public void componentTouchingSolidBelowBandFloorIsKept() {
        Fixture fixture = new Fixture();
        fixture.carveBox(0, 15, BAND_FLOOR_Y, 35, 0, 15);
        fixture.uncarve(7, BAND_FLOOR_Y, 7);

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertFalse(fixture.wasMarked(7, BAND_FLOOR_Y, 7));
    }

    @Test
    public void componentContainingSeamCellIsKept() {
        Fixture fixture = new Fixture();
        fixture.carveBox(0, 15, 25, 35, 0, 15);
        fixture.uncarve(0, 30, 7);
        fixture.uncarve(15, 31, 9);
        fixture.uncarve(6, 32, 0);
        fixture.uncarve(6, 33, 15);

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertFalse(fixture.wasMarked(0, 30, 7));
        assertFalse(fixture.wasMarked(15, 31, 9));
        assertFalse(fixture.wasMarked(6, 32, 0));
        assertFalse(fixture.wasMarked(6, 33, 15));
    }

    @Test
    public void componentLargerThanSixteenCellsIsKept() {
        Fixture fixture = new Fixture();
        fixture.carveBox(1, 14, 25, 35, 1, 14);
        for (int localX = 3; localX <= 5; localX++) {
            for (int localZ = 3; localZ <= 5; localZ++) {
                fixture.uncarve(localX, 27, localZ);
                fixture.uncarve(localX, 28, localZ);
            }
        }

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertFalse(fixture.wasMarked(4, 27, 4));
        assertFalse(fixture.wasMarked(4, 28, 4));
    }

    @Test
    public void componentOfExactlySixteenCellsIsMarkedCarved() {
        Fixture fixture = new Fixture();
        fixture.carveBox(1, 14, 25, 35, 1, 14);
        for (int localX = 3; localX <= 6; localX++) {
            for (int localZ = 3; localZ <= 6; localZ++) {
                fixture.uncarve(localX, 30, localZ);
            }
        }

        int marked = fixture.sweep();

        assertEquals(16, marked);
        assertTrue(fixture.wasMarked(3, 30, 3));
        assertTrue(fixture.wasMarked(6, 30, 6));
    }

    @Test
    public void protectedSurfaceFluidSupportIsNotMarkedCarved() {
        Fixture fixture = new Fixture();
        fixture.carveBox(1, 14, 25, 35, 1, 14);
        fixture.uncarve(7, 30, 7);
        fixture.protect(7, 30, 7);

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertFalse(fixture.wasMarked(7, 30, 7));
    }

    @Test
    public void sweepIsDeterministicAndIdempotent() {
        Fixture first = new Fixture();
        first.carveBox(1, 14, 25, 35, 1, 14);
        first.uncarve(5, 30, 5);
        first.uncarve(8, 30, 8);
        first.uncarve(9, 30, 8);

        Fixture second = new Fixture();
        second.carveBox(1, 14, 25, 35, 1, 14);
        second.uncarve(5, 30, 5);
        second.uncarve(8, 30, 8);
        second.uncarve(9, 30, 8);

        int firstMarked = first.sweep();
        int secondMarked = second.sweep();

        assertEquals(firstMarked, secondMarked);
        assertEquals(first.marks(), second.marks());

        first.clearMarks();
        int rerun = first.sweep();

        assertEquals(0, rerun);
        assertTrue(first.marks().isEmpty());
    }

    @Test
    public void chunkWithoutCarveMarkersInBandDoesNothing() {
        Fixture fixture = new Fixture();

        int marked = fixture.sweep();

        assertEquals(0, marked);
        assertTrue(fixture.marks().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void solidAboveTheCaveCeilingIsNeverSweptOpen() {
        IrisMatterSupport.ensureRegistered();
        int[] ceilings = new int[256];
        Arrays.fill(ceilings, CaveCeilingMask.UNMASKED);
        ceilings[PowerOfTwoCoordinates.packLocal16(5, 5)] = 30;
        for (boolean masked : new boolean[]{false, true}) {
            Matter[] sections = new Matter[WORLD_HEIGHT >> 4];
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(chunk.sectionCount()).thenReturn(sections.length);
            when(chunk.get(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)]);
            when(chunk.getOrCreate(anyInt())).thenAnswer(call -> {
                int index = call.getArgument(0);
                if (sections[index] == null) {
                    sections[index] = new IrisMatter(16, 16, 16);
                }
                return sections[index];
            });
            MatterCavern open = new MatterCavern(true, "", (byte) 0);
            for (int localX = 1; localX <= 14; localX++) {
                for (int y = 25; y <= 35; y++) {
                    for (int localZ = 1; localZ <= 14; localZ++) {
                        if (localX != 5 || localZ != 5 || y < 33 || y > 34) {
                            chunk.getOrCreate(y >> 4).slice(MatterCavern.class).set(localX, y & 15, localZ, open);
                        }
                    }
                }
            }
            int[] surfaceHeights = new int[256];
            Arrays.fill(surfaceHeights, SURFACE_Y);

            int marked = CarveOrphanSweep.sweepChunk(chunk, surfaceHeights, SURFACE_BREAK_DEPTH, WORLD_CEILING_Y, null,
                    masked ? ceilings : null);

            MatterCavern swept = sections[2].<MatterCavern>slice(MatterCavern.class).get(5, 33 & 15, 5);
            assertEquals(masked ? 0 : 2, marked);
            assertEquals(!masked, swept != null);
        }
    }

    private static final class Fixture implements CarveOrphanSweep.CarveAccess {
        private final boolean[] carved = new boolean[16 * WORLD_HEIGHT * 16];
        private final int[] surfaceHeights = new int[256];
        private final List<Integer> marks = new ArrayList<>();
        private int protectedCell = -1;

        private Fixture() {
            Arrays.fill(surfaceHeights, SURFACE_Y);
        }

        private int sweep() {
            return CarveOrphanSweep.sweep(surfaceHeights, SURFACE_BREAK_DEPTH, WORLD_FLOOR_Y, WORLD_CEILING_Y, this);
        }

        private void carveBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            for (int localX = minX; localX <= maxX; localX++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int localZ = minZ; localZ <= maxZ; localZ++) {
                        carved[index(localX, y, localZ)] = true;
                    }
                }
            }
        }

        private void uncarve(int localX, int y, int localZ) {
            carved[index(localX, y, localZ)] = false;
        }

        private boolean wasMarked(int localX, int y, int localZ) {
            return marks.contains(index(localX, y, localZ));
        }

        private void protect(int localX, int y, int localZ) {
            protectedCell = index(localX, y, localZ);
        }

        private List<Integer> marks() {
            return marks;
        }

        private void clearMarks() {
            marks.clear();
        }

        private static int index(int localX, int y, int localZ) {
            return (y * 256) + (localX * 16) + localZ;
        }

        @Override
        public boolean isCarved(int localX, int y, int localZ) {
            return carved[index(localX, y, localZ)];
        }

        @Override
        public void markCarved(int localX, int y, int localZ) {
            carved[index(localX, y, localZ)] = true;
            marks.add(index(localX, y, localZ));
        }

        @Override
        public boolean isProtected(int localX, int y, int localZ) {
            return protectedCell == index(localX, y, localZ);
        }
    }
}
