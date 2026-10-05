package art.arcane.iris.generation.subterrain;

import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubterrainRegionSelectionTest {
    @Test
    public void restrictedRoomsOnlyPlaceInTheirAllowedRegion() {
        IrisSubterrainFeature feature = feature().setAllowedRegions(new KList<>("hot"));
        SubterrainPlanner planner = planner(feature, (x, z) -> x < 0 ? "hot" : "frozen");
        List<SubterrainPlan> plans = planner.plansForBounds(-1024, -1024, 1024, 1024);
        assertFalse(plans.isEmpty());
        for (SubterrainPlan plan : plans) {
            assertTrue(plan.centerX() < 0);
            SubterrainPosition anchor = plan.anchor();
            assertTrue(planner.sample(anchor.x(), anchor.y(), anchor.z()).occupied());
        }
        assertTrue(planner(feature, (x, z) -> "frozen").plansForBounds(-1024, -1024, 1024, 1024).isEmpty());
    }

    @Test
    public void unrestrictedRoomsDoNotSampleRegions() {
        SubterrainPlanner planner = planner(feature(), (x, z) -> {
            throw new AssertionError("Unrestricted room queried region");
        });
        assertFalse(planner.plansForBounds(-1024, -1024, 1024, 1024).isEmpty());
    }

    @Test
    public void restrictionsSurviveSerializationSnapshotAndFootprintFiltering() {
        IrisSubterrainFeature feature = feature().setAllowedRegions(new KList<>("hot"));
        Gson gson = new Gson();
        IrisSubterrainFeature restored = gson.fromJson(gson.toJson(feature), IrisSubterrainFeature.class);
        SubterrainPlanner planner = planner(restored, (x, z) -> "hot");
        List<SubterrainPlan> expected = planner.plansForBounds(-1024, -1024, 1024, 1024);
        restored.getAllowedRegions().clear();
        restored.getAllowedRegions().add("frozen");
        SubterrainPlanner restricted = planner.restrictTo(bounds -> bounds.maxZ() < 0);
        List<SubterrainPlan> actual = restricted.plansForBounds(-1024, -1024, 1024, 1024);
        assertFalse(actual.isEmpty());
        assertEquals(expected.stream().filter(plan -> plan.bounds().maxZ() < 0).map(SubterrainPlan::id).toList(),
                actual.stream().map(SubterrainPlan::id).toList());
        assertTrue(planner(restored, (x, z) -> "hot").plansForBounds(-1024, -1024, 1024, 1024).isEmpty());
    }

    @Test
    public void invalidRegionKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> planner(feature().setAllowedRegions(new KList<>("bad region")), (x, z) -> "hot"));
    }

    private IrisSubterrainFeature feature() {
        return new IrisSubterrainFeature().setId("regional-room").setProbability(1).setSpacing(256)
                .setRadius(16).setHeight(24).setFluidDepth(0).setPillarSpacing(0).setFormationFraction(0)
                .setWorldYRange(new IrisRange(-64, 64));
    }

    private SubterrainPlanner planner(IrisSubterrainFeature feature, SubterrainPlanner.RegionResolver regions) {
        return new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 1337L, -64, 128, regions));
    }
}
