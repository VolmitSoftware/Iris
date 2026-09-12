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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositHeightDistribution;
import art.arcane.iris.generation.decoration.IrisDepositPlacementScope;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDepositModifierContainmentTest {
    @Test
    public void depositSurfaceLimitKeepsEveryColumnSevenBlocksBuried() {
        assertEquals(73, IrisDepositModifier.depositSurfaceLimit(80));
        assertEquals(24, IrisDepositModifier.depositSurfaceLimit(31));
        assertEquals(80, IrisDepositModifier.depositSurfaceLimit(80, 0));
    }

    @Test
    public void placementScopeSeparatesTerrainFromFloatingSolids() {
        assertTrue(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.TERRAIN, 70, 80, 7));
        assertFalse(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.TERRAIN, 74, 80, 7));
        assertFalse(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.ABOVE_TERRAIN, 80, 80, 0));
        assertTrue(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.ABOVE_TERRAIN, 81, 80, 0));
        assertTrue(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.FULL_HEIGHT, 1, 80, 64));
        assertTrue(IrisDepositModifier.placementSurfaceAllows(
                IrisDepositPlacementScope.FULL_HEIGHT, 700, 80, 64));
    }

    @Test
    public void variantHeightUsesAbsoluteWorldY() {
        assertEquals(-32, IrisDepositModifier.absoluteWorldY(-64, 32));
        assertEquals(96, IrisDepositModifier.absoluteWorldY(0, 96));
    }

    @Test
    public void airAndFluidTargetsAreRejected() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        PlatformBlockState fluid = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(fluid.isFluid()).thenReturn(true);

        assertTrue(IrisDepositModifier.canReplaceDepositTarget(solid));
        assertFalse(IrisDepositModifier.canReplaceDepositTarget(air));
        assertFalse(IrisDepositModifier.canReplaceDepositTarget(fluid));
        assertFalse(IrisDepositModifier.canReplaceDepositTarget(null));
    }

    @Test
    public void surfacePolicyReplacesBuriedHostPolicyWithoutAffectingCaves() {
        IrisDepositGenerator generator = mock(IrisDepositGenerator.class);
        IrisBiome biome = mock(IrisBiome.class);
        PlatformBlockState sand = mock(PlatformBlockState.class);
        PlatformBlockState basalt = mock(PlatformBlockState.class);
        when(generator.hasSurfaceReplaceableBlocks(biome)).thenReturn(true);
        when(generator.canReplaceSurface(sand, biome)).thenReturn(true);
        when(generator.canReplace(sand)).thenReturn(false);
        when(generator.canReplaceSurface(basalt, biome)).thenReturn(false);
        when(generator.canReplace(basalt)).thenReturn(true);

        assertTrue(IrisDepositModifier.canReplaceDepositHost(generator, sand, biome, true));
        assertFalse(IrisDepositModifier.canReplaceDepositHost(generator, sand, biome, false));
        assertFalse(IrisDepositModifier.canReplaceDepositHost(generator, basalt, biome, true));
        assertTrue(IrisDepositModifier.canReplaceDepositHost(generator, basalt, biome, false));
    }

    @Test
    public void oreFrequencyMultiplierKeepsConfiguredShareOfVeins() {
        assertTrue(IrisDepositModifier.passesOreFrequency(0.4D, 0.399D));
        assertFalse(IrisDepositModifier.passesOreFrequency(0.4D, 0.4D));
        assertTrue(IrisDepositModifier.passesOreFrequency(1D, 0.999D));
        assertFalse(IrisDepositModifier.passesOreFrequency(0D, 0D));
    }

    @Test
    public void largerVeinsRemainCenteredAndInsideTheChunk() {
        assertEquals(6, IrisDepositModifier.clampDepositCenter(6, 5, 16));
        assertEquals(2, IrisDepositModifier.clampDepositCenter(1, 5, 16));
        assertEquals(13, IrisDepositModifier.clampDepositCenter(14, 5, 16));
        assertEquals(2, IrisDepositModifier.clampDepositCenter(1, 4, 16));
        assertEquals(14, IrisDepositModifier.clampDepositCenter(15, 4, 16));
    }

    @Test
    public void vanillaHeightProvidersSampleAuthoredBandsBeforeWorldClipping() {
        RNG uniform = new RNG(91L);
        boolean sampledBelowBottom = false;
        boolean sampledAboveBottom = false;
        for (int i = 0; i < 2_000; i++) {
            int y = IrisDepositModifier.sampleHeight(
                    IrisDepositHeightDistribution.UNIFORM, uniform, -80, 80, 20);
            sampledBelowBottom |= y < 0;
            sampledAboveBottom |= y > 20;
        }

        assertTrue(sampledBelowBottom);
        assertTrue(sampledAboveBottom);
        assertEquals(14, IrisDepositModifier.sampleHeight(
                IrisDepositHeightDistribution.UNIFORM, uniform, 14, 14, 10));
    }

    @Test
    public void clippedHeightProviderPreservesLegacyTerrainClipping() {
        RNG rng = new RNG(44L);
        for (int i = 0; i < 2_000; i++) {
            int y = IrisDepositModifier.sampleHeight(
                    IrisDepositHeightDistribution.CLIPPED_UNIFORM, rng, -80, 80, 20);
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
            int y = IrisDepositModifier.sampleHeight(
                    IrisDepositHeightDistribution.TRIANGLE, rng, -32, 32, 32);
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
        assertFalse(IrisDepositModifier.shouldDiscardExposed(1D, 0D, false));
        assertTrue(IrisDepositModifier.shouldDiscardExposed(1D, 0.999D, true));
        assertTrue(IrisDepositModifier.shouldDiscardExposed(0.5D, 0.499D, true));
        assertFalse(IrisDepositModifier.shouldDiscardExposed(0.5D, 0.5D, true));
        assertFalse(IrisDepositModifier.shouldDiscardExposed(0D, 0D, true));
    }

    @Test
    public void terrainSurfaceIncludesExteriorAirButExcludesCaveAir() {
        Hunk<PlatformBlockState> data = Hunk.newHunk(3, 3, 3);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState exteriorAir = mock(PlatformBlockState.class);
        PlatformBlockState caveAir = mock(PlatformBlockState.class);
        when(exteriorAir.isAir()).thenReturn(true);
        when(exteriorAir.key()).thenReturn("minecraft:air");
        when(caveAir.isAir()).thenReturn(true);
        when(caveAir.key()).thenReturn("minecraft:cave_air");
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    data.setRaw(x, y, z, solid);
                }
            }
        }

        assertTrue(IrisDepositModifier.isTerrainSurface(data, 1, 1, 1, 1));
        assertFalse(IrisDepositModifier.isTerrainSurface(data, 1, 1, 1, 2));
        data.setRaw(2, 1, 1, exteriorAir);
        assertTrue(IrisDepositModifier.isTerrainSurface(data, 1, 1, 1, 2));
        data.setRaw(2, 1, 1, caveAir);
        assertFalse(IrisDepositModifier.isTerrainSurface(data, 1, 1, 1, 2));
        assertTrue(IrisDepositModifier.isAdjacentToAir(data, 1, 1, 1));
    }

    @Test
    public void exposureProbeChecksOnlyInBoundsOrthogonalNeighbors() {
        Hunk<PlatformBlockState> data = Hunk.newHunk(3, 3, 3);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    data.setRaw(x, y, z, solid);
                }
            }
        }

        assertFalse(IrisDepositModifier.isAdjacentToAir(data, 1, 1, 1));
        data.setRaw(2, 1, 1, air);
        assertTrue(IrisDepositModifier.isAdjacentToAir(data, 1, 1, 1));
        assertFalse(IrisDepositModifier.isAdjacentToAir(data, 0, 0, 0));
    }
}
