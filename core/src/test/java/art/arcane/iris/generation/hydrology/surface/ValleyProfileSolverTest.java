package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ValleyProfileSolverTest {
    private static final int SEA_LEVEL = 60;
    private static final HydrologyGeometrySampler CONSTANT_GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 2;
        default -> request.minimum();
    };

    @Test
    public void diagonalCourseUsesDistanceInBlocksForItsMinimumLength() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0), new HydrologyPoint(48, 0, 48)));
        ChannelProfile channel = new ChannelProfileBuilder(HydrologyPlannerSettings.defaults().surface(),
                terrain, CONSTANT_GEOMETRY).build(centerline, "water", false);

        ValleyProfile valley = solver(terrain).solve(centerline, channel, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(49, centerline.size());
        assertNull(valley.rejection());
    }

    @Test
    public void minimumLengthAcceptsItsExactDistanceAndRejectsOneBlockLess() {
        ValleyProfile exact = solve(65, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);
        ValleyProfile shortCourse = solve(64, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);

        assertNull(exact.rejection());
        assertEquals(HydrologyCandidateRejection.COURSE_TOO_SHORT, shortCourse.rejection());
        assertEquals(63, shortCourse.rejectionDetail());
    }

    @Test
    public void minimumLengthExcludesTheUnexposedOceanTerminal() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 48
                ? HydrologyTerrainSample.ocean(50, "ocean") : HydrologyTerrainSample.openLand(66, 0D, "land");
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0), new HydrologyPoint(80, 0, 80)));
        HydrologyPlannerSettings.Surface surface = HydrologyPlannerSettings.defaults().surface();
        ChannelProfile channel = new ChannelProfileBuilder(surface, terrain, CONSTANT_GEOMETRY)
                .build(centerline, "water", false);

        ValleyProfile valley = new ValleyProfileSolver(surface, terrain, SEA_LEVEL, 67)
                .solve(centerline, channel, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(HydrologyCandidateRejection.COURSE_TOO_SHORT, valley.rejection());
        assertEquals(66, valley.rejectionDetail());
    }

    @Test
    public void flatTerrainHoldsTheHeadAtTheLowestBankByDefault() {
        ValleyProfile valley = solve(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        assertEquals(300, valley.exposedStations());
        for (int station = 0; station < valley.exposedStations(); station++) {
            assertEquals(80, valley.head()[station]);
            assertEquals(80, valley.crossMin()[station]);
            assertEquals(80, valley.centerNatural()[station]);
        }
    }

    @Test
    public void aConfiguredSinkHoldsTheHeadThatFarBelowTheLowestBank() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        ValleyProfile valley = solver(flat, 2).solve(straight(300), channel(300, flat), SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        for (int station = 0; station < valley.exposedStations(); station++) {
            assertEquals(78, valley.head()[station]);
        }
    }

    @Test
    public void gentleSlopeProducesOneBlockNonRisingSteps() {
        ValleyProfile valley = solve(400, (x, z) -> 120 - x / 8, SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        int steps = 0;
        for (int station = 1; station < valley.exposedStations(); station++) {
            int drop = valley.head()[station - 1] - valley.head()[station];
            assertTrue(drop >= 0);
            assertTrue(drop <= 1);
            steps += drop;
        }
        assertTrue(steps >= 45 && steps <= 51);
        assertContained(valley);
    }

    @Test
    public void hillsideTraverseKeepsTheHeadBelowTheLowestBank() {
        ValleyProfile valley = solve(300, (x, z) -> 90 + z, SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        assertContained(valley);
        assertTrue(valley.head()[150] < valley.centerNatural()[150] - 1);
    }

    @Test
    public void aDipBesideTheChannelLowersEveryDownstreamHead() {
        ValleyProfile valley = solve(300, (x, z) -> x == 150 && z == 6 ? 74 : 80, SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        assertEquals(80, valley.head()[100]);
        assertTrue(valley.head()[150] <= 74);
        assertTrue(valley.head()[299] <= 74);
        assertContained(valley);
    }

    @Test
    public void oceanTruncatesTheCourseAndPinsTheMouthToSeaLevel() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100 - x / 6, 0D, "land");
        ValleyProfile valley = solver(sampler).solve(
                straight(300),
                channel(300, sampler),
                SurfaceTerminal.OCEAN_MOUTH,
                SEA_LEVEL
        );

        assertNull(valley.rejection());
        assertTrue(valley.exposedStations() > 200);
        assertTrue(valley.exposedStations() <= 240);
        assertTrue(valley.head()[valley.exposedStations() - 1] >= SEA_LEVEL);
        assertTrue(valley.head()[valley.exposedStations() - 1] <= SEA_LEVEL + 1);
        assertEquals(SEA_LEVEL, valley.head()[valley.exposedStations()]);
        assertEquals(SEA_LEVEL, valley.head()[299]);
        for (int station = 0; station < valley.exposedStations(); station++) {
            assertTrue(valley.head()[station] >= SEA_LEVEL);
        }
        assertContained(valley);
    }

    @Test
    public void dryLandAtSeaLevelRemainsPartOfTheExposedCourse() {
        ValleyProfile valley = solve(300, (x, z) -> x >= 200 ? SEA_LEVEL : 90, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(valley.rejection());
        assertEquals(300, valley.exposedStations());
        assertEquals(SEA_LEVEL, valley.head()[valley.exposedStations() - 1]);
        assertEquals(90, valley.head()[100]);
        for (int station = 200; station < valley.exposedStations(); station++) {
            assertEquals(SEA_LEVEL, valley.head()[station]);
        }
    }

    @Test
    public void aCliffBecomesASingleLargeDrop() {
        ValleyProfile valley = solve(300, (x, z) -> x < 150 ? 100 : 90, SurfaceTerminal.SINKHOLE, 40);

        assertNull(valley.rejection());
        int largest = 0;
        for (int station = 1; station < valley.exposedStations(); station++) {
            largest = Math.max(largest, valley.head()[station - 1] - valley.head()[station]);
        }
        assertEquals(10, largest);
        assertEquals(100, valley.head()[100]);
        assertEquals(90, valley.head()[200]);
    }

    @Test
    public void aTwoBlockTerraceBoundsTheRoundedWetPerimeterBeforeTheNose() {
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(x < 150 ? 100 : 98, 0D, "land");
        SurfaceCenterline centerline = straight(300);
        ChannelProfile channel = channel(300, terrain);
        ValleyProfile valley = solver(terrain).solve(centerline, channel, SurfaceTerminal.TRIBUTARY, 40);

        assertNull(valley.rejection());
        assertEquals(100, valley.head()[130]);
        assertEquals(98, valley.head()[149]);
        ErosionField field = new ErosionFieldCompiler(HydrologyPlannerSettings.defaults().surface(), terrain, SEA_LEVEL)
                .compile(77L, centerline, channel, valley, SurfaceTerminal.TRIBUTARY, 0,
                        HydrologyPlannerSettings.Ponds.none());
        assertEquals(0, field.uncontainedWetCells());
        for (SurfaceColumn column : field.columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL) {
                assertTrue(column.height() <= column.terrain().naturalHeight());
            }
        }
    }

    @Test
    public void aRidgeExitUsesTheIncomingWaterHeadToBoundItsLowPerimeter() {
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                x < 96 ? 120 : x <= 128 ? 152 : 118, 0D, "land");
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), 64, defaults.shoreWidth(), defaults.banks());
        SurfaceCenterline centerline = straight(300);
        ChannelProfile channel = channel(300, terrain);
        ValleyProfile valley = new ValleyProfileSolver(surface, terrain, SEA_LEVEL, 64)
                .solve(centerline, channel, SurfaceTerminal.TRIBUTARY, 40);

        assertNull(valley.rejection());
        assertEquals(120, valley.head()[110]);
        assertEquals(118, valley.head()[128]);
        assertContained(valley);
        ErosionField field = new ErosionFieldCompiler(surface, terrain, SEA_LEVEL)
                .compile(77L, centerline, channel, valley, SurfaceTerminal.TRIBUTARY, 0,
                        HydrologyPlannerSettings.Ponds.none());
        assertEquals(0, field.uncontainedWetCells());
    }

    @Test
    public void aCoastalGrottoEndsTheExposedCourseBeforeItsRoundedCapReachesOcean() {
        HydrologyTerrainSampler terrain = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean") : HydrologyTerrainSample.openLand(92, 0D, "land");
        SurfaceCenterline centerline = straight(240);
        ChannelProfile channel = channel(240, terrain);
        ValleyProfile valley = solver(terrain).solve(centerline, channel, SurfaceTerminal.COASTAL_GROTTO, SEA_LEVEL);

        assertNull(valley.rejection());
        assertTrue(valley.exposedStations() < 240);
        assertEquals(92, valley.head()[valley.exposedStations() - 1]);
        ErosionField field = new ErosionFieldCompiler(HydrologyPlannerSettings.defaults().surface(), terrain, SEA_LEVEL)
                .compile(77L, centerline, channel, valley, SurfaceTerminal.COASTAL_GROTTO, 0,
                        HydrologyPlannerSettings.Ponds.none());
        assertEquals(0, field.uncontainedWetCells());
        for (SurfaceColumn column : field.columns().values()) {
            assertTrue(column.x() < 240);
            assertTrue(column.height() <= column.terrain().naturalHeight());
        }
    }

    @Test
    public void submergedCourseIsRejectedAsTooShort() {
        ValleyProfile valley = solve(300, (x, z) -> 50, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(HydrologyCandidateRejection.COURSE_TOO_SHORT, valley.rejection());
    }

    @Test
    public void sinkholeClearanceIsRequiredAtTheTerminal() {
        ValleyProfile valley = solve(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 90);

        assertEquals(HydrologyCandidateRejection.SURFACE_SINKHOLE_CLEARANCE, valley.rejection());
    }

    @Test
    public void excessiveIncisionIsRejected() {
        ValleyProfile valley = solve(300, (x, z) -> 90 - Math.min(20, Math.abs(z) * 8), SurfaceTerminal.SINKHOLE, 40);

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, valley.rejection());
    }

    @Test
    public void anInletHoldsTheLastStationsAtSeaLevelAndMayCutDeeperThanTheChannelCap() {
        HydrologyTerrainSampler coast = risingCoast();
        HydrologyPlannerSettings.Inlet inlet = HydrologyPlannerSettings.Inlet.of(32, 3, 32);

        ValleyProfile valley = solver(coast, inlet).solve(straight(300), channel(300, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        ValleyProfile plain = solver(coast, HydrologyPlannerSettings.Inlet.none())
                .solve(straight(300), channel(300, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(valley.rejection());
        assertEquals(240, valley.exposedStations());
        for (int station = 240 - inlet.length(); station < 240; station++) {
            assertEquals(SEA_LEVEL, valley.head()[station]);
        }
        int deepestCut = 0;
        for (int station = 0; station < valley.exposedStations(); station++) {
            deepestCut = Math.max(deepestCut, valley.centerNatural()[station] - valley.head()[station]);
        }
        assertTrue("deepest " + deepestCut, deepestCut > 10);
        assertContained(valley);
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, plain.rejection());
        assertTrue(plain.rejectionDetail() > 10);
    }

    @Test
    public void theInletApproachGradesDownOneBlockPerStation() {
        HydrologyTerrainSampler plateau = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(88, 0D, "land");

        ValleyProfile valley = solver(plateau, HydrologyPlannerSettings.Inlet.of(32, 3, 32))
                .solve(straight(300), channel(300, plateau), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        ValleyProfile shallow = solver(plateau, HydrologyPlannerSettings.Inlet.of(32, 3, 24))
                .solve(straight(300), channel(300, plateau), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(valley.rejection());
        int inletStart = 240 - 32;
        assertEquals(88, valley.head()[inletStart - 17]);
        for (int station = inletStart - 16; station < inletStart; station++) {
            assertEquals(SEA_LEVEL + (inletStart - station), valley.head()[station]);
        }
        assertEquals(SEA_LEVEL, valley.head()[inletStart]);
        assertContained(valley);
        // A cap the plateau cannot be cut through keeps the plain crossing: no inlet, no ramp, the
        // river still reaches the sea and drops into it at the coast.
        assertNull(shallow.rejection());
        assertEquals(88, valley.head()[0]);
        for (int station = 0; station < shallow.exposedStations(); station++) {
            assertEquals(88, shallow.head()[station]);
        }
        assertEquals(SEA_LEVEL, shallow.head()[shallow.exposedStations()]);
    }

    @Test
    public void theInletStopsWhereTheCoastRisesBeyondItsCap() {
        HydrologyTerrainSampler bluff = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(x >= 220 ? 66 + (x - 220) / 5 : 110, 0D, "land");

        ValleyProfile valley = solver(bluff, HydrologyPlannerSettings.Inlet.of(64, 3, 32))
                .solve(straight(300), channel(300, bluff), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(valley.rejection());
        assertEquals(240, valley.exposedStations());
        for (int station = 220; station < 240; station++) {
            assertEquals(SEA_LEVEL, valley.head()[station]);
        }
        assertEquals(110, valley.head()[219]);
        assertEquals(110, valley.head()[100]);
        assertContained(valley);
    }

    @Test
    public void upstreamStationsKeepTheChannelCap() {
        HydrologyTerrainSampler ridge = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(x >= 30 && x < 50 ? 94 : 80, 0D, "land");

        ValleyProfile valley = solver(ridge, HydrologyPlannerSettings.Inlet.of(32, 3, 32))
                .solve(straight(300), channel(300, ridge), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, valley.rejection());
        assertTrue(valley.rejectionDetail() > 10);
    }

    @Test
    public void theInletNeverTakesMoreThanHalfOfAShortCourse() {
        HydrologyTerrainSampler coast = (int x, int z) -> x >= 80
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(70, 0D, "land");

        ValleyProfile valley = solver(coast, HydrologyPlannerSettings.Inlet.of(64, 3, 32))
                .solve(straight(100), channel(100, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(valley.rejection());
        assertEquals(80, valley.exposedStations());
        for (int station = 40; station < 80; station++) {
            assertEquals(SEA_LEVEL, valley.head()[station]);
        }
        assertEquals(SEA_LEVEL + 1, valley.head()[39]);
        assertEquals(SEA_LEVEL + 10, valley.head()[30]);
        for (int station = 0; station < 20; station++) {
            assertEquals(70, valley.head()[station]);
        }
        assertContained(valley);
    }

    @Test
    public void inletCourseFractionBoundsTheDrownedReach() {
        HydrologyTerrainSampler coast = longCoast();
        ValleyProfile tenth = solver(coast, new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.1D, 1D))
                .solve(straight(340), channel(340, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        ValleyProfile half = solver(coast, new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, 1D))
                .solve(straight(340), channel(340, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(tenth.rejection());
        assertNull(half.rejection());
        assertEquals(300, tenth.exposedStations());
        assertEquals(30, drownedStations(tenth));
        assertEquals(64, drownedStations(half));
        assertEquals(SEA_LEVEL, tenth.head()[270]);
        assertTrue(tenth.head()[269] > SEA_LEVEL);
        assertContained(tenth);
    }

    @Test
    public void inletRampSlopeGradesTheApproachFaster() {
        HydrologyTerrainSampler coast = longCoast();
        ValleyProfile gentle = solver(coast, new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, 1D))
                .solve(straight(340), channel(340, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        ValleyProfile steep = solver(coast, new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, 2D))
                .solve(straight(340), channel(340, coast), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertNull(gentle.rejection());
        assertNull(steep.rejection());
        assertEquals(SEA_LEVEL, gentle.head()[236]);
        assertEquals(SEA_LEVEL, steep.head()[236]);
        assertEquals(SEA_LEVEL + 1, gentle.head()[235]);
        assertEquals(SEA_LEVEL + 2, steep.head()[235]);
        assertEquals(SEA_LEVEL + 5, gentle.head()[231]);
        assertEquals(70, steep.head()[231]);
        assertEquals(70, gentle.head()[226]);
        assertContained(gentle);
        assertContained(steep);
    }

    /** Flat land at 70 out to the last land station, x=299, with open sea from x=300. */
    private static HydrologyTerrainSampler longCoast() {
        return (int x, int z) -> x >= 300
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(70, 0D, "land");
    }

    private static int drownedStations(ValleyProfile valley) {
        int drowned = 0;
        for (int station = 0; station < valley.exposedStations(); station++) {
            if (valley.head()[station] == SEA_LEVEL) {
                drowned++;
            }
        }
        return drowned;
    }

    /** Land falling gently to 66 at x=200, then a coast rising to 83 over the last forty blocks before the sea at x=240. */
    private static HydrologyTerrainSampler risingCoast() {
        return (int x, int z) -> {
            if (x >= 240) {
                return HydrologyTerrainSample.ocean(50, "ocean");
            }
            int height = x < 200 ? 100 - x / 6 : 66 + (x - 200) * 18 / 40;
            return HydrologyTerrainSample.openLand(height, 0D, "land");
        };
    }

    private static ValleyProfileSolver solver(HydrologyTerrainSampler sampler, HydrologyPlannerSettings.Inlet inlet) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Banks banks = defaults.banks();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(),
                HydrologyPlannerSettings.Banks.of(banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(),
                        banks.roughness(), banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(),
                        banks.mouthFlareRatio(), inlet, banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(),
                        banks.erosion(), banks.ponds()));
        return new ValleyProfileSolver(surface, sampler, SEA_LEVEL, 64);
    }

    private static void assertContained(ValleyProfile valley) {
        for (int station = 0; station < valley.exposedStations(); station++) {
            assertTrue(valley.head()[station] <= valley.crossMin()[station]);
            if (station > 0) {
                assertTrue(valley.head()[station] <= valley.head()[station - 1]);
            }
        }
    }

    private static ValleyProfile solve(int stations, IntBinaryOperator height, SurfaceTerminal terminal, int terminalHead) {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(height.applyAsInt(x, z), 0D, "land");
        return solver(sampler).solve(straight(stations), channel(stations, sampler), terminal, terminalHead);
    }

    private static ValleyProfileSolver solver(HydrologyTerrainSampler sampler) {
        return new ValleyProfileSolver(HydrologyPlannerSettings.defaults().surface(), sampler, SEA_LEVEL, 64);
    }

    private static ValleyProfileSolver solver(HydrologyTerrainSampler sampler, int sink) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Banks banks = defaults.banks();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(),
                HydrologyPlannerSettings.Banks.of(sink, banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(),
                        banks.roughness(), banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(),
                        banks.mouthFlareRatio(), HydrologyPlannerSettings.Inlet.none(), banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(),
                        banks.erosion(), banks.ponds()));
        return new ValleyProfileSolver(surface, sampler, SEA_LEVEL, 64);
    }

    private static ChannelProfile channel(int stations, HydrologyTerrainSampler sampler) {
        return new ChannelProfileBuilder(HydrologyPlannerSettings.defaults().surface(), sampler, CONSTANT_GEOMETRY)
                .build(straight(stations), "water", false);
    }

    private static SurfaceCenterline straight(int stations) {
        return SurfaceCenterline.densify(path(stations));
    }

    private static List<HydrologyPoint> path(int stations) {
        return List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(stations - 1, 0, 0));
    }
}
