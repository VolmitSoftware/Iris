package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfluenceIncisionTest {
    private static final long SEED = 1337L;
    private static final int DEEP_CUT = 12;
    private static final double WALL_FOOT = 16D;
    private static final double WALL_RUN = 15D;
    private static final double WALL_HEIGHT = 60D;
    private static final double WALL_FACE_REACH = 24D;
    private static final double MAX_WALL_CUT = 29D;
    private static final int MAX_COPY_DIFFERENCE = 4;
    private static PlannedScene valley;

    @AfterClass
    public static void dropScene() {
        valley = null;
    }

    @Test
    public void junctionPoolsNeverTrenchASlopingValley() {
        PlannedScene scene = valley();
        int points = 0;
        int deep = 0;
        String worst = "";
        double worstCut = 0D;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                if (inPond(scene.ponds(), reach.xs()[point], reach.zs()[point])) {
                    continue;
                }
                points++;
                double cut = ground(scene, reach, point) - reach.grade()[point];
                deep += cut > DEEP_CUT ? 1 : 0;
                if (cut > worstCut) {
                    worstCut = cut;
                    worst = reach + " point " + point + " cut " + cut;
                }
            }
        }
        assertTrue("reach points checked " + points, points > 10000);
        assertEquals("reach points cut more than " + DEEP_CUT + " below the valley floor, deepest " + worst, 0, deep);
    }

    @Test
    public void slopingValleyConfluencesHaveNoTongues() {
        assertEquals("confluence tongues on the sloping valley over 2x2 windows", List.of(), WaterInvariants.confluenceTongues(valley()));
    }

    @Test
    public void majorDownAWallBesideTheValleyRiverFollowsTheWall() {
        SyntheticTerrain terrain = SyntheticTerrain.valleyBelowAWall(WALL_FOOT, WALL_RUN, WALL_HEIGHT);
        HydrologySettings settings = settings();
        PlannedScene scene = PlannedScene.plan(terrain, settings, 2, 2);
        List<Reach> face = new ArrayList<>();
        for (Reach reach : scene.reaches()) {
            if (reach.tier().equals(settings.majorTier()) && onWallFace(reach)) {
                face.add(reach);
            }
        }
        assertEquals("majors running down the wall face " + face, 1, face.size());
        Reach major = face.get(0);
        double cut = 0D;
        int largest = 0;
        for (int point = 0; point < major.grade().length; point++) {
            if (!inPond(scene.ponds(), major.xs()[point], major.zs()[point])) {
                cut = Math.max(cut, ground(scene, major, point) - major.grade()[point]);
            }
            if (point > 0) {
                largest = Math.max(largest, major.grade()[point - 1] - major.grade()[point]);
            }
        }
        assertTrue(major + " is cut " + cut + " into the wall", cut <= MAX_WALL_CUT);
        double tallestFall = WALL_HEIGHT / 2D + major.tier().waterfallMinDrop();
        assertTrue(major + " falls " + largest + " blocks at once down a " + WALL_HEIGHT + "-block wall", largest <= tallestFall);
    }

    @Test
    public void neighbouringWindowsGradeTheirSharedReachesAlike() {
        SyntheticTerrain terrain = SyntheticTerrain.valleySlope();
        HydrologySettings settings = settings();
        RegionPlanner regionPlanner = new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20), new PondPlanner(settings), new ReachShaper(settings));
        WindowPlanner windowPlanner = new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21),
                new PondPlanner(settings), new ReachShaper(settings));
        Map<RegionKey, RegionPlan> regions = new HashMap<>();
        WindowKey west = new WindowKey(0, 0);
        WindowKey east = new WindowKey(1, 0);
        Map<Long, Reach> seenFromWest = byId(windowPlanner.compute(west, regionPlans(regionPlanner, regions, west)).allReaches());
        Map<Long, Reach> seenFromEast = byId(windowPlanner.compute(east, regionPlans(regionPlanner, regions, east)).allReaches());
        int copies = 0;
        int worst = 0;
        String worstCopy = "";
        for (Map.Entry<Long, Reach> entry : seenFromWest.entrySet()) {
            Reach fromWest = entry.getValue();
            Reach fromEast = seenFromEast.get(entry.getKey());
            if (fromEast == null || !Arrays.equals(fromWest.xs(), fromEast.xs()) || !Arrays.equals(fromWest.zs(), fromEast.zs())) {
                continue;
            }
            copies++;
            for (int point = 0; point < fromWest.grade().length; point++) {
                int difference = Math.abs(fromWest.grade()[point] - fromEast.grade()[point]);
                if (difference > worst) {
                    worst = difference;
                    worstCopy = fromWest + " point " + point + " west " + fromWest.grade()[point] + " east " + fromEast.grade()[point];
                }
            }
        }
        assertTrue("reaches both windows shape alike " + copies, copies > 300);
        assertTrue("grades of one reach differ by " + worst + " between the windows that plan it: " + worstCopy, worst <= MAX_COPY_DIFFERENCE);
    }

    private static synchronized PlannedScene valley() {
        if (valley == null) {
            valley = PlannedScene.plan(SyntheticTerrain.valleySlope(), settings(), 2, 2);
        }
        return valley;
    }

    private static HydrologySettings settings() {
        return HydrologySettings.compile(new IrisHydrology(), SEED, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static boolean onWallFace(Reach reach) {
        double top = SyntheticTerrain.VALLEY_AXIS - WALL_FOOT - WALL_RUN;
        double foot = SyntheticTerrain.VALLEY_AXIS - WALL_FOOT;
        for (int point = 0; point < reach.xs().length; point++) {
            double z = reach.zs()[point];
            if (Math.abs(reach.xs()[point] - SyntheticTerrain.VALLEY_AXIS) < WALL_FACE_REACH && z > top && z < foot) {
                return true;
            }
        }
        return false;
    }

    private static double ground(PlannedScene scene, Reach reach, int point) {
        return scene.natural((int) Math.floor(reach.xs()[point]), (int) Math.floor(reach.zs()[point]));
    }

    private static boolean inPond(List<Pond> ponds, double x, double z) {
        for (Pond pond : ponds) {
            if (pond.mask().contains((int) Math.floor(x), (int) Math.floor(z))) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, Reach> byId(List<Reach> reaches) {
        Map<Long, Reach> byId = new HashMap<>(reaches.size() * 2);
        for (Reach reach : reaches) {
            byId.put(reach.id(), reach);
        }
        return byId;
    }

    private static List<RegionPlan> regionPlans(RegionPlanner planner, Map<RegionKey, RegionPlan> cache, WindowKey key) {
        List<RegionPlan> plans = new ArrayList<>();
        for (RegionKey region : WindowPlanner.lakeRegions(key)) {
            plans.add(cache.computeIfAbsent(region, planner::plan));
        }
        return plans;
    }
}
