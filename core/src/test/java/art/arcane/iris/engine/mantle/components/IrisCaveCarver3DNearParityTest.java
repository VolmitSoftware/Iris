package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.carve.CaveFieldModule;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.terrain.FluidPaletteSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.terrain.carving.ResolvedCaveProfile;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.engine.object.noise.IrisStyledRange;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.noise.NoiseStyle;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCaveCarver3DNearParityTest {
    private static final int SURFACE_CEILING_FADE_DEPTH = 12;
    private static final double SURFACE_CEILING_SOLID_EPSILON = 0.000001D;

    private static Method sampleDensityMethod;
    private static Method aquiferCandidateMethod;
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
    public void genericFluidContractKeepsOverworldDefaults() {
        CaveProfile profile = new CaveProfile();
        assertTrue(profile.allowFluid());
        assertEquals(12, profile.fluidMinDepthBelowSurface());
        assertTrue(profile.fluidRequiresFloor());
        assertEquals("water", new FluidPaletteSettings().palette().palette().get(0).block());
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
        CaveProfile firstProfile = createProfile(true, false);
        TestSettings.with(firstProfile, "warpStyle", com.volmit.noise.NoiseStyle.of("SIMPLEX").withZoom(0.12D));
        CaveProfile secondProfile = createProfile(true, false);
        TestSettings.with(secondProfile, "warpStyle", com.volmit.noise.NoiseStyle.of("CELLULAR").withZoom(0.12D));
        CaveProfile controlProfile = createProfile(true, false);
        TestSettings.with(controlProfile, "warpStyle", com.volmit.noise.NoiseStyle.of("CELLULAR").withZoom(0.12D));
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
        assertFlatSurfaceCeiling(TestSettings.with(createFluidProfile(), "verticalRange", com.volmit.config.Range.of(20D, 72D), "allowSurfaceBreak", false, "surfaceClearance", 5, "allowFluid", false, "allowLava", false, "adaptiveSampling", false, "sampleStep", 1));
        assertFlatSurfaceCeiling(TestSettings.with(createFluidProfile(), "verticalRange", com.volmit.config.Range.of(20D, 72D), "allowSurfaceBreak", false, "surfaceClearance", 5, "allowFluid", false, "allowLava", false, "adaptiveSampling", true, "sampleStep", 1));
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
    public void legacySampleStepTwoMatchesExactReference() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        CaveProfile exactProfile = TestSettings.with(createProfile(true, true), "sampleStep", 1, "adaptiveSampling", false);
        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, exactProfile);
        WriterCapture exactCapture = createWriterCapture(96);
        int exactCarved = exactCarver.carve(exactCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        CaveProfile legacyProfile = TestSettings.with(createProfile(true, true), "sampleStep", 2, "adaptiveSampling", false);
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

        CaveProfile lavaProfile = TestSettings.with(createProfile(false, false), "allowLava", true, "allowFluid", false);
        IrisCaveCarver3D lavaCarver = new IrisCaveCarver3D(engine, lavaProfile);
        WriterCapture lavaCapture = createWriterCapture(48);
        lavaCarver.carve(lavaCapture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        CaveProfile forcedAirProfile = TestSettings.with(createProfile(false, false), "allowLava", false, "allowFluid", false);
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
        CaveProfile wetProfile = TestSettings.with(createFluidProfile(), "verticalRange", com.volmit.config.Range.of(2D, 18D), "allowLava", false, "fluidRequiresFloor", false);
        WriterCapture wetCapture = createWriterCapture(48);
        new IrisCaveCarver3D(engine, wetProfile).carve(
                wetCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        CaveProfile dryProfile = TestSettings.with(createFluidProfile(), "verticalRange", com.volmit.config.Range.of(2D, 18D), "allowFluid", false, "allowLava", false, "fluidRequiresFloor", false);
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

        CaveProfile enabledProfile = TestSettings.with(createFluidProfile(), "allowFluid", true, "fluidMinDepthBelowSurface", 10);
        WriterCapture enabledCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, enabledProfile).carve(
                enabledCapture.writer, 0, 0, fullWeights(), 0D, 0D, null, surfaceHeights);

        CaveProfile disabledProfile = TestSettings.with(createFluidProfile(), "allowFluid", false, "fluidMinDepthBelowSurface", 10);
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
        CaveProfile profile = TestSettings.with(createFluidProfile(), "fluidMinDepthBelowSurface", 0);
        WriterCapture capture = createWriterCapture(80);

        new IrisCaveCarver3D(engine, profile).carve(
                capture.writer, 0, 0, fullWeights(), 0D, 0D, null, filledHeights(70));

        assertTrue(countLiquid(capture, (byte) 1) > 0);
        assertLiquidAtOrBelow(capture, (byte) 1, 64);
        assertTrue(containsLiquidInRange(capture.carvedLiquids, 65, 70, (byte) 0));
    }

    @Test
    public void surfaceFluidBoundaryProtectsReservoirWithoutFloodingDeeperCaves() {
        assertSurfaceFluidBoundaryForProfile(TestSettings.with(createFluidProfile(), "adaptiveSampling", false, "sampleStep", 1));
        assertSurfaceFluidBoundaryForProfile(TestSettings.with(createFluidProfile(), "adaptiveSampling", true, "sampleStep", 1));
        assertSurfaceFluidBoundaryForProfile(TestSettings.with(createFluidProfile(), "adaptiveSampling", false, "sampleStep", 4));
    }

    @Test
    public void floorRequiredFluidResolvesAfterTheCompleteCarveMask() {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = filledHeights(70);
        int chunkX = -8;
        int chunkZ = -1;

        com.volmit.noise.StyledRange cupThreshold = com.volmit.noise.StyledRange.of(0.15D, 0.15D, com.volmit.noise.NoiseStyle.of("FLAT"));
        CaveProfile supportedProfile = TestSettings.with(createFluidProfile(), "densityThreshold", cupThreshold, "fluidMinDepthBelowSurface", 0, "fluidRequiresFloor", true);
        WriterCapture firstCapture = createWriterCapture(80);
        CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();
        new IrisCaveCarver3D(engine, supportedProfile).carve(
                firstCapture.writer, chunkX, chunkZ, fullWeights(), 0D, 0D, null, surfaceHeights, null, null, supportPlan);
        int candidateCount = countLiquid(firstCapture, (byte) 1);
        supportPlan.resolve(firstCapture.writer.acquireChunk(chunkX, chunkZ));

        CaveProfile repeatedProfile = TestSettings.with(createFluidProfile(), "densityThreshold", cupThreshold, "fluidMinDepthBelowSurface", 0, "fluidRequiresFloor", true);
        WriterCapture secondCapture = createWriterCapture(80);
        new IrisCaveCarver3D(engine, repeatedProfile).carve(
                secondCapture.writer, chunkX, chunkZ, fullWeights(), 0D, 0D, null, surfaceHeights);

        CaveProfile unrestrictedProfile = TestSettings.with(createFluidProfile(), "densityThreshold", cupThreshold, "fluidMinDepthBelowSurface", 0, "fluidRequiresFloor", false);
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
        CaveProfile fluidProfile = TestSettings.with(createFluidProfile(), "densityThreshold", com.volmit.noise.StyledRange.of(0.15D, 0.15D, com.volmit.noise.NoiseStyle.of("FLAT")), "fluidRequiresFloor", true);
        CaveProfile airProfile = TestSettings.with(createFluidProfile(), "allowFluid", false);

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
        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(true, true), "adaptiveSampling", false));
        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(true, true), "adaptiveSampling", false));
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(92);
        IrisRange worldYRange = new IrisRange(0D, 96D);

        for (int warmup = 0; warmup < 4; warmup++) {
            runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
            runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
        }

        long optimizedTime = Long.MAX_VALUE;
        long naiveTime = Long.MAX_VALUE;
        for (int iteration = 0; iteration < 10; iteration++) {
            if ((iteration & 1) == 0) {
                optimizedTime = Math.min(optimizedTime, runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128));
                naiveTime = Math.min(naiveTime, runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128));
                continue;
            }

            naiveTime = Math.min(naiveTime, runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128));
            optimizedTime = Math.min(optimizedTime, runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128));
        }

        // Fastest of ten alternating runs, not the sum: one scheduling hiccup decides a summed comparison.
        // The bar is deliberately well under the measured ratio; a loaded machine reaches 1.6x while a
        // regression that drops the skip logic lands at 1.0x, which is what this guard has to catch.
        double speedup = naiveTime / (double) optimizedTime;
        assertTrue("expected at least 1.4x speedup but was " + speedup, speedup >= 1.4D);
    }

    @Test
    public void adaptivePathStaysNearExactReferenceWithWarpAndModules() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveCarver3D adaptiveCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(true, true), "adaptiveSampling", true));
        WriterCapture adaptiveCapture = createWriterCapture(96);
        int adaptiveCarved = adaptiveCarver.carve(adaptiveCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(true, true), "adaptiveSampling", false));
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

    private void assertFlatSurfaceCeiling(CaveProfile profile) {
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

        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(warp, modules), "adaptiveSampling", adaptiveSampling));
        WriterCapture optimizedCapture = createWriterCapture(96);
        int optimizedCarved = optimizedCarver.carve(optimizedCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, TestSettings.with(createProfile(warp, modules), "adaptiveSampling", false));
        WriterCapture naiveCapture = createWriterCapture(96);
        int naiveCarved = carveNaiveExact(naiveCarver, naiveCapture.writer, 5, -1, columnWeights, worldYRange, precomputedSurfaceHeights);

        assertEquals(optimizedCarved, naiveCarved);
        assertEquals(optimizedCapture.carvedCells, naiveCapture.carvedCells);
        assertEquals(optimizedCapture.carvedLiquids, naiveCapture.carvedLiquids);
    }

    private long runOptimizedOnce(IrisCaveCarver3D carver, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights, int worldHeight) {
        WriterCapture capture = createWriterCapture(worldHeight);
        long start = System.nanoTime();
        carver.carve(capture.writer, chunkX, chunkZ, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);
        long elapsed = System.nanoTime() - start;
        assertTrue(!capture.carvedCells.isEmpty());
        return elapsed;
    }

    private double sampleDensity(IrisCaveCarver3D carver, int x, int y, int z) throws Exception {
        return (double) sampleDensityMethod.invoke(carver, x, y, z);
    }

    private long runNaiveOnce(IrisCaveCarver3D carver, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights, int worldHeight) throws Exception {
        WriterCapture capture = createWriterCapture(worldHeight);
        long start = System.nanoTime();
        int carved = carveNaiveExact(carver, capture.writer, chunkX, chunkZ, columnWeights, worldYRange, precomputedSurfaceHeights);
        long elapsed = System.nanoTime() - start;
        assertTrue(carved > 0);
        return elapsed;
    }

    private int carveNaiveExact(IrisCaveCarver3D carver, MantleWriter writer, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights) throws Exception {
        Engine engine = (Engine) engineField.get(carver);
        IrisData data = (IrisData) dataField.get(carver);
        CaveProfile profile = (CaveProfile) profileField.get(carver);
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
        int minY = Math.max(0, (int) Math.floor(profile.verticalRange().min()));
        int maxY = Math.min(worldHeight - 1, (int) Math.ceil(profile.verticalRange().max()));
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

        boolean allowSurfaceBreak = profile.allowSurfaceBreak();
        int surfaceClearance = Math.max(0, profile.surfaceClearance());
        int surfaceBreakDepth = Math.max(0, profile.surfaceBreakDepth());
        double surfaceBreakNoiseThreshold = profile.surfaceBreakNoiseThreshold();
        double surfaceBreakThresholdBoost = Math.max(0D, profile.surfaceBreakThresholdBoost());
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
                fluidMaxY[columnIndex] = profile.allowFluid()
                        ? Math.min(engine.getDimension().getFluidHeight(), columnSurfaceY - Math.max(0, profile.fluidMinDepthBelowSurface()))
                        : Integer.MIN_VALUE;
                surfaceBreakFloorY[columnIndex] = Math.max(minY, columnSurfaceY - surfaceBreakDepth);
                surfaceBreakColumn[columnIndex] = breakColumn;
                surfaceCeilingColumn[columnIndex] = !breakColumn && unclampedClearanceTopY <= maxY;
                double columnWeight = clampColumnWeight(resolvedWeights[columnIndex]);
                if (columnWeight <= 0D || resolvedTopY < minY) {
                    passThreshold[columnIndex] = Double.NaN;
                    continue;
                }

                passThreshold[columnIndex] = ResolvedCaveProfile.of(profile).densityThreshold().get(thresholdRng, x, z, data) - profile.thresholdBias();
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

    private double[] computeSurfaceClosureThresholds(CaveProfile profile, int minY, int maxY) {
        double normalization = Math.abs(profile.baseWeight()) + Math.abs(profile.detailWeight());
        for (ResolvedCaveProfile.Module module : ResolvedCaveProfile.of(profile).modules()) {
            normalization += Math.abs(module.weight());
        }
        if (normalization <= 0D) {
            normalization = 1D;
        }

        double[] thresholds = new double[Math.max(0, maxY - minY + 1)];
        double baseMinimum = -Math.abs(profile.baseWeight()) - Math.abs(profile.detailWeight());
        for (int y = minY; y <= maxY; y++) {
            double minimumDensity = baseMinimum;
            for (ResolvedCaveProfile.Module module : ResolvedCaveProfile.of(profile).modules()) {
                IrisRange range = module.verticalRange();
                if (y < Math.floor(range.getMin()) || y > Math.ceil(range.getMax())) {
                    continue;
                }
                double rawMinimum = module.invert()
                        ? module.threshold() - 1D
                        : -1D - module.threshold();
                double rawMaximum = module.invert()
                        ? module.threshold() + 1D
                        : 1D - module.threshold();
                minimumDensity += Math.min(rawMinimum * module.weight(), rawMaximum * module.weight());
            }
            thresholds[y - minY] = (minimumDensity / normalization) - SURFACE_CEILING_SOLID_EPSILON;
        }
        return thresholds;
    }

    private double[] computeVerticalEdgeFade(CaveProfile profile, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        double[] verticalEdgeFade = new double[size];
        int fadeRange = Math.max(0, profile.verticalEdgeFade());
        double fadeStrength = Math.max(0D, profile.verticalEdgeFadeStrength());
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

    private MatterCavern[] computeMatterByY(Engine engine, CaveProfile profile, MatterCavern carveAir, MatterCavern carveLava, MatterCavern carveForcedAir, int minY, int maxY) {
        MatterCavern[] matterByY = new MatterCavern[Math.max(0, maxY - minY + 1)];
        boolean allowLava = profile.allowLava();
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
        SeedManager seedManager = new SeedManager(942_337_445L);
        EngineMetrics metrics = new EngineMetrics(16);
        IrisWorld world = IrisWorld.builder().minHeight(0).maxHeight(worldHeight).build();

        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(metrics).when(engine).getMetrics();
        doReturn(world).when(engine).getWorld();
        doReturn(sampledHeight).when(engine).getHeight(anyInt(), anyInt());

        doReturn(18).when(dimension).getCaveLavaHeight();
        doReturn(64).when(dimension).getFluidHeight();

        return engine;
    }

    private CaveProfile createProfile(boolean warp, boolean modules) {
        CaveProfile profile = new CaveProfile();
        TestSettings.with(profile, "enabled", true);
        TestSettings.with(profile, "verticalRange", com.volmit.config.Range.of(0D, 120D));
        TestSettings.with(profile, "verticalEdgeFade", 14);
        TestSettings.with(profile, "verticalEdgeFadeStrength", 0.21D);
        TestSettings.with(profile, "baseDensityStyle", com.volmit.noise.NoiseStyle.of("SIMPLEX").withZoom(0.07D));
        TestSettings.with(profile, "detailDensityStyle", com.volmit.noise.NoiseStyle.of("SIMPLEX").withZoom(0.17D));
        TestSettings.with(profile, "warpStyle", com.volmit.noise.NoiseStyle.of("SIMPLEX").withZoom(0.12D));
        TestSettings.with(profile, "surfaceBreakStyle", com.volmit.noise.NoiseStyle.of("SIMPLEX").withZoom(0.09D));
        TestSettings.with(profile, "baseWeight", 1D);
        TestSettings.with(profile, "detailWeight", 0.48D);
        TestSettings.with(profile, "warpStrength", warp ? 0.37D : 0D);
        TestSettings.with(profile, "densityThreshold", com.volmit.noise.StyledRange.of(1D, 1D, com.volmit.noise.NoiseStyle.of("FLAT")));
        TestSettings.with(profile, "thresholdBias", 0D);
        TestSettings.with(profile, "sampleStep", 1);
        TestSettings.with(profile, "surfaceClearance", 5);
        TestSettings.with(profile, "allowSurfaceBreak", true);
        TestSettings.with(profile, "surfaceBreakNoiseThreshold", 0.16D);
        TestSettings.with(profile, "surfaceBreakDepth", 12);
        TestSettings.with(profile, "surfaceBreakThresholdBoost", 0.17D);
        TestSettings.with(profile, "allowFluid", true);
        TestSettings.with(profile, "fluidMinDepthBelowSurface", 8);
        TestSettings.with(profile, "fluidRequiresFloor", false);
        TestSettings.with(profile, "allowLava", true);
        if (modules) {
            TestSettings.with(profile, "modules", List.of(
                    TestSettings.of(CaveFieldModule.class, "{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.11}, \"weight\": 0.23,"
                            + " \"threshold\": 0.04, \"verticalRange\": {\"min\": 0, \"max\": 72}, \"invert\": false}"),
                    TestSettings.of(CaveFieldModule.class, "{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.19}, \"weight\": 0.17,"
                            + " \"threshold\": -0.06, \"verticalRange\": {\"min\": 24, \"max\": 120}, \"invert\": true}")));
        } else {
            TestSettings.with(profile, "modules", List.of());
        }
        return profile;
    }

    private CaveProfile createFluidProfile() {
        return TestSettings.with(createProfile(false, false), "verticalRange", com.volmit.config.Range.of(20D, 70D), "verticalEdgeFade", 0, "verticalEdgeFadeStrength", 0D, "densityThreshold", com.volmit.noise.StyledRange.of(2D, 2D, com.volmit.noise.NoiseStyle.of("FLAT")), "adaptiveSampling", false, "surfaceClearance", 5, "allowSurfaceBreak", true, "surfaceBreakNoiseThreshold", -1D, "surfaceBreakThresholdBoost", 0D, "allowFluid", true, "fluidRequiresFloor", false, "allowLava", true);
    }

    private void assertSurfaceFluidBoundaryForProfile(CaveProfile profile) {
        Engine engine = createEngine(80, 70);
        int[] surfaceHeights = filledHeights(70);
        surfaceHeights[0] = 60;
        int[] boundaryStartY = new int[256];
        Arrays.fill(boundaryStartY, SurfaceFluidBoundaryPlan.NO_BOUNDARY);
        boundaryStartY[0] = 60;
        boundaryStartY[16] = 61;
        WriterCapture capture = createWriterCapture(80);
        CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();

        new IrisCaveCarver3D(engine, TestSettings.with(profile, "fluidMinDepthBelowSurface", 0, "fluidRequiresFloor", false)).carve(
                capture.writer,
                0,
                0,
                fullWeights(),
                0D,
                0D,
                null,
                surfaceHeights,
                boundaryStartY,
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
