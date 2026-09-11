package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyOceanReceiverTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyTerrainSample LAND = HydrologyTerrainSample.openLand(70, 0D, "coast");
    private static final HydrologyTerrainSample FLOODED = HydrologyTerrainSample.openLand(60, 0D, "coast");
    private static final HydrologyTerrainSample OCEAN = HydrologyTerrainSample.ocean(60, "ocean");

    @Test
    public void connectedFloodedCoastIsAReceiverWithoutChangingRawTerrain() {
        HydrologyTerrainSampler source = coast();
        HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forOutlet(SETTINGS, source, outlet());

        for (int x = 1; x < 8; x++) {
            assertTrue(scoped.receivingWater(x, 0, 63));
            assertSame(FLOODED, scoped.sample(x, 0));
            assertFalse(scoped.sample(x, 0).ocean());
            assertFalse(source.receivingWater(x, 0, 63));
        }
        assertTrue(scoped.receivingWater(3, 1, 63));
        assertFalse(scoped.receivingWater(0, 0, 63));
        assertFalse(scoped.receivingWater(3, 0, 64));
    }

    @Test
    public void isolatedFloodedBasinIsNotPromotedInsideTheSameHalo() {
        HydrologyTerrainSampler source = (x, z) -> x == 3 && z == 5 ? FLOODED : coast().sample(x, z);
        HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forOutlet(SETTINGS, source, outlet());

        assertTrue(scoped.receivingWater(3, 1, 63));
        assertFalse(scoped.receivingWater(3, 5, 63));
        assertSame(FLOODED, scoped.sample(3, 5));
    }

    @Test
    public void aDrySillBreaksTheClaimedNaturalConnection() {
        HydrologyTerrainSampler source = (x, z) -> x == 4 ? LAND : coast().sample(x, z);
        HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forOutlet(SETTINGS, source, outlet());

        assertFalse(scoped.receivingWater(3, 0, 63));
        assertFalse(scoped.receivingWater(5, 0, 63));
        assertTrue(scoped.receivingWater(8, 0, 63));
    }

    @Test
    public void aFloodedEndpointWithoutKnownSeaDoesNotCertifyTheRun() {
        HydrologyTerrainSampler source = (x, z) -> x == 0 ? LAND : FLOODED;
        HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forOutlet(SETTINGS, source, outlet());

        assertSame(source, scoped);
        assertFalse(scoped.receivingWater(8, 0, 63));
    }

    @Test
    public void diagonalWetSamplesRequireACardinalWaterBridge() {
        RiverCourse course = course(List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(3, 63, 3)), 8);
        HydrologyTerrainSampler diagonal = (x, z) -> x == 3 && z == 3 ? OCEAN
                : x == z && x > 0 ? FLOODED : LAND;
        assertFalse(HydrologyOceanReceiver.forCourse(SETTINGS, diagonal, course).receivingWater(1, 1, 63));

        HydrologyTerrainSampler bridged = (x, z) -> x == 1 && z == 2 || x == 2 && z == 3
                ? FLOODED : diagonal.sample(x, z);
        assertTrue(HydrologyOceanReceiver.forCourse(SETTINGS, bridged, course).receivingWater(1, 1, 63));
    }

    @Test
    public void overlappingCourseViewsKeepIdenticalTerrainMetadata() {
        HydrologyTerrainSampler source = coast();
        HydrologyTerrainSampler first = HydrologyOceanReceiver.forCourse(SETTINGS, source,
                course(List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(8, 63, 0)), 8));
        HydrologyTerrainSampler second = HydrologyOceanReceiver.forCourse(SETTINGS, source,
                course(List.of(new HydrologyPoint(0, 63, 1), new HydrologyPoint(8, 63, 1)), 4));
        HydrologyColumnSample firstColumn = new FootprintMutableColumn(3, 0, first.sample(3, 0), 63).build();
        HydrologyColumnSample secondColumn = new FootprintMutableColumn(3, 0, second.sample(3, 0), 63).build();

        HydrologyFootprintCompiler.validateMatchingTerrainMetadata(firstColumn, secondColumn);
        assertEquals(firstColumn, secondColumn);
        assertFalse(firstColumn.ocean());
        for (int x = 7; x > 0; x--) {
            assertEquals(first.receivingWater(x, 0, 63), second.receivingWater(x, 0, 63));
        }
    }

    @Test
    public void receiverSamplingHasAFixedBudgetEvenForAWideMouth() {
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler source = (x, z) -> {
            samples.incrementAndGet();
            return x == 8 && z == 0 ? OCEAN : x <= 0 ? LAND : FLOODED;
        };
        HydrologyOceanReceiver.forCourse(SETTINGS, source,
                course(List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(8, 63, 0)), 1000));

        assertTrue(samples.get() <= 65_538);
        assertTrue(samples.get() >= 65_536);
    }

    @Test
    public void aMouthCannotBorrowAnotherMouthsOceanWitness() {
        HydrologyTerrainSampler source = coast();
        HydraulicSegment proven = course(List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(8, 63, 0)), 8)
                .segments().getFirst();
        HydraulicSegment unproven = course(List.of(new HydrologyPoint(0, 63, 1), new HydrologyPoint(7, 63, 1)), 8)
                .segments().getFirst();
        HydrologyTerrainSampler first = HydrologyOceanReceiver.forMouth(SETTINGS, source, proven);
        HydrologyTerrainSampler second = HydrologyOceanReceiver.forMouth(SETTINGS, source, unproven);

        assertTrue(first.receivingWater(7, 1, 63));
        assertFalse(second.receivingWater(7, 1, 63));
        assertSame(source, second);
        assertSame(first.sample(7, 1), second.sample(7, 1));
    }

    @Test
    public void anIncomingCoastalMouthReversesOnlyItsProofDirection() {
        HydraulicSegment incoming = course(List.of(new HydrologyPoint(8, 63, 0), new HydrologyPoint(0, 63, 0)), 8)
                .segments().getFirst();
        HydrologyTerrainSampler receiver = HydrologyOceanReceiver.forMouth(SETTINGS, coast(), incoming);

        assertTrue(receiver.receivingWater(1, 0, 63));
        assertSame(FLOODED, receiver.sample(1, 0));
        assertEquals(new HydrologyPoint(8, 63, 0), incoming.start());
        assertEquals(new HydrologyPoint(0, 63, 0), incoming.end());
    }

    private static HydrologyTerrainSampler coast() {
        return (x, z) -> Math.abs(z) > 1 || x <= 0 ? LAND : x >= 8 ? OCEAN : FLOODED;
    }

    private static RiverOutlet outlet() {
        return new RiverOutlet(1L, HydrologyFeatureType.MOUTH, 2L,
                new HydrologyPoint(0, 63, 0), new HydrologyPoint(8, 63, 0), 63, true);
    }

    private static RiverCourse course(List<HydrologyPoint> points, int width) {
        HydraulicSegment mouth = new HydraulicSegment(3L, 4L, HydrologyFeatureType.MOUTH,
                63, 63, width, 3, false, false, points, HydraulicChannelProfile.uniform(width, 3));
        return new RiverCourse(4L, RiverCourseType.SURFACE, OptionalLong.of(2L), OptionalLong.of(1L),
                "water", 1, List.of(), List.of(mouth));
    }
}
