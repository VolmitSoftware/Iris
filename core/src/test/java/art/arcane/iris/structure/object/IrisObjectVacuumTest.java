/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.IrisVacuumSettings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisObjectVacuumTest {

    @Test
    public void footprintExtentsMatchObjectWidthForEvenAndOdd() {
        int evenLow = IrisObjectVacuum.footprintLow(10);
        int evenHigh = IrisObjectVacuum.footprintHigh(10);
        assertEquals(-5, evenLow);
        assertEquals(4, evenHigh);
        assertEquals(10, evenHigh - evenLow + 1);

        int oddLow = IrisObjectVacuum.footprintLow(15);
        int oddHigh = IrisObjectVacuum.footprintHigh(15);
        assertEquals(-7, oddLow);
        assertEquals(7, oddHigh);
        assertEquals(15, oddHigh - oddLow + 1);

        assertEquals(0, IrisObjectVacuum.footprintLow(1));
        assertEquals(0, IrisObjectVacuum.footprintHigh(1));
    }

    @Test
    public void evenObjectRaisesFlushWithNoOverhang() {
        int low = IrisObjectVacuum.footprintLow(10);
        int high = IrisObjectVacuum.footprintHigh(10);
        int originalY = 100;
        int meetY = 110;

        for (int dx = low; dx <= high; dx++) {
            for (int dz = low; dz <= high; dz++) {
                int target = IrisObjectVacuum.columnTargetY(dx, dz, low, high, low, high, 12.0, 2.0, originalY, meetY);
                assertEquals("column inside footprint must be flush at the object base", meetY, target);
            }
        }

        int justOutsideHigh = IrisObjectVacuum.columnTargetY(high + 1, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertTrue("one block past the high edge must taper, not stay full", justOutsideHigh < meetY);
        assertTrue(justOutsideHigh > originalY);

        int justOutsideLow = IrisObjectVacuum.columnTargetY(low - 1, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertEquals("taper must be symmetric on both axes", justOutsideHigh, justOutsideLow);
    }

    @Test
    public void downwardBendCarvesWhenBaseBelowSurface() {
        int low = IrisObjectVacuum.footprintLow(10);
        int high = IrisObjectVacuum.footprintHigh(10);
        int originalY = 120;
        int meetY = 105;

        int center = IrisObjectVacuum.columnTargetY(0, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertEquals("inside footprint must carve down to the object base", meetY, center);
        assertTrue("downward bend must drop below the original surface", center < originalY);

        int far = IrisObjectVacuum.columnTargetY(high + 200, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertEquals("beyond the radius the surface is untouched", originalY, far);
    }

    @Test
    public void columnsOutsideRadiusAreUnchanged() {
        int low = -5;
        int high = 4;
        int originalY = 64;
        int meetY = 80;

        int beyond = IrisObjectVacuum.columnTargetY(high + 13, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertEquals(originalY, beyond);

        int atRadiusEdge = IrisObjectVacuum.columnTargetY(high + 12, 0, low, high, low, high, 12.0, 2.0, originalY, meetY);
        assertEquals(originalY, atRadiusEdge);
    }

    @Test
    public void resolveRadiusMatchesModesAndHonorsSettingsOverride() {
        assertEquals(12, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM, null));
        assertEquals(20, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM_HIGH, null));
        assertEquals(8, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM_FAST, null));
        assertEquals(12, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM_ORGANIC, null));
        assertEquals(12, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM_WAVY, null));

        IrisVacuumSettings settings = new IrisVacuumSettings();
        settings.setRadius(30);
        assertEquals(30, IrisObjectVacuum.resolveRadius(ObjectPlaceMode.VACUUM_FAST, settings));
    }

    @Test
    public void resolveStepIsCoarseOnlyForFast() {
        assertEquals(1, IrisObjectVacuum.resolveStep(ObjectPlaceMode.VACUUM));
        assertEquals(1, IrisObjectVacuum.resolveStep(ObjectPlaceMode.VACUUM_HIGH));
        assertEquals(2, IrisObjectVacuum.resolveStep(ObjectPlaceMode.VACUUM_FAST));
        assertEquals(1, IrisObjectVacuum.resolveStep(ObjectPlaceMode.VACUUM_ORGANIC));
        assertEquals(1, IrisObjectVacuum.resolveStep(ObjectPlaceMode.VACUUM_WAVY));
    }

    @Test
    public void isVacuumModeOnlyTrueForVacuumModes() {
        assertTrue(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.VACUUM));
        assertTrue(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.VACUUM_HIGH));
        assertTrue(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.VACUUM_FAST));
        assertTrue(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.VACUUM_ORGANIC));
        assertTrue(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.VACUUM_WAVY));
        assertFalse(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.CENTER_HEIGHT));
        assertFalse(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.STILT));
        assertFalse(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.PAINT));
        assertFalse(IrisObjectVacuum.isVacuumMode(ObjectPlaceMode.FLOATING));
    }

    @Test
    public void carveFloorPreservesBuriedObjectInsideFootprint() {
        int surfaceY = 122;
        int baseY = 110;
        int topY = 119;
        int meetY = baseY - 1;

        int insideFloor = IrisObjectVacuum.carveFloorY(meetY, topY, true);
        assertEquals("inside the footprint the carve must stop one above the object top", topY + 1, insideFloor);
        assertTrue("inside carve must not reach the object's blocks", insideFloor > topY);
        assertTrue("inside carve still removes the terrain burying the object", insideFloor <= surfaceY + 1);

        int ringFloor = IrisObjectVacuum.carveFloorY(meetY, topY, false);
        assertEquals("the ring carves down to the deformation target (the crater floor)", meetY + 1, ringFloor);
        assertTrue("ring crater is deeper than the object-protected inside floor", ringFloor < insideFloor);
    }

    @Test
    public void wavyOffsetIsZeroAtSeatAndRimSoSeatStaysFlushAndEdgeBlends() {
        assertEquals("directly under the object the wave must be zero so it seats flush", 0, IrisObjectVacuum.waveOffset(0.0, 12.0, 1.0, 3.0));
        assertEquals("at the outer rim the wave must be zero so it blends to terrain", 0, IrisObjectVacuum.waveOffset(12.0, 12.0, 1.0, 3.0));
        assertEquals("beyond the radius there is no wave", 0, IrisObjectVacuum.waveOffset(20.0, 12.0, 1.0, 3.0));
        assertEquals("amplitude 0 disables the wave entirely", 0, IrisObjectVacuum.waveOffset(6.0, 12.0, 1.0, 0.0));
    }

    @Test
    public void wavyOffsetIsSignedSymmetricAndPeaksMidSlope() {
        int up = IrisObjectVacuum.waveOffset(6.0, 12.0, 1.0, 3.0);
        int down = IrisObjectVacuum.waveOffset(6.0, 12.0, -1.0, 3.0);
        assertEquals("peak noise at mid-slope lifts the full amplitude", 3, up);
        assertEquals("negative noise pushes the slope down by the same amount", -3, down);
        assertEquals("equal and opposite noise mirrors the offset", up, -down);
        assertTrue("the wave never exceeds the configured amplitude", Math.abs(up) <= 3);

        int nearSeat = IrisObjectVacuum.waveOffset(1.0, 12.0, 1.0, 3.0);
        assertTrue("waves are strongest mid-slope and gentle near the flush seat", up > nearSeat);
    }

    @Test
    public void resolveWaveAmplitudeAndScaleUseDefaultsAndHonorOverrides() {
        assertEquals(3.0, IrisObjectVacuum.resolveWaveAmplitude(null), 0.0001);
        assertEquals(5.0, IrisObjectVacuum.resolveWaveScale(null), 0.0001);

        IrisVacuumSettings settings = new IrisVacuumSettings();
        settings.setWaveAmplitude(6);
        settings.setWaveScale(2.5);
        assertEquals(6.0, IrisObjectVacuum.resolveWaveAmplitude(settings), 0.0001);
        assertEquals(2.5, IrisObjectVacuum.resolveWaveScale(settings), 0.0001);

        IrisVacuumSettings disabled = new IrisVacuumSettings();
        disabled.setWaveAmplitude(0);
        assertEquals("amplitude 0 disables the wave", 0.0, IrisObjectVacuum.resolveWaveAmplitude(disabled), 0.0001);

        IrisVacuumSettings badScale = new IrisVacuumSettings();
        badScale.setWaveScale(0);
        assertEquals("non-positive scale falls back to the default", 5.0, IrisObjectVacuum.resolveWaveScale(badScale), 0.0001);
    }
}
