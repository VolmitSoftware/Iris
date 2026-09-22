package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RiverFirstTerrainTest {
    @Test
    public void riverBuildsContainedBanksAroundALowNaturalPatch() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(77, 0D, "land");
        ErosionField field = compile(sampler, null);

        assertNull(field.rejection());
        assertEquals(0, field.uncontainedWetCells());
        SurfaceColumn bed = field.column(0, 0);
        assertNotNull(bed);
        assertEquals(bed.terrain().naturalHeight(), bed.height());
        assertTrue(bed.height() < bed.headY());
        int raisedBanks = 0;
        for (SurfaceColumn column : field.columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL && column.height() > column.terrain().naturalHeight()) {
                raisedBanks++;
                assertTrue(column.height() - column.terrain().naturalHeight() <= 8);
            }
        }
        assertTrue(raisedBanks > 0);
    }

    @Test
    public void riverRejectsRequiredFillBeyondTheTerrainBudget() {
        ErosionField field = compile((x, z) -> HydrologyTerrainSample.openLand(69, 0D, "land"), null);
        assertEquals(HydrologyCandidateRejection.SURFACE_WATER_CONTAINMENT, field.rejection());
    }

    @Test
    public void unavailableBankCannotCertifyTheWetPerimeter() {
        HydrologyTerrainSample ground = HydrologyTerrainSample.openLand(77, 0D, "land");
        ErosionField complete = compile((x, z) -> ground, null);
        assertNull(complete.rejection());
        SurfaceColumn bank = complete.columns().values().stream()
                .filter(column -> column.x() == 0 && column.role() != SurfaceRole.CHANNEL
                        && complete.column(0, column.z() - 1) != null
                        && complete.column(0, column.z() - 1).role() == SurfaceRole.CHANNEL)
                .findFirst().orElseThrow();

        ErosionField unavailable = compile((x, z) -> x == bank.x() && z == bank.z() ? null : ground, null);

        assertEquals(HydrologyCandidateRejection.SURFACE_WATER_CONTAINMENT, unavailable.rejection());
        assertTrue(unavailable.uncontainedWetCells() > 0);
    }

    @Test
    public void fineChannelIncisionLimitIsCheckedBeforePublication() {
        ErosionField field = compile((x, z) -> HydrologyTerrainSample.openLand(x == 0 && z == 1 ? 110 : 82, 0D, "land"), null);
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, field.rejection());
    }

    @Test
    public void channelEdgesRetainWaterWithinTheLocalIncisionLimit() {
        ErosionField field = compile((x, z) -> HydrologyTerrainSample.openLand(x == 0 && z == 1 ? 89 : 82, 0D, "land"), null);
        assertNull(field.rejection());
        SurfaceColumn column = field.column(0, 1);
        assertEquals(SurfaceRole.CHANNEL, column.role());
        assertEquals(79, column.height());
        assertEquals(80, column.headY());
    }

    @Test
    public void adjacentChunkRastersRetainIdenticalRaisedTerrain() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(77 + Math.floorMod(x + z, 3), 0D, "land");
        ErosionField complete = compile(sampler, null);
        assertNull(complete.rejection());
        for (SurfaceBounds bounds : List.of(new SurfaceBounds(-16, -16, -1, 15),
                new SurfaceBounds(0, -16, 15, 15))) {
            ErosionField bounded = compile(sampler, bounds);
            assertNull(bounded.rejection());
            for (SurfaceColumn column : complete.columns().values()) {
                if (bounds.contains(column.x(), column.z())) {
                    assertEquals(column, bounded.column(column.x(), column.z()));
                }
            }
        }
    }

    private static ErosionField compile(HydrologyTerrainSampler sampler, SurfaceBounds bounds) {
        HydrologyPlannerSettings.Surface settings = HydrologyPlannerSettings.defaults().surface();
        SurfaceCenterline centerline = SurfaceCenterline.densify(
                List.of(new HydrologyPoint(-48, 80, 0), new HydrologyPoint(48, 80, 0)));
        int count = centerline.size();
        double[] widths = new double[count];
        double[] depths = new double[count];
        double[] multipliers = new double[count];
        int[] heads = new int[count];
        Arrays.fill(widths, 3D);
        Arrays.fill(depths, 2D);
        Arrays.fill(multipliers, 1D);
        Arrays.fill(heads, 80);
        return new ErosionFieldCompiler(rasterSettings(settings), sampler).compile(17L, centerline,
                new ChannelProfile(widths, depths, multipliers), ValleyProfile.fromHeads(heads, count),
                SurfaceTerminal.SINKHOLE, 0, HydrologyPlannerSettings.Ponds.none(), SurfaceRasterContext.bounded(bounds));
    }
    private static HydrologyPlannerSettings rasterSettings(HydrologyPlannerSettings.Surface surface) {
        HydrologyPlannerSettings defaults = HydrologyPlannerSettings.defaults();
        return new HydrologyPlannerSettings(60, defaults.routing(), surface, defaults.hydraulics(),
                defaults.underground(), defaults.outlets(), defaults.geometry(), defaults.deepFluids(),
                defaults.surfacePools(), surface.shoreWidth(), defaults.seaCaves(), defaults.surfacePolicyBounds());
    }

}
