package art.arcane.iris.probe;

import art.arcane.iris.engine.hydrology.RiverFootprint;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RiverTransectProbeTest {
    @Test
    public void sectionStationsSitAtTenThirtyFiftySeventyAndNinetyPercent() {
        assertArrayEquals(new int[] {2, 6, 10, 14, 18}, RiverTransectProbe.sectionStations(20));
        assertArrayEquals(new int[] {0, 0, 0, 0, 0}, RiverTransectProbe.sectionStations(1));
        assertArrayEquals(new int[] {0, 0, 1, 2, 2}, RiverTransectProbe.sectionStations(3));
    }

    @Test
    public void summaryCountsCutsBankStepsOceanWritesAndSpillingChannels() {
        Map<Long, RiverTransectProbe.ColumnView> columns = new HashMap<>();
        put(columns, 0, 0, 72, 68, 70, RiverTransectProbe.Role.CHANNEL);
        put(columns, 1, 0, 72, 71, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK);
        put(columns, -1, 0, 72, 69, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK);
        put(columns, 0, 1, 72, 70, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.SHORE);
        put(columns, 0, -1, 72, 72, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.NONE);
        put(columns, 2, 0, 72, 72, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.NONE);
        put(columns, 5, 5, 60, 58, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.NONE);
        put(columns, 6, 6, 60, 60, 63, RiverTransectProbe.Role.APRON);

        RiverTransectProbe.CourseSummary summary = RiverTransectProbe.summarize(options(7L, 12), columns);

        assertEquals(7L, summary.id());
        assertEquals(12, summary.stations());
        assertEquals(4, summary.ownedColumns());
        assertEquals(1, summary.minimumCut());
        assertEquals(4, summary.maximumCut());
        assertEquals(1, summary.maximumBankStep());
        assertEquals(1, summary.oceanWrites());
        assertEquals(1, summary.uncontainedWetCells());
        assertFalse(summary.passes());
        assertEquals(3, summary.details().size());
        assertTrue(summary.details().get(0).startsWith("uncontained 0,0"));
        assertTrue(summary.details().get(1).startsWith("oceanWrite 5,5"));
        assertTrue(summary.details().get(2).startsWith("bankStep 1"));
    }

    @Test
    public void containedCourseAwayFromTheOceanPasses() {
        Map<Long, RiverTransectProbe.ColumnView> columns = new HashMap<>();
        put(columns, 0, 0, 72, 68, 70, RiverTransectProbe.Role.CHANNEL);
        put(columns, 1, 0, 72, 71, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK);
        put(columns, -1, 0, 72, 71, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK);
        put(columns, 0, 1, 72, 70, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.SHORE);
        put(columns, 0, -1, 72, 71, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.SHORE);

        RiverTransectProbe.CourseSummary summary = RiverTransectProbe.summarize(options(1L, 3), columns);

        assertTrue(summary.passes());
        assertEquals(0, summary.oceanWrites());
        assertEquals(0, summary.uncontainedWetCells());
    }

    @Test
    public void drySillMouthCutsConnectToUntouchedOceanWithoutCountingAsOceanBedWrites() {
        Map<Long, RiverTransectProbe.ColumnView> columns = new HashMap<>();
        columns.put(RiverFootprint.pack(0, 0), new RiverTransectProbe.ColumnView(
                0, 0, 63, 60, 63, RiverTransectProbe.Role.CHANNEL,
                new RiverTransectProbe.ColumnPlan(false, true, true, true, 60, 1L)));
        put(columns, 1, 0, 60, 60, 63, RiverTransectProbe.Role.APRON);
        RiverTransectProbe.CourseSummary connected = RiverTransectProbe.summarize(options(1L, 2), columns);
        assertEquals(1, connected.drySillCuts());
        assertEquals(0, connected.oceanWrites());
        assertEquals(1, connected.connectedMouthColumns());
        assertTrue(connected.passes());
        columns.remove(RiverFootprint.pack(1, 0));
        assertEquals(1, RiverTransectProbe.summarize(options(1L, 2), columns).disconnectedMouthColumns());
    }

    @Test
    public void bankCutBudgetUsesPublishedLowerFloorEvenWhenUpperTerrainSpanIsRetained() {
        Map<Long, RiverTransectProbe.ColumnView> columns = new HashMap<>();
        columns.put(RiverFootprint.pack(0, 0), new RiverTransectProbe.ColumnView(
                0, 0, 80, 80, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK,
                new RiverTransectProbe.ColumnPlan(false, true, false, false, 70, 1L)));
        RiverTransectProbe.CourseSummary summary = RiverTransectProbe.summarize(options(1L, 2), columns);
        assertEquals(10, summary.maximumBankCut());
        assertEquals(10, summary.bankExcavation());
        assertEquals(1, summary.bankBudgetViolations());
        assertFalse(summary.passes());
    }

    @Test
    public void reconstructedReachVolumesUseTheSharedSixteenBlockMetric() {
        Map<Long, RiverTransectProbe.ColumnView> columns = new HashMap<>();
        for (int x = 0; x <= 32; x++) {
            put(columns, x, 2, 80, 72, RiverTransectProbe.NO_WATER, RiverTransectProbe.Role.BANK);
        }
        assertEquals(9, RiverTransectProbe.estimatedBankVolumePerBlock(options(1L, 33), columns));
        assertEquals(0, RiverTransectProbe.estimatedBankVolumePerBlock(options(2L, 33), columns));
    }

    private static RiverTransectProbe.SummaryOptions options(long id, int stations) {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(stations - 1, 70, 0)));
        return new RiverTransectProbe.SummaryOptions(id, centerline, 63,
                new HydrologyPlannerSettings.Excavation(8, 16, 128));
    }

    private static void put(
            Map<Long, RiverTransectProbe.ColumnView> columns,
            int x,
            int z,
            int natural,
            int terrain,
            int water,
            RiverTransectProbe.Role role
    ) {
        columns.put(RiverFootprint.pack(x, z), new RiverTransectProbe.ColumnView(x, z, natural, terrain, water, role,
                new RiverTransectProbe.ColumnPlan(role == RiverTransectProbe.Role.APRON,
                        role.owned(), role == RiverTransectProbe.Role.CHANNEL, false, terrain, 1L)));
    }
}
