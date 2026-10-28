/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.modules.deposit.DepositHeightDistribution;
import art.arcane.iris.modules.deposit.DepositPlacementScope;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.host.ArrayHunk;
import com.volmit.hunk.Hunk;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DepositPlacerContainmentTest {
    @Test
    public void depositSurfaceLimitKeepsEveryColumnSevenBlocksBuried() {
        assertEquals(73, DepositPlacer.depositSurfaceLimit(80));
        assertEquals(24, DepositPlacer.depositSurfaceLimit(31));
        assertEquals(80, DepositPlacer.depositSurfaceLimit(80, 0));
    }

    @Test
    public void placementScopeSeparatesTerrainFromFloatingSolids() {
        assertTrue(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.TERRAIN, 70, 80, 7));
        assertFalse(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.TERRAIN, 74, 80, 7));
        assertFalse(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.ABOVE_TERRAIN, 80, 80, 0));
        assertTrue(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.ABOVE_TERRAIN, 81, 80, 0));
        assertTrue(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.FULL_HEIGHT, 1, 80, 64));
        assertTrue(DepositPlacer.placementSurfaceAllows(
                DepositPlacementScope.FULL_HEIGHT, 700, 80, 64));
    }

    @Test
    public void variantHeightUsesAbsoluteWorldY() {
        assertEquals(-32, DepositPlacer.absoluteWorldY(-64, 32));
        assertEquals(96, DepositPlacer.absoluteWorldY(0, 96));
    }

    @Test
    public void airAndFluidTargetsAreRejected() {
        BlockRegistry blocks = ApiChunk.blockRegistry();

        assertTrue(DepositPlacer.canReplaceDepositTarget(blocks, new BlockState("minecraft:stone")));
        assertFalse(DepositPlacer.canReplaceDepositTarget(blocks, ApiChunk.AIR));
        assertFalse(DepositPlacer.canReplaceDepositTarget(blocks, new BlockState("minecraft:water")));
        assertFalse(DepositPlacer.canReplaceDepositTarget(blocks, null));
    }

    @Test
    public void oreFrequencyMultiplierKeepsConfiguredShareOfVeins() {
        assertTrue(DepositPlacer.passesOreFrequency(0.4D, 0.399D));
        assertFalse(DepositPlacer.passesOreFrequency(0.4D, 0.4D));
        assertTrue(DepositPlacer.passesOreFrequency(1D, 0.999D));
        assertFalse(DepositPlacer.passesOreFrequency(0D, 0D));
    }

    @Test
    public void largerVeinsRemainCenteredAndInsideTheChunk() {
        assertEquals(6, DepositPlacer.clampDepositCenter(6, 5, 16));
        assertEquals(2, DepositPlacer.clampDepositCenter(1, 5, 16));
        assertEquals(13, DepositPlacer.clampDepositCenter(14, 5, 16));
        assertEquals(2, DepositPlacer.clampDepositCenter(1, 4, 16));
        assertEquals(14, DepositPlacer.clampDepositCenter(15, 4, 16));
    }

    @Test
    public void vanillaHeightProvidersSampleAuthoredBandsBeforeWorldClipping() {
        RNG uniform = new RNG(91L);
        boolean sampledBelowBottom = false;
        boolean sampledAboveBottom = false;
        for (int i = 0; i < 2_000; i++) {
            int y = DepositPlacer.sampleHeight(
                    DepositHeightDistribution.UNIFORM, uniform, -80, 80, 20);
            sampledBelowBottom |= y < 0;
            sampledAboveBottom |= y > 20;
        }

        assertTrue(sampledBelowBottom);
        assertTrue(sampledAboveBottom);
        assertEquals(14, DepositPlacer.sampleHeight(
                DepositHeightDistribution.UNIFORM, uniform, 14, 14, 10));
    }

    @Test
    public void clippedHeightProviderPreservesLegacyTerrainClipping() {
        RNG rng = new RNG(44L);
        for (int i = 0; i < 2_000; i++) {
            int y = DepositPlacer.sampleHeight(
                    DepositHeightDistribution.CLIPPED_UNIFORM, rng, -80, 80, 20);
            assertTrue(y >= 0);
            assertTrue(y <= 20);
        }
    }

    @Test
    public void triangleHeightProviderPeaksNearTheMidpoint() {
        RNG rng = new RNG(112L);
        int center = 0;
        int edges = 0;
        long total = 0L;
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            int y = DepositPlacer.sampleHeight(
                    DepositHeightDistribution.TRIANGLE, rng, -32, 32, 32);
            total += y;
            if (Math.abs(y) <= 4) {
                center++;
            }
            if (Math.abs(y) >= 28) {
                edges++;
            }
        }

        assertTrue(Math.abs(total / (double) samples) < 0.25D);
        assertTrue(center > edges * 4);
    }

    @Test
    public void exposureDiscardRequiresAdjacentAirAndPassingChanceRoll() {
        assertFalse(DepositPlacer.shouldDiscardExposed(1D, 0D, false));
        assertTrue(DepositPlacer.shouldDiscardExposed(1D, 0.999D, true));
        assertTrue(DepositPlacer.shouldDiscardExposed(0.5D, 0.499D, true));
        assertFalse(DepositPlacer.shouldDiscardExposed(0.5D, 0.5D, true));
        assertFalse(DepositPlacer.shouldDiscardExposed(0D, 0D, true));
    }

    @Test
    public void exposureProbeChecksOnlyInBoundsOrthogonalNeighbors() {
        BlockRegistry blocks = ApiChunk.blockRegistry();
        Hunk<BlockState> data = new ArrayHunk<>(BlockState.class, 3, 3, 3, new BlockState("minecraft:stone"));
        Predicate<BlockState> isAir = state -> blocks.hasTrait(state, BlockTrait.AIR);

        assertFalse(DepositPlacer.isAdjacentToAir(data, 1, 1, 1, isAir));
        data.set(2, 1, 1, ApiChunk.AIR);
        assertTrue(DepositPlacer.isAdjacentToAir(data, 1, 1, 1, isAir));
        assertFalse(DepositPlacer.isAdjacentToAir(data, 0, 0, 0, isAir));
    }
}
