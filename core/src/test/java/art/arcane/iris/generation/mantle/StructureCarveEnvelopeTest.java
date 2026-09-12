package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.IrisStructureCarveShape;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseType;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureCarveEnvelopeTest {
    @Test
    public void exactStructureFootprintAlwaysCarves() {
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ERODED, 0D, 0D, 1D));
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ROUNDED, 0D, 0D, 1D));
    }

    @Test
    public void boxModeKeepsStraightCandidateVolume() {
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.BOX, 100D, 0D, 1D));
    }

    @Test
    public void roundedModeStopsAtConfiguredReach() {
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ROUNDED, 1D, 0D, 1D));
        assertFalse(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ROUNDED, 1.000001D, 1D, 0D));
    }

    @Test
    public void zeroErosionStrengthMatchesRoundedMode() {
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ERODED, 1D, 0D, 0D));
        assertFalse(StructureCarveEnvelope.shouldCarveOverboreCell(
                IrisStructureCarveShape.ERODED, 1.000001D, 1D, 0D));
    }

    @Test
    public void zeroConfiguredCeilingHasNoErodedExtension() {
        assertEquals(0D, StructureCarveEnvelope.erodedUpReach(null, 0.16D, 1D, 0, 0, 0), 0D);
    }

    @Test
    public void zeroErosionStrengthKeepsTheRoundedCeiling() {
        assertEquals(10D, StructureCarveEnvelope.erodedUpReach(null, 0.16D, 0D, 0, 0, 10), 0D);
    }

    @Test
    public void erosionStrengthAndNoiseAreClampedDeterministically() {
        assertEquals(0.2D, StructureCarveEnvelope.overboreBoundaryLimit(-10D, 0.8D), 1.0E-12D);
        assertEquals(1D, StructureCarveEnvelope.overboreBoundaryLimit(10D, 0.8D), 0D);
        assertEquals(0.5D, StructureCarveEnvelope.overboreBoundaryLimit(0.5D, 1D), 0D);
        assertEquals(1D, StructureCarveEnvelope.overboreBoundaryLimit(0D, -1D), 0D);
        assertEquals(0D, StructureCarveEnvelope.overboreBoundaryLimit(0D, 10D), 0D);
        assertTrue(StructureCarveEnvelope.shouldCarveOverboreCell(
                null, 0.25D, 0.5D, 1D));
        assertFalse(StructureCarveEnvelope.shouldCarveOverboreCell(
                null, 0.250001D, 0.5D, 1D));
    }

    @Test
    public void erodedCeilingStaysInsideTheAdvertisedCarveShapeExtension() {
        CNG roll = CNG.signature(new RNG(4242L));

        for (double strength : new double[]{0.25D, 0.5D, 1D}) {
            int limit = IrisStructureCarveShape.ERODED.maximumCeilingExtension(10, strength);
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 32; z++) {
                    double reach = StructureCarveEnvelope.erodedUpReach(roll, 0.05D, strength, x, z, 10);
                    assertTrue(reach >= 1D);
                    assertTrue(reach <= limit);
                }
            }
        }
    }

    @Test
    public void floorModulationNeverCutsPastTheConfiguredPadding() {
        CNG roll = CNG.signature(new RNG(9001L));

        assertEquals(0D, StructureCarveEnvelope.erodedDownReach(null, 0.05D, 1D, 0, 0, 0), 0D);
        assertEquals(6D, StructureCarveEnvelope.erodedDownReach(null, 0.05D, 0D, 0, 0, 6), 0D);
        boolean modulated = false;
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                double reach = StructureCarveEnvelope.erodedDownReach(roll, 0.05D, 1D, x, z, 6);
                assertTrue(reach >= 0D);
                assertTrue(reach <= 6D);
                modulated |= reach < 6D;
            }
        }
        assertTrue(modulated);
    }

    @Test
    public void lobedSideReachWandersInsideTheConfiguredPaddingBand() {
        CNG lobe = new CNG(new RNG(1337L), NoiseType.CLOVER, 1D, 1);
        double lowest = 14D;
        double highest = 0D;

        for (int x = -256; x <= 256; x++) {
            for (int z = -256; z <= 256; z += 256) {
                double reach = StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 0.85D, x, z, 14);
                assertTrue(reach >= 14D * 0.15D - 1.0E-9D);
                assertTrue(reach <= 14D);
                lowest = Math.min(lowest, reach);
                highest = Math.max(highest, reach);
            }
        }

        assertTrue(highest - lowest > 8D);
    }

    @Test
    public void zeroLobeStrengthKeepsTheUniformPadding() {
        CNG lobe = new CNG(new RNG(1337L), 1D, 1);

        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                assertEquals(14D, StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 0D, x, z, 14), 0D);
                assertEquals(9D, StructureCarveEnvelope.lobedUpReach(lobe, 0.015D, 0D, x, z, 9D), 0D);
            }
        }
        assertEquals(0D, StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 1D, 0, 0, 0), 0D);
        assertEquals(14D, StructureCarveEnvelope.lobedSideReach(null, 0.015D, 1D, 0, 0, 14), 0D);
        assertEquals(9D, StructureCarveEnvelope.lobedUpReach(null, 0.015D, 1D, 0, 0, 9D), 0D);
    }

    @Test
    public void lobeWavelengthSpansTensOfBlocksAtTheDerivedFrequency() {
        CNG lobe = new CNG(new RNG(20250729L), 1D, 1);
        int span = 2048;
        int crossings = 0;
        double previous = 0D;

        for (int x = 0; x < span; x++) {
            double centered = StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 1D, x, 137, 1000)
                    - 500D;
            if (x > 0 && previous * centered < 0D) {
                crossings++;
            }
            previous = centered;
        }

        double wavelength = 2D * span / crossings;
        assertTrue("wavelength " + wavelength, wavelength >= 24D && wavelength <= 48D);
    }

    @Test
    public void ceilingLobeIsHalfTheWallLobe() {
        CNG lobe = new CNG(new RNG(4242L), 1D, 1);

        for (int x = -96; x <= 96; x += 3) {
            for (int z = -96; z <= 96; z += 3) {
                double side = StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 0.85D, x, z, 1000);
                double up = StructureCarveEnvelope.lobedUpReach(lobe, 0.015D, 0.85D, x, z, 1000D);
                assertEquals((1000D - side) / 2D, 1000D - up, 1.0E-9D);
                assertTrue(up <= 1000D);
                assertTrue(up >= 1000D * (1D - 0.85D / 2D) - 1.0E-9D);
            }
        }
        assertEquals(1D, StructureCarveEnvelope.lobedUpReach(lobe, 0.015D, 1D, 0, 0, 1D), 0D);
        assertEquals(0D, StructureCarveEnvelope.lobedUpReach(lobe, 0.015D, 1D, 0, 0, 0D), 0D);
    }

    @Test
    public void lobeSamplingIsAPureFunctionOfTheColumn() {
        CNG lobe = new CNG(new RNG(77L), 1D, 1);

        for (int x = -32; x <= 32; x += 7) {
            for (int z = -32; z <= 32; z += 7) {
                assertEquals(
                        StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 0.85D, x, z, 14),
                        StructureCarveEnvelope.lobedSideReach(lobe, 0.015D, 0.85D, x, z, 14), 0D);
            }
        }
    }

    @Test
    public void verticalDistanceIsZeroInsideTheColumnSourceSpan() {
        assertEquals(0D, StructureCarveEnvelope.normalizedVerticalDistance(
                40, 32, 48, 4D, 2D), 0D);
        assertEquals(0.5D, StructureCarveEnvelope.normalizedVerticalDistance(
                50, 32, 48, 4D, 2D), 1.0E-12D);
        assertEquals(1.5D, StructureCarveEnvelope.normalizedVerticalDistance(
                29, 32, 48, 4D, 2D), 1.0E-12D);
    }
}
