package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerSurfacePoolTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);

    @Test
    public void poolsAreCarvedWhereThePolicyAllowsThemAndNowhereElse() {
        HydrologyPlannerSettings settings = settings(2D, 128, 4, 6, 2);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land")
                .withSurfacePoolKeys(x < 512 ? List.of("lava_pool") : List.of());

        HydrologyTile tile = new HydrologyPlanner(91L, settings, terrain).plan(TILE);
        HydrologyTile replay = new HydrologyPlanner(91L, settings, terrain).plan(TILE);

        assertEquals(tile, replay);
        List<RiverCourse> pools = pools(tile);
        assertFalse(tile.diagnosticCandidates().toString(), pools.isEmpty());
        for (RiverCourse pool : pools) {
            assertEquals("lava_pool", pool.profileKey());
            assertEquals(1, pool.segments().size());
            HydraulicSegment segment = pool.segments().getFirst();
            assertEquals(HydrologyFeatureType.STANDING_POOL, segment.type());
            assertTrue(segment.width() >= 8 && segment.width() <= 12);
            assertTrue(segment.start().x() < 512);
            assertEquals(80, segment.upstreamHeadY());
            assertEquals(segment.upstreamHeadY(), segment.downstreamHeadY());
        }
    }

    @Test
    public void aPoolIsABowlWithALipInsideTheFootprint() {
        HydrologyPlannerSettings settings = settings(2D, 256, 5, 5, 2);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land")
                .withSurfacePoolKeys(List.of("lava_pool"));

        HydrologyTile tile = new HydrologyPlanner(7L, settings, terrain).plan(TILE);
        RiverCourse pool = pools(tile).getFirst();
        HydrologyPoint center = pool.segments().getFirst().centerline().get(1);

        int wet = 0;
        int shore = 0;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            HydrologyColumnLayer layer = column.primarySurfaceLayer().orElse(null);
            if (layer == null || layer.feature().courseId() != pool.id()) {
                continue;
            }
            assertEquals(HydrologyFeatureType.STANDING_POOL, layer.feature().type());
            assertTrue(column.terrainHeight() <= column.naturalHeight());
            if (layer.channel()) {
                wet++;
                assertEquals("lava_pool", layer.profileKey());
                assertEquals(80, layer.fluidHeadY());
                assertTrue(layer.bedY() < layer.fluidHeadY());
                assertTrue(center.distanceSquared2D(new HydrologyPoint(column.x(), 0, column.z())) <= 8 * 8);
            } else if (layer.shore()) {
                shore++;
                assertTrue(column.terrainHeight() >= 80);
            }
        }
        assertTrue("wet=" + wet, wet >= 60 && wet <= 140);
        assertTrue("shore=" + shore, shore > 0);
    }

    @Test
    public void poolsKeepClearOfEachOther() {
        HydrologyPlannerSettings settings = settings(8D, 64, 6, 6, 2);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land")
                .withSurfacePoolKeys(List.of("lava_pool"));

        HydrologyTile tile = new HydrologyPlanner(3L, settings, terrain).plan(TILE);
        List<RiverCourse> pools = pools(tile);

        assertTrue(pools.size() >= 2);
        for (int first = 0; first < pools.size(); first++) {
            for (int second = first + 1; second < pools.size(); second++) {
                HydrologyPoint a = pools.get(first).segments().getFirst().centerline().get(1);
                HydrologyPoint b = pools.get(second).segments().getFirst().centerline().get(1);
                assertTrue(a.distanceSquared2D(b) >= 40L * 40L);
            }
        }
    }

    private static List<RiverCourse> pools(HydrologyTile tile) {
        ArrayList<RiverCourse> pools = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SURFACE_POOL) {
                pools.add(course);
            }
        }
        return pools;
    }

    private static HydrologyPlannerSettings settings(double density, int spacing, int minimumRadius, int maximumRadius, int depth) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Routing routing = new HydrologyPlannerSettings.Routing(
                1024, 64, base.routing().maximumRouteNodes(), 4096, 384, 192, 1.5D, 24D, 2D, 0.2D, 1D, 0, HydrologyPlannerSettings.Regional.disabled());
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                routing,
                base.surface(),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                base.geometry(),
                List.of(),
                List.of(new HydrologyPlannerSettings.SurfacePool(
                        "lava_pool", true, density, spacing, minimumRadius, maximumRadius, depth, 64, null)),
                        0D,
                        HydrologyPlannerSettings.SeaCaves.disabled(),
                        HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }
}
