package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisCaveFieldModule;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.engine.object.IrisStyledRange;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCaveCarver3DNearParityTest {
    private static final long CARVE_SEED = -54_777_863_784_492_918L;
    private static final int SURFACE_CEILING_FADE_DEPTH = 12;
    private static final double SURFACE_CEILING_SOLID_EPSILON = 0.000001D;

    private static Method sampleDensityMethod;
    private static Method aquiferCandidateMethod;
    private static Method aquiferCupSupportMethod;
    private static Method deepAdaptivePlaneMethod;
    private static Field baseDensityField;
    private static Field detailDensityField;
    private static Field modulesField;
    private static Field moduleDensityField;
    private static Field engineField;
    private static Field dataField;
    private static Field profileField;
    private static Field surfaceBreakDensityField;
    private static Field thresholdRngField;
    private static Field carveAirField;
    private static Field carveFluidField;
    private static Field carveLavaField;
    private static Field carveForcedAirField;

    @BeforeClass
    public static void setupReflection() throws Exception {
        sampleDensityMethod = IrisCaveCarver3D.class.getDeclaredMethod("sampleDensityOptimized", int.class, int.class, int.class);
        sampleDensityMethod.setAccessible(true);
        aquiferCandidateMethod = IrisCaveCarver3D.class.getDeclaredMethod("isAquiferCandidate", int.class, int.class, int.class, double.class);
        aquiferCandidateMethod.setAccessible(true);
        aquiferCupSupportMethod = IrisCaveCarver3D.class.getDeclaredMethod(
                "hasAquiferCupSupport", CaveCarveScratch.class, int.class, int.class, int.class, double.class);
        aquiferCupSupportMethod.setAccessible(true);
        deepAdaptivePlaneMethod = IrisCaveCarver3D.class.getDeclaredMethod(
                "classifyDeepAdaptivePlaneFromSamples",
                CaveCarveScratch.class,
                int[].class,
                double[].class,
                int.class,
                boolean[].class,
                int.class,
                double[].class,
                int.class,
                int.class
        );
        deepAdaptivePlaneMethod.setAccessible(true);
        baseDensityField = IrisCaveCarver3D.class.getDeclaredField("baseDensity");
        baseDensityField.setAccessible(true);
        detailDensityField = IrisCaveCarver3D.class.getDeclaredField("detailDensity");
        detailDensityField.setAccessible(true);
        modulesField = IrisCaveCarver3D.class.getDeclaredField("modules");
        modulesField.setAccessible(true);
        moduleDensityField = CaveFieldModuleState.class.getDeclaredField("density");
        moduleDensityField.setAccessible(true);
        engineField = IrisCaveCarver3D.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        dataField = IrisCaveCarver3D.class.getDeclaredField("data");
        dataField.setAccessible(true);
        profileField = IrisCaveCarver3D.class.getDeclaredField("profile");
        profileField.setAccessible(true);
        surfaceBreakDensityField = IrisCaveCarver3D.class.getDeclaredField("surfaceBreakDensity");
        surfaceBreakDensityField.setAccessible(true);
        thresholdRngField = IrisCaveCarver3D.class.getDeclaredField("thresholdRng");
        thresholdRngField.setAccessible(true);
        carveAirField = IrisCaveCarver3D.class.getDeclaredField("carveAir");
        carveAirField.setAccessible(true);
        carveFluidField = IrisCaveCarver3D.class.getDeclaredField("carveFluid");
        carveFluidField.setAccessible(true);
        carveLavaField = IrisCaveCarver3D.class.getDeclaredField("carveLava");
        carveLavaField.setAccessible(true);
        carveForcedAirField = IrisCaveCarver3D.class.getDeclaredField("carveForcedAir");
        carveForcedAirField.setAccessible(true);
    }

    @Test
    public void carvingReleasesSectionReferencesAfterSuccessAndFailure() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            Engine engine = createEngine(128, 110);
            IrisCaveProfile profile = createProfile(false, false).setAdaptiveSampling(false)
                    .setAllowFluid(false).setDetailWeight(0D)
                    .setDensityThreshold(new IrisStyledRange(2D, 2D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
            IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
            WriterCapture capture = createWriterCapture(128);
            if (fail) {
                AtomicInteger samples = new AtomicInteger();
                CNG density = mock(CNG.class);
                doAnswer(invocation -> {
                    if (samples.incrementAndGet() > 300) {
                        throw new IllegalStateException("Density sample failed");
                    }
                    return -1D;
                }).when(density).noiseFastSigned3D(anyDouble(), anyDouble(), anyDouble());
                baseDensityField.set(carver, density);
                assertThrows(IllegalStateException.class, () -> carver.carve(capture.writer, 0, 0,
                        fullWeights(), 0D, 0D, null, filledHeights(110)));
            } else {
                carver.carve(capture.writer, 0, 0, fullWeights(), 0D, 0D, null, filledHeights(110));
            }
            assertFalse(capture.carvedCells.isEmpty());
            Field scratchField = IrisCaveCarver3D.class.getDeclaredField("scratchCache");
            scratchField.setAccessible(true);
            ThreadLocal<?> scratchCache = (ThreadLocal<?>) scratchField.get(carver);
            CaveCarveScratch scratch = (CaveCarveScratch) scratchCache.get();
            assertTrue(scratch.sectionMatter.length > 0);
            for (Matter matter : scratch.sectionMatter) {
                assertNull(matter);
            }
            for (MatterSlice<?> slice : scratch.sectionSlices) {
                assertNull(slice);
            }
        }
    }

    @Test
    public void parallelDensityPreservesExactAndAdaptiveCavernsAndLiquids() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            for (boolean adaptive : new boolean[]{false, true}) {
                for (boolean warp : new boolean[]{false, true}) {
                    for (boolean modules : new boolean[]{false, true}) {
                        Engine engine = createEngine(128, 110);
                        IrisCaveProfile profile = createProfile(warp, modules).setAdaptiveSampling(adaptive)
                                .setDensityThreshold(new IrisStyledRange(0.08D, 0.08D, new IrisGeneratorStyle(NoiseStyle.FLAT)))
                                .setFluidRequiresFloor(true);
                        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
                        WriterCapture serial = createWriterCapture(128);
                        WriterCapture parallel = createWriterCapture(128);
                        int[] heights = filledHeights(110);
                        double[] weights = fullWeights();
                        long[] boundaries = new long[256];
                        Arrays.fill(boundaries, SurfaceFluidBoundaryPlan.boundary(
                                SurfaceFluidBoundaryPlan.NO_BOUNDARY, Integer.MIN_VALUE));
                        for (int column = 0; column < 256; column += 11) {
                            heights[column] = 80;
                            weights[column] = column % 3 == 0 ? 0D : 0.6D;
                            boundaries[column] = SurfaceFluidBoundaryPlan.boundary(56, 64);
                        }
                        int expected = carver.carve(serial.writer, -17, 19, weights, 0.1D, 0.15D,
                                null, heights, boundaries, null, new CaveFluidSupportPlan());
                        int actual = pool.submit(() -> carver.carve(parallel.writer, -17, 19, weights, 0.1D, 0.15D,
                                null, heights, boundaries, null, new CaveFluidSupportPlan())).get(20, TimeUnit.SECONDS);
                        assertTrue(expected > 0);
                        assertEquals(expected, actual);
                        assertEquals(serial.carvedCells, parallel.carvedCells);
                        assertEquals(serial.carvedLiquids, parallel.carvedLiquids);
                    }
                }
            }
        }
    }

    @Test
    public void rejectedParallelVoxelsDoNotSampleAquifers() throws Exception {
        Engine engine = createEngine(128, 110);
        IrisCaveProfile profile = createProfile(false, false).setAdaptiveSampling(false)
                .setBaseDensityStyle(new IrisGeneratorStyle(NoiseStyle.FLAT))
                .setDetailWeight(0D)
                .setDensityThreshold(new IrisStyledRange(-2D, -2D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
        CNG detail = mock(CNG.class);
        doAnswer(invocation -> {
            throw new AssertionError("Rejected voxel sampled an aquifer");
        }).when(detail).noiseFastSigned3D(anyDouble(), anyDouble(), anyDouble());
        Field detailField = IrisCaveCarver3D.class.getDeclaredField("detailDensity");
        detailField.setAccessible(true);
        detailField.set(carver, detail);
        WriterCapture capture = createWriterCapture(128);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertEquals(0, pool.submit(() -> carver.carve(capture.writer, 0, 0, fullWeights(),
                    0D, 0D, null, filledHeights(110))).get(20, TimeUnit.SECONDS).intValue());
        }
        assertTrue(capture.carvedCells.isEmpty());
    }

    @Test
    public void parallelMaterialDecisionsPreserveLavaFluidAndForcedAir() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            for (boolean lava : new boolean[]{false, true}) {
                Engine engine = createEngine(128, 110);
                IrisCaveProfile profile = createProfile(true, true).setAdaptiveSampling(true)
                        .setAllowLava(lava).setFluidRequiresFloor(false)
                        .setDensityThreshold(new IrisStyledRange(2D, 2D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
                IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
                WriterCapture serial = createWriterCapture(128);
                WriterCapture parallel = createWriterCapture(128);
                int[] heights = filledHeights(110);
                int expected = carver.carve(serial.writer, 7, -3, fullWeights(), 0D, 0D, null, heights);
                int actual = pool.submit(() -> carver.carve(parallel.writer, 7, -3, fullWeights(), 0D, 0D,
                        null, heights)).get(20, TimeUnit.SECONDS);
                assertEquals(expected, actual);
                assertEquals(serial.carvedCells, parallel.carvedCells);
                assertEquals(serial.carvedLiquids, parallel.carvedLiquids);
                assertTrue(countLiquid(serial, (byte) 0) > 0);
                assertTrue(countLiquid(serial, (byte) 1) > 0);
                assertTrue(countLiquid(serial, lava ? (byte) 2 : (byte) 3) > 0);
            }
        }
    }

    @Test
    public void parallelLatticePreservesCavernsAndResolvedFluidSupport() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            for (int step : new int[]{3, 5}) {
                Engine engine = createEngine(384, 360);
                IrisCaveProfile profile = createProfile(true, true).setSampleStep(step)
                        .setVerticalRange(new IrisRange(6, 350))
                        .setDensityThreshold(new IrisStyledRange(0.08D, 0.08D, new IrisGeneratorStyle(NoiseStyle.FLAT)))
                        .setFluidRequiresFloor(true);
                IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
                WriterCapture serial = createWriterCapture(384);
                WriterCapture parallel = createWriterCapture(384);
                int[] heights = filledHeights(340);
                double[] weights = fullWeights();
                for (int column = 0; column < 256; column += 7) {
                    heights[column] = 80;
                    weights[column] = column % 3 == 0 ? 0D : 0.6D;
                }
                int expected = carver.carve(serial.writer, 9, -7, weights, 0.1D, 0.15D, null, heights);
                int actual = pool.submit(() -> carver.carve(parallel.writer, 9, -7, weights, 0.1D, 0.15D,
                        null, heights)).get(20, TimeUnit.SECONDS);
                assertTrue(expected > 0);
                assertEquals(expected, actual);
                assertEquals(serial.carvedCells, parallel.carvedCells);
                assertEquals(serial.carvedLiquids, parallel.carvedLiquids);
            }
        }
    }

    @Test
    public void expressionsInDensityFracturesDisableParallelClassification() throws Exception {
        Method fixedStyles = IrisCaveCarver3D.class.getDeclaredMethod("fixedDensityStyles", IrisCaveProfile.class);
        fixedStyles.setAccessible(true);
        IrisCaveProfile profile = createProfile(true, true);
        assertEquals(true, fixedStyles.invoke(null, profile));
        profile.getWarpStyle().setFracture(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setExpression("live-height"));
        assertEquals(false, fixedStyles.invoke(null, profile));
        profile = createProfile(true, true);
        profile.getModules().get(0).getStyle().setExpression("live-height");
        assertEquals(false, fixedStyles.invoke(null, profile));
    }

    @Test
    public void nestedCarvingPreservesOuterScratch() {
        Engine engine = createEngine(128, 110);
        doReturn(110).when(engine).getHeight(anyInt(), anyInt(), anyBoolean());
        IrisCaveProfile profile = createProfile(true, true).setAdaptiveSampling(false);
        WriterCapture expected = createWriterCapture(128);
        new IrisCaveCarver3D(engine, profile).carve(expected.writer, 2, 3);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
        WriterCapture nested = createWriterCapture(128);
        AtomicBoolean entered = new AtomicBoolean();
        doAnswer(invocation -> {
            if (entered.compareAndSet(false, true)) {
                carver.carve(nested.writer, -9, -11, fullWeights(), 0D, 0D, null, filledHeights(80));
            }
            return 110;
        }).when(engine).getHeight(anyInt(), anyInt(), anyBoolean());
        WriterCapture actual = createWriterCapture(128);
        carver.carve(actual.writer, 2, 3);
        assertTrue(entered.get());
        assertFalse(nested.carvedCells.isEmpty());
        assertEquals(expected.carvedCells, actual.carvedCells);
        assertEquals(expected.carvedLiquids, actual.carvedLiquids);
    }

    @Test
    public void genericFluidContractKeepsOverworldDefaults() {
        IrisCaveProfile profile = new IrisCaveProfile();
        IrisDimension dimension = new IrisDimension();

        assertTrue(profile.isAllowFluid());
        assertEquals(12, profile.getFluidMinDepthBelowSurface());
        assertTrue(profile.isFluidRequiresFloor());
        assertEquals("water", dimension.getFluidPalette().getPalette().get(0).getBlock());
    }

    @Test
    public void carvedCellDistributionStableAcrossEquivalentCarvers() {
        Engine engine = createEngine(128, 92);

        IrisCaveCarver3D firstCarver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture firstCapture = createWriterCapture(128);
        int firstCarved = firstCarver.carve(firstCapture.writer, 7, -3);

        IrisCaveCarver3D secondCarver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture secondCapture = createWriterCapture(128);
        int secondCarved = secondCarver.carve(secondCapture.writer, 7, -3);

        assertTrue(firstCarved > 0);
        assertEquals(firstCarved, secondCarved);
        assertEquals(firstCapture.carvedCells, secondCapture.carvedCells);
        assertEquals(firstCapture.carvedLiquids, secondCapture.carvedLiquids);
    }

    @Test
    public void warpCacheDoesNotCrossContaminateCarverInstancesOnSameThread() throws Exception {
        Engine engine = createEngine(128, 92);
        IrisCaveProfile firstProfile = createProfile(true, false);
        firstProfile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.1875D));
        IrisCaveProfile secondProfile = createProfile(true, false);
        secondProfile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.CELLULAR).zoomed(0.1875D));
        IrisCaveProfile controlProfile = createProfile(true, false);
        controlProfile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.CELLULAR).zoomed(0.1875D));
        IrisCaveCarver3D firstCarver = new IrisCaveCarver3D(engine, firstProfile);
        IrisCaveCarver3D secondCarver = new IrisCaveCarver3D(engine, secondProfile);
        IrisCaveCarver3D controlCarver = new IrisCaveCarver3D(engine, controlProfile);
        int x = 48;
        int y = 64;
        int z = -352;

        double firstDensity = sampleDensity(firstCarver, x, y, z);
        double secondDensity = sampleDensity(secondCarver, x, y, z);
        AtomicReference<Double> controlDensity = new AtomicReference<>();
        AtomicReference<Throwable> controlFailure = new AtomicReference<>();
        Thread controlThread = new Thread(() -> {
            try {
                controlDensity.set(sampleDensity(controlCarver, x, y, z));
            } catch (Throwable throwable) {
                controlFailure.set(throwable);
            }
        }, "Iris cave warp cache control");
        controlThread.start();
        controlThread.join();

        if (controlFailure.get() != null) {
            throw new AssertionError(controlFailure.get());
        }
        assertNotEquals(firstDensity, controlDensity.get(), 0D);
        assertEquals(controlDensity.get(), secondDensity, 0D);
    }

    @Test
    public void exactPathCarvesChunkEdgesAndRespectsWorldHeightClipping() {
        Engine engine = createEngine(48, 46);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture capture = createWriterCapture(48);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(46);

        int carved = carver.carve(capture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        assertTrue(carved > 0);
        assertTrue(hasX(capture.carvedCells, 14));
        assertTrue(hasX(capture.carvedCells, 15));
        assertTrue(hasZ(capture.carvedCells, 14));
        assertTrue(hasZ(capture.carvedCells, 15));
        assertTrue(maxY(capture.carvedCells) <= 47);
        assertTrue(minY(capture.carvedCells) >= 0);
    }

    @Test
    public void flatSurfaceDoesNotHardClipLargeCaveCeiling() {
        assertFlatSurfaceCeiling(createFluidProfile()
                .setVerticalRange(new IrisRange(20D, 72D))
                .setAllowSurfaceBreak(false)
                .setSurfaceClearance(5)
                .setAllowFluid(false)
                .setAllowLava(false)
                .setAdaptiveSampling(false)
                .setSampleStep(1));
        assertFlatSurfaceCeiling(createFluidProfile()
                .setVerticalRange(new IrisRange(20D, 72D))
                .setAllowSurfaceBreak(false)
                .setSurfaceClearance(5)
                .setAllowFluid(false)
                .setAllowLava(false)
                .setAdaptiveSampling(true)
                .setSampleStep(1));
    }

    @Test
    public void exactPathMatchesNaiveReferenceWithoutWarpOrModules() throws Exception {
        assertExactParity(false, false, false);
    }

    @Test
    public void exactPathMatchesNaiveReferenceWithWarpAndModules() throws Exception {
        assertExactParity(true, true, false);
    }

    @Test
    public void exactPathMatchesNaiveReferenceWhenProfileCeilingExceedsTerrain() throws Exception {
        Engine engine = createEngine(256, 64);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(64);
        IrisCaveProfile optimizedProfile = createProfile(true, true)
                .setVerticalRange(new IrisRange(0D, 220D))
                .setAllowSurfaceBreak(false)
                .setAdaptiveSampling(false);
        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, optimizedProfile);
        WriterCapture optimizedCapture = createWriterCapture(256);
        int optimizedCarved = optimizedCarver.carve(
                optimizedCapture.writer, 5, -1, columnWeights, 0D, 0D, null, precomputedSurfaceHeights);

        IrisCaveProfile referenceProfile = createProfile(true, true)
                .setVerticalRange(new IrisRange(0D, 220D))
                .setAllowSurfaceBreak(false)
                .setAdaptiveSampling(false);
        IrisCaveCarver3D referenceCarver = new IrisCaveCarver3D(engine, referenceProfile);
        WriterCapture referenceCapture = createWriterCapture(256);
        int referenceCarved = carveNaiveExact(
                referenceCarver, referenceCapture.writer, 5, -1, columnWeights, null, precomputedSurfaceHeights);

        assertEquals(referenceCarved, optimizedCarved);
        assertEquals(referenceCapture.carvedCells, optimizedCapture.carvedCells);
        assertEquals(referenceCapture.carvedLiquids, optimizedCapture.carvedLiquids);
    }

    @Test
    public void deepAdaptiveSampleClassifierMatchesLegacyDecisionTree() throws Exception {
        IrisCaveProfile profile = createProfile(true, true);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(createEngine(128, 92), profile);
        CaveCarveScratch scratch = new CaveCarveScratch();
        int adaptiveSampleStep = 8;
        int axisCells = 2;
        int axisSamples = 3;
        int[] planeColumnIndices = new int[256];
        double[] planeThresholdLimit = new double[256];
        boolean[] planeCarve = new boolean[256];
        double[] adaptivePlaneDensity = scratch.adaptivePlaneDensity;
        for (int index = 0; index < planeColumnIndices.length; index++) {
            planeColumnIndices[index] = index;
            planeThresholdLimit[index] = ((index % 17) - 8) * 0.11D;
            planeCarve[index] = (index & 1) == 0;
        }
        for (int index = 0; index < axisSamples * axisSamples; index++) {
            adaptivePlaneDensity[index] = ((index * 13) % 19 - 9) * 0.14D;
        }

        deepAdaptivePlaneMethod.invoke(
                carver,
                scratch,
                planeColumnIndices,
                planeThresholdLimit,
                planeColumnIndices.length,
                planeCarve,
                adaptiveSampleStep,
                adaptivePlaneDensity,
                axisCells,
                axisSamples
        );

        double normalization = Math.abs(profile.getBaseWeight()) + Math.abs(profile.getDetailWeight());
        for (IrisCaveFieldModule module : profile.getModules()) {
            normalization += Math.abs(module.getWeight());
        }
        double inverseNormalization = 1D / normalization;
        double adaptiveThresholdMargin = 0.19D;
        for (int columnIndex = 0; columnIndex < planeColumnIndices.length; columnIndex++) {
            int localX = columnIndex >> 4;
            int localZ = columnIndex & 15;
            int cellX = Math.min(localX / adaptiveSampleStep, axisCells - 1);
            int cellZ = Math.min(localZ / adaptiveSampleStep, axisCells - 1);
            int x0 = cellX * adaptiveSampleStep;
            int z0 = cellZ * adaptiveSampleStep;
            int x1 = Math.min(x0 + adaptiveSampleStep, 16);
            int z1 = Math.min(z0 + adaptiveSampleStep, 16);
            double tx = x1 == x0 ? 0D : (localX - x0) / (double) (x1 - x0);
            double tz = z1 == z0 ? 0D : (localZ - z0) / (double) (z1 - z0);
            int row0 = cellX * axisSamples;
            int row1 = (cellX + 1) * axisSamples;
            double d00 = adaptivePlaneDensity[row0 + cellZ];
            double d01 = adaptivePlaneDensity[row0 + cellZ + 1];
            double d10 = adaptivePlaneDensity[row1 + cellZ];
            double d11 = adaptivePlaneDensity[row1 + cellZ + 1];
            double dx0 = d00 + ((d10 - d00) * tx);
            double dx1 = d01 + ((d11 - d01) * tx);
            double predictedDensity = dx0 + ((dx1 - dx0) * tz);
            double threshold = planeThresholdLimit[columnIndex] * inverseNormalization;
            double minDensity = Math.min(Math.min(d00, d01), Math.min(d10, d11));
            double maxDensity = Math.max(Math.max(d00, d01), Math.max(d10, d11));
            double ambiguityMargin = adaptiveThresholdMargin + ((maxDensity - minDensity) * 0.125D);
            boolean expected;
            if (localX % adaptiveSampleStep == 0 && localZ % adaptiveSampleStep == 0) {
                expected = predictedDensity <= threshold;
            } else if (predictedDensity <= threshold - ambiguityMargin) {
                expected = true;
            } else if (predictedDensity > threshold + ambiguityMargin) {
                expected = false;
            } else {
                expected = predictedDensity <= threshold;
            }
            assertEquals("column " + columnIndex, expected, planeCarve[columnIndex]);
        }
    }

    @Test
    public void aquiferCupSupportStopsAsSoonAsTheResultIsKnown() throws Exception {
        Engine engine = createEngine(128, 92);
        IrisCaveProfile profile = createProfile(false, false)
                .setBaseWeight(1D)
                .setDetailWeight(0D)
                .setFluidRequiresFloor(true);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
        Map<String, Double> rejectedDensity = new HashMap<>();
        rejectedDensity.put("11:10:10", -1D);
        rejectedDensity.put("9:10:10", -1D);
        CoordinateDensityCNG rejectedNoise = new CoordinateDensityCNG(rejectedDensity);
        baseDensityField.set(carver, rejectedNoise);

        boolean rejected = (boolean) aquiferCupSupportMethod.invoke(
                carver, new CaveCarveScratch(), 10, 10, 10, 0D);

        assertFalse(rejected);
        assertEquals(List.of("10:9:10", "10:8:10", "11:10:10", "9:10:10"), rejectedNoise.sampledCoordinates);

        CoordinateDensityCNG acceptedNoise = new CoordinateDensityCNG(Map.of());
        baseDensityField.set(carver, acceptedNoise);

        boolean accepted = (boolean) aquiferCupSupportMethod.invoke(
                carver, new CaveCarveScratch(), 10, 10, 10, 0D);

        assertTrue(accepted);
        assertEquals(
                List.of("10:9:10", "10:8:10", "11:10:10", "9:10:10", "10:10:11", "10:10:9"),
                acceptedNoise.sampledCoordinates
        );
    }

    @Test
    public void legacySampleStepTwoMatchesExactReference() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveProfile exactProfile = createProfile(true, true).setSampleStep(1).setAdaptiveSampling(false);
        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, exactProfile);
        WriterCapture exactCapture = createWriterCapture(96);
        int exactCarved = exactCarver.carve(exactCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveProfile legacyProfile = createProfile(true, true).setSampleStep(2).setAdaptiveSampling(false);
        IrisCaveCarver3D legacyCarver = new IrisCaveCarver3D(engine, legacyProfile);
        WriterCapture legacyCapture = createWriterCapture(96);
        int legacyCarved = legacyCarver.carve(legacyCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        assertEquals(exactCarved, legacyCarved);
        assertEquals(exactCapture.carvedCells, legacyCapture.carvedCells);
        assertEquals(exactCapture.carvedLiquids, legacyCapture.carvedLiquids);
    }

    @Test
    public void exactPathUsesExpectedLavaAndForcedAirBands() {
        Engine engine = createEngine(48, 46);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(46);

        IrisCaveProfile lavaProfile = createProfile(false, false).setAllowLava(true).setAllowFluid(false);
        IrisCaveCarver3D lavaCarver = new IrisCaveCarver3D(engine, lavaProfile);
        WriterCapture lavaCapture = createWriterCapture(48);
        lavaCarver.carve(lavaCapture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        IrisCaveProfile forcedAirProfile = createProfile(false, false).setAllowLava(false).setAllowFluid(false);
        IrisCaveCarver3D forcedAirCarver = new IrisCaveCarver3D(engine, forcedAirProfile);
        WriterCapture forcedAirCapture = createWriterCapture(48);
        forcedAirCarver.carve(forcedAirCapture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        assertTrue(containsLiquidInRange(lavaCapture.carvedLiquids, 0, 18, (byte) 2));
        assertTrue(containsLiquidInRange(forcedAirCapture.carvedLiquids, 0, 18, (byte) 3));
        assertTrue(containsLiquidInRange(lavaCapture.carvedLiquids, 19, 47, (byte) 0));
        assertTrue(containsLiquidInRange(forcedAirCapture.carvedLiquids, 19, 47, (byte) 0));
    }

    @Test
    public void fluidPrecedesForcedAirWhenLavaIsDisabled() {
        Engine engine = createEngine(48, 46);
        int[] surfaceHeights = filledHeights(46);
        IrisCaveProfile wetProfile = createFluidProfile()
                .setVerticalRange(new IrisRange(2D, 18D))
                .setAllowLava(false)
                .setFluidRequiresFloor(false);
        WriterCapture wetCapture = createWriterCapture(48);
        new IrisCaveCarver3D(engine, wetProfile).carve(
                wetCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        IrisCaveProfile dryProfile = createFluidProfile()
                .setVerticalRange(new IrisRange(2D, 18D))
                .setAllowFluid(false)
                .setAllowLava(false)
                .setFluidRequiresFloor(false);
        WriterCapture dryCapture = createWriterCapture(48);
        new IrisCaveCarver3D(engine, dryProfile).carve(
                dryCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        assertEquals(wetCapture.carvedCells, dryCapture.carvedCells);
        assertTrue(containsLiquidInRange(wetCapture.carvedLiquids, 2, 18, (byte) 1));
        assertTrue(containsLiquidInRange(wetCapture.carvedLiquids, 2, 18, (byte) 3));
        assertEquals(0, countLiquid(dryCapture, (byte) 1));
        assertTrue(containsLiquidInRange(dryCapture.carvedLiquids, 2, 18, (byte) 3));
    }

    @Test
    public void fluidToggleAndMinimumDepthUseEachColumnsTerrainSurface() {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = splitSurfaceHeights(40, 70);

        IrisCaveProfile enabledProfile = createFluidProfile().setAllowFluid(true).setFluidMinDepthBelowSurface(10);
        WriterCapture enabledCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, enabledProfile).carve(
                enabledCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        IrisCaveProfile disabledProfile = createFluidProfile().setAllowFluid(false).setFluidMinDepthBelowSurface(10);
        WriterCapture disabledCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, disabledProfile).carve(
                disabledCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        assertEquals(enabledCapture.carvedCells, disabledCapture.carvedCells);
        assertTrue(countLiquid(enabledCapture, (byte) 1) > 0);
        assertEquals(0, countLiquid(disabledCapture, (byte) 1));
        assertFluidRespectsSplitCutoff(enabledCapture, 30, 60);
        assertTrue(enabledCapture.carvedCells.contains(cellKey(0, 40, 0)));
        assertTrue(enabledCapture.carvedCells.contains(cellKey(15, 70, 0)));
    }

    @Test
    public void dimensionFluidHeightCapsFluidIntent() {
        Engine engine = createEngine(80, 70);
        IrisCaveProfile profile = createFluidProfile().setFluidMinDepthBelowSurface(0);
        WriterCapture capture = createWriterCapture(80);

        new IrisCaveCarver3D(engine, profile).carve(
                capture.writer, 0, 0, fullWeights(), 0D, 0D, null, filledHeights(70));

        assertTrue(countLiquid(capture, (byte) 1) > 0);
        assertLiquidAtOrBelow(capture, (byte) 1, 64);
        assertTrue(containsLiquidInRange(capture.carvedLiquids, 65, 70, (byte) 0));
    }

    @Test
    public void surfaceFluidBoundaryProtectsReservoirWithoutFloodingDeeperCaves() {
        assertSurfaceFluidBoundaryForProfile(createFluidProfile().setAdaptiveSampling(false).setSampleStep(1));
        assertSurfaceFluidBoundaryForProfile(createFluidProfile().setAdaptiveSampling(true).setSampleStep(1));
        assertSurfaceFluidBoundaryForProfile(createFluidProfile().setAdaptiveSampling(false).setSampleStep(4));
    }

    @Test
    public void floorRequiredFluidResolvesAfterTheCompleteCarveMask() {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = filledHeights(70);
        int chunkX = -8;
        int chunkZ = -1;

        IrisStyledRange cupThreshold = new IrisStyledRange(0.15D, 0.15D, new IrisGeneratorStyle(NoiseStyle.FLAT));
        IrisCaveProfile supportedProfile = createFluidProfile()
                .setDensityThreshold(cupThreshold)
                .setFluidMinDepthBelowSurface(0)
                .setFluidRequiresFloor(true);
        WriterCapture firstCapture = createWriterCapture(80);
        CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();
        new IrisCaveCarver3D(engine, supportedProfile).carve(
                firstCapture.writer, chunkX, chunkZ, fullWeights(), 0D, 0D, null, surfaceHeights, null, null, supportPlan);
        int candidateCount = countLiquid(firstCapture, (byte) 1);
        supportPlan.resolve(firstCapture.writer.acquireChunk(chunkX, chunkZ));

        IrisCaveProfile repeatedProfile = createFluidProfile()
                .setDensityThreshold(cupThreshold)
                .setFluidMinDepthBelowSurface(0)
                .setFluidRequiresFloor(true);
        WriterCapture secondCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, repeatedProfile).carve(
                secondCapture.writer, chunkX, chunkZ, fullWeights(), 0D, 0D, null, surfaceHeights);

        IrisCaveProfile unrestrictedProfile = createFluidProfile()
                .setDensityThreshold(cupThreshold)
                .setFluidMinDepthBelowSurface(0)
                .setFluidRequiresFloor(false);
        WriterCapture unrestrictedCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, unrestrictedProfile).carve(
                unrestrictedCapture.writer, chunkX, chunkZ, fullWeights(), 0D, 0D, null, surfaceHeights);

        assertTrue(candidateCount > 0);
        assertEquals(firstCapture.carvedLiquids, secondCapture.carvedLiquids);
        assertTrue(countLiquid(firstCapture, (byte) 1) > 0);
        assertTrue(countLiquid(firstCapture, (byte) 1) < countLiquid(unrestrictedCapture, (byte) 1));
        assertFluidCellsHaveSolidSupport(firstCapture);
    }

    @Test
    public void finalFluidSupportRejectsUnknownNeighborChunkEdges() {
        WriterCapture capture = createWriterCapture(80);
        MantleChunk<Matter> chunk = capture.writer.acquireChunk(0, 0);
        MatterSlice<MatterCavern> slice = chunk.getOrCreate(3).slice(MatterCavern.class);
        MatterCavern fluid = new MatterCavern(true, "", (byte) 1);
        MatterCavern air = new MatterCavern(true, "", (byte) 0);
        int y = 56;
        int z = 8;
        slice.set(0, y & 15, z, fluid);
        slice.set(8, y & 15, z, fluid);
        CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();
        supportPlan.add(0, y, z, fluid, air);
        supportPlan.add(8, y, z, fluid, air);

        supportPlan.resolve(chunk);

        assertEquals(Byte.valueOf((byte) 0), capture.carvedLiquids.get(cellKey(0, y, z)));
        assertEquals(Byte.valueOf((byte) 1), capture.carvedLiquids.get(cellKey(8, y, z)));
    }

    @Test
    public void floorRequiredFluidSeesLaterProfileCarvePasses() {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = filledHeights(70);
        WriterCapture capture = createWriterCapture(80);
        CaveFluidSupportPlan fluidSupportPlan = new CaveFluidSupportPlan();
        IrisCaveProfile fluidProfile = createFluidProfile()
                .setDensityThreshold(new IrisStyledRange(0.15D, 0.15D, new IrisGeneratorStyle(NoiseStyle.FLAT)))
                .setFluidRequiresFloor(true);
        IrisCaveProfile airProfile = createFluidProfile().setAllowFluid(false);

        new IrisCaveCarver3D(engine, fluidProfile).carve(
                capture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights,
                null, new IrisRange(20D, 64D), fluidSupportPlan);
        String fluidCell = firstCellWithLiquid(capture, (byte) 1);
        assertTrue(fluidCell != null);
        int fluidY = coordinate(fluidCell, 1);
        new IrisCaveCarver3D(engine, airProfile).carve(
                capture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights,
                null, new IrisRange(fluidY - 1D, fluidY - 1D), fluidSupportPlan);

        assertEquals(Byte.valueOf((byte) 1), capture.carvedLiquids.get(fluidCell));
        fluidSupportPlan.resolve(capture.writer.acquireChunk(0, 0));
        assertEquals(Byte.valueOf((byte) 0), capture.carvedLiquids.get(fluidCell));
    }

    @Test
    public void optimizedExactPathOutperformsNaiveReference() throws Exception {
        Engine engine = createEngine(128, 92);
        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(92);
        IrisRange worldYRange = new IrisRange(0D, 96D);
        AtomicLong optimizedSamples = new AtomicLong();
        AtomicLong naiveSamples = new AtomicLong();
        countSkippableSamples(optimizedCarver, optimizedSamples);
        countSkippableSamples(naiveCarver, naiveSamples);

        WriterCapture optimizedCapture = createWriterCapture(128);
        int optimizedCarved = optimizedCarver.carve(optimizedCapture.writer, 3, -2, columnWeights, 0D, 0D,
                worldYRange, precomputedSurfaceHeights);
        WriterCapture naiveCapture = createWriterCapture(128);
        int naiveCarved = carveNaiveExact(naiveCarver, naiveCapture.writer, 3, -2, columnWeights,
                worldYRange, precomputedSurfaceHeights);

        assertTrue(optimizedCarved > 0);
        assertEquals(naiveCarved, optimizedCarved);
        assertEquals(naiveCapture.carvedCells, optimizedCapture.carvedCells);
        assertEquals(naiveCapture.carvedLiquids, optimizedCapture.carvedLiquids);

        long optimizedSampleCount = optimizedSamples.get();
        long naiveSampleCount = naiveSamples.get();
        assertTrue(optimizedSampleCount > 0L);
        double sampleRatio = naiveSampleCount / (double) optimizedSampleCount;
        assertTrue("expected at least 1.4x fewer detail and module samples but was " + sampleRatio,
                sampleRatio >= 1.4D);
    }

    @Test
    public void adaptivePathStaysNearExactReferenceWithWarpAndModules() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveCarver3D adaptiveCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(true));
        WriterCapture adaptiveCapture = createWriterCapture(96);
        int adaptiveCarved = adaptiveCarver.carve(adaptiveCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        WriterCapture exactCapture = createWriterCapture(96);
        int exactCarved = exactCarver.carve(exactCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        Set<String> differingCells = new HashSet<>(adaptiveCapture.carvedCells);
        differingCells.addAll(exactCapture.carvedCells);
        Set<String> sharedCells = new HashSet<>(adaptiveCapture.carvedCells);
        sharedCells.retainAll(exactCapture.carvedCells);
        differingCells.removeAll(sharedCells);

        int baselineCells = Math.max(1, exactCapture.carvedCells.size());
        double carveDeltaRatio = Math.abs(adaptiveCarved - exactCarved) / (double) baselineCells;
        double differingRatio = differingCells.size() / (double) baselineCells;
        assertTrue("expected carve count delta below 2.5% but was " + carveDeltaRatio, carveDeltaRatio <= 0.025D);
        assertTrue("expected carved cell delta below 3.5% but was " + differingRatio, differingRatio <= 0.035D);
    }

    private void assertFlatSurfaceCeiling(IrisCaveProfile profile) {
        Engine engine = createEngine(96, 72);
        WriterCapture capture = createWriterCapture(96);

        new IrisCaveCarver3D(engine, profile).carve(
                capture.writer, 0, 0, fullWeights(), 0D, 0D, null, filledHeights(72));

        int[] ceilingYByColumn = new int[256];
        Arrays.fill(ceilingYByColumn, Integer.MIN_VALUE);
        for (String cell : capture.carvedCells) {
            int localX = coordinate(cell, 0);
            int y = coordinate(cell, 1);
            int localZ = coordinate(cell, 2);
            int columnIndex = (localX << 4) | localZ;
            ceilingYByColumn[columnIndex] = Math.max(ceilingYByColumn[columnIndex], y);
        }

        Map<Integer, Integer> ceilingPlaneCounts = new HashMap<>();
        int largestPlane = 0;
        for (int ceilingY : ceilingYByColumn) {
            assertTrue(ceilingY != Integer.MIN_VALUE);
            assertTrue(ceilingY <= 67);
            int planeCount = ceilingPlaneCounts.merge(ceilingY, 1, Integer::sum);
            largestPlane = Math.max(largestPlane, planeCount);
        }

        assertTrue("expected at least three ceiling levels but found " + ceilingPlaneCounts,
                ceilingPlaneCounts.size() >= 3);
        assertTrue("expected no dominant ceiling plane but found " + ceilingPlaneCounts,
                largestPlane < 192);
    }

    private void assertExactParity(boolean warp, boolean modules, boolean adaptiveSampling) throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, createProfile(warp, modules).setAdaptiveSampling(adaptiveSampling));
        WriterCapture optimizedCapture = createWriterCapture(96);
        int optimizedCarved = optimizedCarver.carve(optimizedCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, createProfile(warp, modules).setAdaptiveSampling(false));
        WriterCapture naiveCapture = createWriterCapture(96);
        int naiveCarved = carveNaiveExact(naiveCarver, naiveCapture.writer, 5, -1, columnWeights, worldYRange, precomputedSurfaceHeights);

        assertEquals(optimizedCarved, naiveCarved);
        assertEquals(optimizedCapture.carvedCells, naiveCapture.carvedCells);
        assertEquals(optimizedCapture.carvedLiquids, naiveCapture.carvedLiquids);
    }

    private void countSkippableSamples(IrisCaveCarver3D carver, AtomicLong samples) throws Exception {
        detailDensityField.set(carver, new CountingCNG((CNG) detailDensityField.get(carver), samples));
        CaveFieldModuleState[] modules = (CaveFieldModuleState[]) modulesField.get(carver);
        for (CaveFieldModuleState module : modules) {
            moduleDensityField.set(module, new CountingCNG((CNG) moduleDensityField.get(module), samples));
        }
    }

    private double sampleDensity(IrisCaveCarver3D carver, int x, int y, int z) throws Exception {
        return (double) sampleDensityMethod.invoke(carver, x, y, z);
    }

    private int carveNaiveExact(IrisCaveCarver3D carver, MantleWriter writer, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights) throws Exception {
        Engine engine = (Engine) engineField.get(carver);
        IrisData data = (IrisData) dataField.get(carver);
        IrisCaveProfile profile = (IrisCaveProfile) profileField.get(carver);
        CNG surfaceBreakDensity = (CNG) surfaceBreakDensityField.get(carver);
        RNG thresholdRng = (RNG) thresholdRngField.get(carver);
        MatterCavern carveAir = (MatterCavern) carveAirField.get(carver);
        MatterCavern carveFluid = (MatterCavern) carveFluidField.get(carver);
        MatterCavern carveLava = (MatterCavern) carveLavaField.get(carver);
        MatterCavern carveForcedAir = (MatterCavern) carveForcedAirField.get(carver);

        double[] resolvedWeights = columnWeights;
        if (resolvedWeights == null || resolvedWeights.length < 256) {
            resolvedWeights = fullWeights();
        }

        int worldHeight = writer.getMantle().getWorldHeight();
        int minY = Math.max(0, (int) Math.floor(profile.getVerticalRange().getMin()));
        int maxY = Math.min(worldHeight - 1, (int) Math.ceil(profile.getVerticalRange().getMax()));
        if (worldYRange != null) {
            int worldMinHeight = engine.getWorld().minHeight();
            int rangeMinY = (int) Math.floor(worldYRange.getMin() - worldMinHeight);
            int rangeMaxY = (int) Math.ceil(worldYRange.getMax() - worldMinHeight);
            minY = Math.max(minY, rangeMinY);
            maxY = Math.min(maxY, rangeMaxY);
        }
        if (maxY < minY) {
            return 0;
        }

        boolean allowSurfaceBreak = profile.isAllowSurfaceBreak();
        int surfaceClearance = Math.max(0, profile.getSurfaceClearance());
        int surfaceBreakDepth = Math.max(0, profile.getSurfaceBreakDepth());
        double surfaceBreakNoiseThreshold = profile.getSurfaceBreakNoiseThreshold();
        double surfaceBreakThresholdBoost = Math.max(0D, profile.getSurfaceBreakThresholdBoost());
        int[] columnTopY = new int[256];
        int[] surfaceBreakFloorY = new int[256];
        boolean[] surfaceBreakColumn = new boolean[256];
        boolean[] surfaceCeilingColumn = new boolean[256];
        int[] fluidMaxY = new int[256];
        double[] passThreshold = new double[256];
        double[] verticalEdgeFade = computeVerticalEdgeFade(profile, minY, maxY);
        double[] surfaceClosureThreshold = computeSurfaceClosureThresholds(profile, minY, maxY);
        MatterCavern[] matterByY = computeMatterByY(engine, profile, carveAir, carveLava, carveForcedAir, minY, maxY);

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        for (int localX = 0; localX < 16; localX++) {
            int x = x0 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = z0 + localZ;
                int columnIndex = (localX << 4) | localZ;
                int columnSurfaceY;
                if (precomputedSurfaceHeights != null && precomputedSurfaceHeights.length > columnIndex) {
                    columnSurfaceY = precomputedSurfaceHeights[columnIndex];
                } else {
                    columnSurfaceY = engine.getHeight(x, z);
                }

                int unclampedClearanceTopY = columnSurfaceY - surfaceClearance;
                int clearanceTopY = Math.min(maxY, Math.max(minY, unclampedClearanceTopY));
                boolean breakColumn = allowSurfaceBreak && signed(surfaceBreakDensity.noiseFast2D(x, z)) >= surfaceBreakNoiseThreshold;
                int resolvedTopY = breakColumn ? Math.min(maxY, Math.max(minY, columnSurfaceY)) : clearanceTopY;
                columnTopY[columnIndex] = resolvedTopY;
                fluidMaxY[columnIndex] = profile.isAllowFluid()
                        ? Math.min(engine.getDimension().getFluidHeight(), columnSurfaceY - Math.max(0, profile.getFluidMinDepthBelowSurface()))
                        : Integer.MIN_VALUE;
                surfaceBreakFloorY[columnIndex] = Math.max(minY, columnSurfaceY - surfaceBreakDepth);
                surfaceBreakColumn[columnIndex] = breakColumn;
                surfaceCeilingColumn[columnIndex] = !breakColumn && unclampedClearanceTopY <= maxY;
                double columnWeight = clampColumnWeight(resolvedWeights[columnIndex]);
                if (columnWeight <= 0D || resolvedTopY < minY) {
                    passThreshold[columnIndex] = Double.NaN;
                    continue;
                }

                passThreshold[columnIndex] = profile.getDensityThreshold().get(thresholdRng, x, z, data) - profile.getThresholdBias();
            }
        }

        @SuppressWarnings("unchecked")
        MantleChunk<Matter> chunk = writer.acquireChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }

        int carved = 0;
        for (int localX = 0; localX < 16; localX++) {
            int x = x0 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = z0 + localZ;
                int columnIndex = (localX << 4) | localZ;
                if (Double.isNaN(passThreshold[columnIndex])) {
                    continue;
                }

                int topY = columnTopY[columnIndex];
                for (int y = minY; y <= topY; y++) {
                    double localThreshold = passThreshold[columnIndex];
                    if (surfaceBreakColumn[columnIndex] && y >= surfaceBreakFloorY[columnIndex]) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }
                    localThreshold -= verticalEdgeFade[y - minY];
                    localThreshold = applySurfaceCeilingFade(
                            localThreshold,
                            surfaceCeilingColumn[columnIndex],
                            topY,
                            y,
                            minY,
                            surfaceClosureThreshold
                    );

                    double density = (double) sampleDensityMethod.invoke(carver, x, y, z);
                    if (density > localThreshold) {
                        continue;
                    }

                    Matter sectionMatter = chunk.getOrCreate(y >> 4);
                    MatterSlice<MatterCavern> cavernSlice = sectionMatter.slice(MatterCavern.class);
                    MatterCavern verticalMatter = matterByY[y - minY];
                    boolean aquifer = verticalMatter == carveAir
                            && y <= fluidMaxY[columnIndex]
                            && (boolean) aquiferCandidateMethod.invoke(carver, x, y, z, localThreshold);
                    MatterCavern matter = aquifer ? carveFluid : verticalMatter;
                    cavernSlice.set(localX, y & 15, localZ, matter);
                    carved++;
                }
            }
        }

        return carved;
    }

    private double applySurfaceCeilingFade(
            double threshold,
            boolean surfaceCeilingColumn,
            int columnTopY,
            int y,
            int minY,
            double[] surfaceClosureThreshold
    ) {
        if (!surfaceCeilingColumn) {
            return threshold;
        }

        int ceilingDistance = columnTopY - y;
        if (ceilingDistance < 0 || ceilingDistance >= SURFACE_CEILING_FADE_DEPTH) {
            return threshold;
        }

        double closureThreshold = surfaceClosureThreshold[y - minY];
        if (threshold <= closureThreshold) {
            return threshold;
        }

        double progress = ceilingDistance / (double) SURFACE_CEILING_FADE_DEPTH;
        double smooth = progress * progress * (3D - (2D * progress));
        return closureThreshold + ((threshold - closureThreshold) * smooth);
    }

    private double[] computeSurfaceClosureThresholds(IrisCaveProfile profile, int minY, int maxY) {
        double normalization = Math.abs(profile.getBaseWeight()) + Math.abs(profile.getDetailWeight());
        for (IrisCaveFieldModule module : profile.getModules()) {
            normalization += Math.abs(module.getWeight());
        }
        if (normalization <= 0D) {
            normalization = 1D;
        }

        double[] thresholds = new double[Math.max(0, maxY - minY + 1)];
        double baseMinimum = -Math.abs(profile.getBaseWeight()) - Math.abs(profile.getDetailWeight());
        for (int y = minY; y <= maxY; y++) {
            double minimumDensity = baseMinimum;
            for (IrisCaveFieldModule module : profile.getModules()) {
                IrisRange range = module.getVerticalRange();
                if (y < Math.floor(range.getMin()) || y > Math.ceil(range.getMax())) {
                    continue;
                }
                double rawMinimum = module.isInvert()
                        ? module.getThreshold() - 1D
                        : -1D - module.getThreshold();
                double rawMaximum = module.isInvert()
                        ? module.getThreshold() + 1D
                        : 1D - module.getThreshold();
                minimumDensity += Math.min(rawMinimum * module.getWeight(), rawMaximum * module.getWeight());
            }
            thresholds[y - minY] = (minimumDensity / normalization) - SURFACE_CEILING_SOLID_EPSILON;
        }
        return thresholds;
    }

    private double[] computeVerticalEdgeFade(IrisCaveProfile profile, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        double[] verticalEdgeFade = new double[size];
        int fadeRange = Math.max(0, profile.getVerticalEdgeFade());
        double fadeStrength = Math.max(0D, profile.getVerticalEdgeFadeStrength());
        if (size == 0 || fadeRange <= 0 || maxY <= minY || fadeStrength <= 0D) {
            return verticalEdgeFade;
        }

        for (int y = minY; y <= maxY; y++) {
            int floorDistance = y - minY;
            int ceilingDistance = maxY - y;
            int edgeDistance = Math.min(floorDistance, ceilingDistance);
            int offsetIndex = y - minY;
            if (edgeDistance >= fadeRange) {
                continue;
            }

            double t = Math.max(0D, Math.min(1D, edgeDistance / (double) fadeRange));
            double smooth = t * t * (3D - (2D * t));
            verticalEdgeFade[offsetIndex] = (1D - smooth) * fadeStrength;
        }

        return verticalEdgeFade;
    }

    private MatterCavern[] computeMatterByY(Engine engine, IrisCaveProfile profile, MatterCavern carveAir, MatterCavern carveLava, MatterCavern carveForcedAir, int minY, int maxY) {
        MatterCavern[] matterByY = new MatterCavern[Math.max(0, maxY - minY + 1)];
        boolean allowLava = profile.isAllowLava();
        int lavaHeight = engine.getDimension().getCaveLavaHeight();

        for (int y = minY; y <= maxY; y++) {
            int offset = y - minY;
            if (allowLava && y <= lavaHeight) {
                matterByY[offset] = carveLava;
                continue;
            }
            if (!allowLava && y <= lavaHeight) {
                matterByY[offset] = carveForcedAir;
                continue;
            }

            matterByY[offset] = carveAir;
        }

        return matterByY;
    }

    private Engine createEngine(int worldHeight, int sampledHeight) {
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        SeedManager seedManager = mock(SeedManager.class);
        EngineMetrics metrics = new EngineMetrics(16);
        IrisWorld world = IrisWorld.builder().minHeight(0).maxHeight(worldHeight).build();

        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(CARVE_SEED).when(seedManager).getCarve();
        doReturn(metrics).when(engine).getMetrics();
        doReturn(world).when(engine).getWorld();
        doReturn(sampledHeight).when(engine).getHeight(anyInt(), anyInt());

        doReturn(18).when(dimension).getCaveLavaHeight();
        doReturn(64).when(dimension).getFluidHeight();

        return engine;
    }

    private IrisCaveProfile createProfile(boolean warp, boolean modules) {
        IrisCaveProfile profile = new IrisCaveProfile();
        profile.setEnabled(true);
        profile.setVerticalRange(new IrisRange(0D, 120D));
        profile.setVerticalEdgeFade(14);
        profile.setVerticalEdgeFadeStrength(0.21D);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.109375D));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.265625D));
        profile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.1875D));
        profile.setSurfaceBreakStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.140625D));
        profile.setBaseWeight(1D);
        profile.setDetailWeight(0.48D);
        profile.setWarpStrength(warp ? 0.37D : 0D);
        profile.setDensityThreshold(new IrisStyledRange(1D, 1D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
        profile.setThresholdBias(0D);
        profile.setSampleStep(1);
        profile.setSurfaceClearance(5);
        profile.setAllowSurfaceBreak(true);
        profile.setSurfaceBreakNoiseThreshold(0.16D);
        profile.setSurfaceBreakDepth(12);
        profile.setSurfaceBreakThresholdBoost(0.17D);
        profile.setAllowFluid(true);
        profile.setFluidMinDepthBelowSurface(8);
        profile.setFluidRequiresFloor(false);
        profile.setAllowLava(true);
        if (modules) {
            KList<IrisCaveFieldModule> caveModules = new KList<>();
            caveModules.add(new IrisCaveFieldModule(
                    new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.171875D),
                    0.23D,
                    0.04D,
                    new IrisRange(0D, 72D),
                    false
            ));
            caveModules.add(new IrisCaveFieldModule(
                    new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.296875D),
                    0.17D,
                    -0.06D,
                    new IrisRange(24D, 120D),
                    true
            ));
            profile.setModules(caveModules);
        } else {
            profile.setModules(new KList<>());
        }
        return profile;
    }

    private IrisCaveProfile createFluidProfile() {
        return createProfile(false, false)
                .setVerticalRange(new IrisRange(20D, 70D))
                .setVerticalEdgeFade(0)
                .setVerticalEdgeFadeStrength(0D)
                .setDensityThreshold(new IrisStyledRange(2D, 2D, new IrisGeneratorStyle(NoiseStyle.FLAT)))
                .setAdaptiveSampling(false)
                .setSurfaceClearance(5)
                .setAllowSurfaceBreak(true)
                .setSurfaceBreakNoiseThreshold(-1D)
                .setSurfaceBreakThresholdBoost(0D)
                .setAllowFluid(true)
                .setFluidRequiresFloor(false)
                .setAllowLava(true);
    }

    private void assertSurfaceFluidBoundaryForProfile(IrisCaveProfile profile) {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = filledHeights(70);
        surfaceHeights[0] = 60;
        long[] boundaries = new long[256];
        Arrays.fill(boundaries, SurfaceFluidBoundaryPlan.boundary(
                SurfaceFluidBoundaryPlan.NO_BOUNDARY, Integer.MIN_VALUE));
        boundaries[0] = SurfaceFluidBoundaryPlan.boundary(60, 64);
        boundaries[16] = SurfaceFluidBoundaryPlan.boundary(61, 64);
        WriterCapture capture = createWriterCapture(80);
        CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();

        new IrisCaveCarver3D(engine, profile.setFluidMinDepthBelowSurface(0).setFluidRequiresFloor(false)).carve(
                capture.writer,
                0,
                0,
                fullWeights(),
                0D,
                0D,
                null,
                surfaceHeights,
                boundaries,
                null,
                supportPlan
        );

        assertTrue(capture.carvedCells.contains(cellKey(0, 56, 0)));
        assertFalse(capture.carvedCells.contains(cellKey(0, 60, 0)));
        assertTrue(capture.carvedCells.contains(cellKey(1, 60, 0)));
        for (int y = 61; y <= 64; y++) {
            assertFalse(capture.carvedCells.contains(cellKey(1, y, 0)));
        }
        assertTrue(capture.carvedCells.contains(cellKey(1, 65, 0)));
        assertTrue(capture.carvedCells.contains(cellKey(2, 64, 0)));
        assertTrue(countLiquid(capture, (byte) 1) > 0);
        assertTrue(countLiquid(capture, (byte) 0) > 0);
    }

    private WriterCapture createWriterCapture(int worldHeight) {
        MantleWriter writer = mock(MantleWriter.class);
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        @SuppressWarnings("unchecked")
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Map<Integer, Matter> sections = new HashMap<>();
        Map<Integer, Map<Integer, MatterCavern>> sectionCells = new HashMap<>();
        Set<String> carvedCells = new HashSet<>();
        Map<String, Byte> carvedLiquids = new HashMap<>();

        doReturn(mantle).when(writer).getMantle();
        doReturn(worldHeight).when(mantle).getWorldHeight();
        doReturn(chunk).when(writer).acquireChunk(anyInt(), anyInt());
        doAnswer(invocation -> sections.get(invocation.getArgument(0, Integer.class))).when(chunk).get(anyInt());
        doAnswer(invocation -> {
            int sectionIndex = invocation.getArgument(0);
            Matter section = sections.get(sectionIndex);
            if (section != null) {
                return section;
            }

            Matter created = createSection(sectionIndex, sectionCells, carvedCells, carvedLiquids);
            sections.put(sectionIndex, created);
            return created;
        }).when(chunk).getOrCreate(anyInt());

        return new WriterCapture(writer, carvedCells, carvedLiquids);
    }

    private Matter createSection(int sectionIndex, Map<Integer, Map<Integer, MatterCavern>> sectionCells, Set<String> carvedCells, Map<String, Byte> carvedLiquids) {
        Matter matter = mock(Matter.class);
        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> slice = mock(MatterSlice.class);
        Map<Integer, MatterCavern> localCells = sectionCells.computeIfAbsent(sectionIndex, key -> new HashMap<>());

        doReturn(slice).when(matter).slice(MatterCavern.class);
        doReturn(slice).when(matter).getSlice(MatterCavern.class);
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            return localCells.get(packLocal(localX, localY, localZ));
        }).when(slice).get(anyInt(), anyInt(), anyInt());
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            MatterCavern value = invocation.getArgument(3);
            localCells.put(packLocal(localX, localY, localZ), value);
            int worldY = (sectionIndex << 4) + localY;
            String cellKey = cellKey(localX, worldY, localZ);
            carvedCells.add(cellKey);
            carvedLiquids.put(cellKey, value.getLiquid());
            return null;
        }).when(slice).set(anyInt(), anyInt(), anyInt(), any(MatterCavern.class));

        return matter;
    }

    private double[] fullWeights() {
        double[] columnWeights = new double[256];
        Arrays.fill(columnWeights, 1D);
        return columnWeights;
    }

    private int[] filledHeights(int height) {
        int[] heights = new int[256];
        Arrays.fill(heights, height);
        return heights;
    }

    private int[] splitSurfaceHeights(int lowHeight, int highHeight) {
        int[] heights = new int[256];
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                heights[(localX << 4) | localZ] = localX < 8 ? lowHeight : highHeight;
            }
        }
        return heights;
    }

    private void assertFluidCellsHaveSolidSupport(WriterCapture capture) {
        for (Map.Entry<String, Byte> entry : capture.carvedLiquids.entrySet()) {
            if (entry.getValue() != 1) {
                continue;
            }

            String[] split = entry.getKey().split(":");
            int x = Integer.parseInt(split[0]);
            int y = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);
            assertFalse(capture.carvedCells.contains(cellKey(x, y - 1, z)));
            assertFalse(capture.carvedCells.contains(cellKey(x, y - 2, z)));
            int support = 0;
            if (!capture.carvedCells.contains(cellKey(x + 1, y, z))) {
                support++;
            }
            if (!capture.carvedCells.contains(cellKey(x - 1, y, z))) {
                support++;
            }
            if (!capture.carvedCells.contains(cellKey(x, y, z + 1))) {
                support++;
            }
            if (!capture.carvedCells.contains(cellKey(x, y, z - 1))) {
                support++;
            }
            if (!capture.carvedCells.contains(cellKey(x, y + 1, z))) {
                support++;
            }
            assertTrue(support >= 4);
        }
    }

    private void assertFluidRespectsSplitCutoff(WriterCapture capture, int lowCutoff, int highCutoff) {
        boolean lowFluid = false;
        boolean highFluid = false;
        for (Map.Entry<String, Byte> entry : capture.carvedLiquids.entrySet()) {
            if (entry.getValue() != 1) {
                continue;
            }
            int x = coordinate(entry.getKey(), 0);
            int y = coordinate(entry.getKey(), 1);
            if (x < 8) {
                assertTrue(y <= lowCutoff);
                lowFluid = true;
            } else {
                assertTrue(y <= highCutoff);
                highFluid = true;
            }
        }
        assertTrue(lowFluid);
        assertTrue(highFluid);
    }

    private void assertLiquidAtOrBelow(WriterCapture capture, byte liquid, int maxY) {
        for (Map.Entry<String, Byte> entry : capture.carvedLiquids.entrySet()) {
            if (entry.getValue() == liquid) {
                assertTrue(coordinate(entry.getKey(), 1) <= maxY);
            }
        }
    }

    private int countLiquid(WriterCapture capture, byte liquid) {
        int count = 0;
        for (byte value : capture.carvedLiquids.values()) {
            if (value == liquid) {
                count++;
            }
        }
        return count;
    }

    private String firstCellWithLiquid(WriterCapture capture, byte liquid) {
        for (Map.Entry<String, Byte> entry : capture.carvedLiquids.entrySet()) {
            if (entry.getValue() == liquid) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int coordinate(String cell, int index) {
        return Integer.parseInt(cell.split(":")[index]);
    }

    private double clampColumnWeight(double weight) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return 0D;
        }
        if (weight <= 0D) {
            return 0D;
        }
        if (weight >= 1D) {
            return 1D;
        }
        return weight;
    }

    private double signed(double value) {
        return (value * 2D) - 1D;
    }

    private int packLocal(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    private String cellKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private boolean containsLiquidInRange(Map<String, Byte> carvedLiquids, int minY, int maxY, byte liquid) {
        for (Map.Entry<String, Byte> entry : carvedLiquids.entrySet()) {
            if (entry.getValue() != liquid) {
                continue;
            }

            String[] split = entry.getKey().split(":");
            int y = Integer.parseInt(split[1]);
            if (y >= minY && y <= maxY) {
                return true;
            }
        }
        return false;
    }

    private boolean hasX(Set<String> carvedCells, int x) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[0]) == x) {
                return true;
            }
        }

        return false;
    }

    private boolean hasZ(Set<String> carvedCells, int z) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[2]) == z) {
                return true;
            }
        }

        return false;
    }

    private int maxY(Set<String> carvedCells) {
        int max = Integer.MIN_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y > max) {
                max = y;
            }
        }
        return max;
    }

    private int minY(Set<String> carvedCells) {
        int min = Integer.MAX_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y < min) {
                min = y;
            }
        }
        return min;
    }

    private static final class CountingCNG extends CNG {
        private final CNG delegate;
        private final AtomicLong samples;

        private CountingCNG(CNG delegate, AtomicLong samples) {
            super(new RNG(41_117L));
            this.delegate = delegate;
            this.samples = samples;
        }

        @Override
        public double noiseFastSigned3D(double x, double y, double z) {
            samples.incrementAndGet();
            return delegate.noiseFastSigned3D(x, y, z);
        }
    }

    private static final class CoordinateDensityCNG extends CNG {
        private final Map<String, Double> densityByCoordinate;
        private final List<String> sampledCoordinates = new ArrayList<>();

        private CoordinateDensityCNG(Map<String, Double> densityByCoordinate) {
            super(new RNG(82_991L));
            this.densityByCoordinate = densityByCoordinate;
        }

        @Override
        public double noiseFastSigned3D(double x, double y, double z) {
            String coordinate = (int) x + ":" + (int) y + ":" + (int) z;
            sampledCoordinates.add(coordinate);
            return densityByCoordinate.getOrDefault(coordinate, 1D);
        }
    }

    private static final class WriterCapture {
        private final MantleWriter writer;
        private final Set<String> carvedCells;
        private final Map<String, Byte> carvedLiquids;

        private WriterCapture(MantleWriter writer, Set<String> carvedCells, Map<String, Byte> carvedLiquids) {
            this.writer = writer;
            this.carvedCells = carvedCells;
            this.carvedLiquids = carvedLiquids;
        }
    }
}
