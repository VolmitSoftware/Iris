package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FloatingIslandSampleBottomYTest {
    private FloatingIslandSample buildSample(int islandBaseY, boolean[] solidMask) {
        int topIdx = 0;
        int solidCount = 0;
        for (int i = solidMask.length - 1; i >= 0; i--) {
            if (solidMask[i]) {
                topIdx = i;
                break;
            }
        }
        for (boolean b : solidMask) {
            if (b) {
                solidCount++;
            }
        }
        return FloatingIslandSample.constructForTest(islandBaseY, solidMask.length, topIdx, solidCount, solidMask);
    }

    @Test
    public void bottomY_firstMaskTrue_returnsIslandBaseY() {
        boolean[] mask = {true, true, false};
        FloatingIslandSample sample = buildSample(100, mask);
        assertEquals(100, sample.bottomY());
    }

    @Test
    public void bottomY_lowestSolidAtOffset_returnsIslandBaseYPlusOffset() {
        boolean[] mask = {false, false, true, true};
        FloatingIslandSample sample = buildSample(50, mask);
        assertEquals(52, sample.bottomY());
    }

    @Test
    public void bottomY_allFalseMask_returnsNegativeOne() {
        boolean[] mask = {false, false, false};
        FloatingIslandSample sample = buildSample(100, mask);
        assertEquals(-1, sample.bottomY());
    }

    @Test
    public void bottomY_isCached_sameSampleReturnsSameValue() {
        boolean[] mask = {false, true, true};
        FloatingIslandSample sample = buildSample(200, mask);
        int first = sample.bottomY();
        int second = sample.bottomY();
        assertEquals(first, second);
    }

    @Test
    public void mergedEntryMask_preservesTopAndBottomOwners() {
        IrisFloatingChildBiomes bottom = new IrisFloatingChildBiomes();
        IrisFloatingChildBiomes top = new IrisFloatingChildBiomes();
        boolean[] mask = {true, true, false, true};
        IrisFloatingChildBiomes[] entryMask = {bottom, bottom, null, top};
        FloatingIslandSample sample = FloatingIslandSample.constructForTest(top, 80, mask.length, 3, 3, mask, entryMask);

        assertSame(top, sample.entry);
        assertSame(bottom, sample.bottomEntry());
        assertSame(bottom, sample.entryAt(1));
        assertSame(top, sample.entryAt(3));
    }

    @Test
    public void solidifyUncarvedInterior_fillsGapsBetweenSolids() {
        boolean[] mask = {false, true, false, false, true, false};
        int count = FloatingIslandSample.solidifyUncarvedInterior(mask);
        assertEquals(4, count);
        assertEquals(false, mask[0]);
        assertEquals(true, mask[1]);
        assertEquals(true, mask[2]);
        assertEquals(true, mask[3]);
        assertEquals(true, mask[4]);
        assertEquals(false, mask[5]);
    }

    @Test
    public void solidifyUncarvedInterior_emptyMaskStaysEmpty() {
        boolean[] mask = {false, false, false};
        int count = FloatingIslandSample.solidifyUncarvedInterior(mask);
        assertEquals(0, count);
        assertEquals(false, mask[0]);
        assertEquals(false, mask[1]);
        assertEquals(false, mask[2]);
    }

    @Test
    public void roundedEdgeHeight_zeroFade_removesEdgeWall() {
        assertEquals(0, FloatingIslandSample.roundedEdgeHeight(18, 0.0));
    }

    @Test
    public void roundedEdgeDepth_zeroFade_removesMinimumTailAtEdge() {
        assertEquals(0, FloatingIslandSample.roundedEdgeDepth(10, 20, 1.0, 0.0));
    }

    @Test
    public void roundedEdgeDepth_fullFade_keepsConfiguredDepth() {
        assertEquals(15, FloatingIslandSample.roundedEdgeDepth(10, 20, 0.5, 1.0));
    }

    @Test
    public void roundedEdgeDepth_fixedDepthFollowsOnlyFootprintFade() {
        assertEquals(0, FloatingIslandSample.roundedEdgeDepth(20, 20, 0.0, 0.0));
        assertEquals(5, FloatingIslandSample.roundedEdgeDepth(20, 20, 0.0, 0.25));
        assertEquals(10, FloatingIslandSample.roundedEdgeDepth(20, 20, 1.0, 0.5));
        assertEquals(20, FloatingIslandSample.roundedEdgeDepth(20, 20, 0.0, 1.0));
    }

    @Test
    public void effectiveWallWarpAmplitude_fadesWarpOutAtRoundedRim() {
        assertEquals(0.0, FloatingIslandSample.effectiveWallWarpAmplitude(5.0, 0.0), 0.0);
        assertEquals(2.5, FloatingIslandSample.effectiveWallWarpAmplitude(5.0, 0.5), 0.0);
        assertEquals(5.0, FloatingIslandSample.effectiveWallWarpAmplitude(5.0, 1.0), 0.0);
    }

    @Test
    public void carveSolidInterior_preservesOuterShell() {
        boolean[] mask = {false, true, true, true, true, false};
        boolean[] lateralCarveMask = {true, true, true, true, true, true};
        CNG carve = constantCng(1.0);

        int count = FloatingIslandSample.carveSolidInterior(mask, lateralCarveMask, 100, 0, 0, carve, 0.5);

        assertEquals(2, count);
        assertEquals(false, mask[0]);
        assertEquals(true, mask[1]);
        assertEquals(false, mask[2]);
        assertEquals(false, mask[3]);
        assertEquals(true, mask[4]);
        assertEquals(false, mask[5]);
    }

    @Test
    public void carveSolidInterior_preservesLateralPerimeter() {
        boolean[] mask = {true, true, true, true, true, true};
        boolean[] lateralCarveMask = {false, false, false, false, false, false};

        int count = FloatingIslandSample.carveSolidInterior(mask, lateralCarveMask, 100, 0, 0, constantCng(1.0), 0.5);

        assertEquals(6, count);
        for (boolean solid : mask) {
            assertTrue(solid);
        }
    }

    @Test
    public void lateralCarving_requiresRoundedInteriorAndFullNeighborRing() {
        CNG fullRing = constantCng(1.0);
        FloatingIslandSample.NeighborSupport fullSupport = FloatingIslandSample.footprintNeighborSupport(fullRing, 0, 0, 0.0);
        CNG missingDiagonal = new CNG(new RNG(11)) {
            @Override
            public double noise(double x, double z) {
                return x == 1 && z == 1 ? 0.0 : 1.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };
        FloatingIslandSample.NeighborSupport partialSupport = FloatingIslandSample.footprintNeighborSupport(missingDiagonal, 0, 0, 0.0);

        assertTrue(FloatingIslandSample.canCarveLaterally(1.0, fullSupport));
        assertFalse(FloatingIslandSample.canCarveLaterally(0.99, fullSupport));
        assertFalse(FloatingIslandSample.canCarveLaterally(1.0, partialSupport));
    }

    @Test
    public void footprintPinholeFill_acceptsSingleColumnHoleWithSolidRing() {
        CNG footprint = new CNG(new RNG(4)) {
            @Override
            public double noise(double x, double z) {
                return x == 0 && z == 0 ? 0.46 : 1.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        FloatingIslandSample.NeighborSupport support = FloatingIslandSample.footprintNeighborSupport(footprint, 0, 0, 0.0);
        assertTrue(FloatingIslandSample.isFootprintPinholeRepairable(support));
    }

    @Test
    public void footprintPinholeFill_keepsBroadHoleOpen() {
        CNG footprint = new CNG(new RNG(5)) {
            @Override
            public double noise(double x, double z) {
                return Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 0.0 : 1.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        FloatingIslandSample.NeighborSupport support = FloatingIslandSample.footprintNeighborSupport(footprint, 0, 0, 0.0);
        assertFalse(FloatingIslandSample.isFootprintPinholeRepairable(support));
    }

    @Test
    public void layerSupport_rejectsIsolatedSolidProtrusion() {
        CNG footprint = new CNG(new RNG(6)) {
            @Override
            public double noise(double x, double z) {
                return x == 0 && z == 0 ? 1.0 : 0.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        assertTrue(FloatingIslandSample.layerFootprintSolid(footprint, null, false, 0.0, 0, 64, 0, 0.0));
        assertFalse(FloatingIslandSample.layerNeighborSupport(footprint, null, false, 0.0, 0, 64, 0, 0.0).hasSolidSupport());
    }

    @Test
    public void layerSupport_fillsSingleColumnAirGap() {
        CNG footprint = new CNG(new RNG(7)) {
            @Override
            public double noise(double x, double z) {
                return x == 0 && z == 0 ? 0.0 : 1.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        assertFalse(FloatingIslandSample.layerFootprintSolid(footprint, null, false, 0.0, 0, 64, 0, 0.0));
        assertTrue(FloatingIslandSample.layerNeighborSupport(footprint, null, false, 0.0, 0, 64, 0, 0.0).canFillPinhole());
    }

    @Test
    public void carveSolidInterior_rejectsIsolatedCarveAir() {
        boolean[] mask = {true, true, true, true, true};
        CNG carve = new CNG(new RNG(8), new NoiseGenerator() {
            @Override
            public double noise(double x) {
                return 0.0;
            }

            @Override
            public double noise(double x, double z) {
                return x == 0 && z == 0 ? 1.0 : 0.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        }, 1.0, 1);

        boolean[] lateralCarveMask = {true, true, true, true, true};
        int count = FloatingIslandSample.carveSolidInterior(mask, lateralCarveMask, 100, 0, 0, carve, 0.5);

        assertEquals(5, count);
        assertEquals(true, mask[1]);
        assertEquals(true, mask[2]);
        assertEquals(true, mask[3]);
    }

    @Test
    public void carveSolidInterior_capsBroadVerticalCarveSheet() {
        boolean[] mask = new boolean[30];
        boolean[] lateralCarveMask = new boolean[30];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = true;
            lateralCarveMask[i] = true;
        }
        CNG carve = constantCng(1.0);

        int count = FloatingIslandSample.carveSolidInterior(mask, lateralCarveMask, 100, 0, 0, carve, 0.5);

        assertEquals(25, count);
        for (int i = 0; i < FloatingIslandSample.carveShellThickness(mask.length); i++) {
            assertTrue(mask[i]);
            assertTrue(mask[mask.length - 1 - i]);
        }
        int longestAirRun = 0;
        int currentAirRun = 0;
        for (boolean solid : mask) {
            if (solid) {
                currentAirRun = 0;
                continue;
            }
            currentAirRun++;
            longestAirRun = Math.max(longestAirRun, currentAirRun);
        }
        assertEquals(FloatingIslandSample.maxVerticalCarveRun(mask.length), longestAirRun);
    }

    @Test
    public void clampVerticalCarveRuns_keepsMiddleOfOversizedRunOnly() {
        boolean[] carveMask = new boolean[12];
        for (int i = 1; i <= 10; i++) {
            carveMask[i] = true;
        }

        FloatingIslandSample.clampVerticalCarveRuns(carveMask, 1, 10, 4);

        assertEquals(false, carveMask[2]);
        assertEquals(true, carveMask[4]);
        assertEquals(true, carveMask[7]);
        assertEquals(false, carveMask[9]);
    }

    @Test
    public void directCarveEnabled_respectsCarvingReferenceOverride() {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setCarving("carving/mushroom");
        CNG carve = new CNG(new RNG(9));

        assertFalse(FloatingIslandSample.directCarveEnabled(entry, null, carve, 0.5));
    }

    @Test
    public void hasFootprintNeighborSupport_rejectsSingleIsolatedColumn() {
        CNG footprint = new CNG(new RNG(2)) {
            @Override
            public double noise(double x, double z) {
                return x == 0 && z == 0 ? 1.0 : 0.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        assertEquals(false, FloatingIslandSample.hasFootprintNeighborSupport(footprint, 0, 0, 0.0));
    }

    @Test
    public void hasFootprintNeighborSupport_acceptsCardinalNeighbor() {
        CNG footprint = new CNG(new RNG(3)) {
            @Override
            public double noise(double x, double z) {
                return (x == 0 && z == 0) || (x == 1 && z == 0) ? 1.0 : 0.0;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, z);
            }
        };

        assertEquals(true, FloatingIslandSample.hasFootprintNeighborSupport(footprint, 0, 0, 0.0));
    }

    private CNG constantCng(double value) {
        return new CNG(new RNG(12), new NoiseGenerator() {
            @Override
            public double noise(double x) {
                return value;
            }

            @Override
            public double noise(double x, double z) {
                return value;
            }

            @Override
            public double noise(double x, double y, double z) {
                return value;
            }
        }, 1.0, 1);
    }
}
