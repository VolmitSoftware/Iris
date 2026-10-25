package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeaInletPlannerTest {
    private static final int SEA = InletTerrain.SEA;

    @Test
    public void inletStartsAtOceanCell() {
        InletTerrain terrain = InletTerrain.cliff(30D);
        List<SeaInlet> inlets = terrain.plan(InletTerrain.settings());
        assertFalse("a straight cliff coast holds inlets", inlets.isEmpty());
        for (SeaInlet inlet : inlets) {
            assertTrue(inlet + " opens from an ocean cell", terrain.ocean(inlet.xs()[0], inlet.zs()[0]));
            assertFalse(inlet + " runs inland", terrain.ocean(inlet.xs()[1], inlet.zs()[1]));
            for (int station = 1; station < inlet.stations(); station++) {
                assertTrue(inlet + " climbs away from the sea", inlet.xs()[station] > inlet.xs()[station - 1]);
            }
        }
    }

    @Test
    public void waterLevelIsSeaLevelEverywhere() {
        InletTerrain terrain = InletTerrain.cliff(30D);
        HydrologySettings settings = InletTerrain.settings();
        HydrologySettings.SeaInlets config = settings.underground().seaInlets();
        List<SeaInlet> inlets = terrain.plan(settings);
        assertFalse(inlets.isEmpty());
        for (SeaInlet inlet : inlets) {
            assertEquals(SEA, inlet.waterLevel());
            for (int station = 0; station < inlet.stations(); station++) {
                int depth = SEA - inlet.floor()[station];
                assertTrue(inlet + " depth " + depth, depth >= config.depth().min() && depth <= config.depth().max());
                assertTrue(inlet + " keeps air or an open mouth above the water", inlet.ceiling()[station] >= SEA);
            }
        }
    }

    @Test
    public void roofClearanceHeld() {
        HydrologySettings settings = InletTerrain.settings();
        int clearance = settings.underground().seaInlets().roofClearance();
        double troughFrom = 300D;
        InletTerrain terrain = InletTerrain.cliffWithTrough(30D, troughFrom, 340D, SEA + 9D);
        List<SeaInlet> inlets = terrain.plan(settings);
        assertFalse(inlets.isEmpty());
        int covered = 0;
        int[] column = new int[2];
        for (SeaInlet inlet : inlets) {
            int[] bounds = SeaInletRasterizer.bounds(inlet);
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                for (int x = bounds[0]; x <= bounds[2]; x++) {
                    if (!SeaInletRasterizer.column(inlet, x, z, column)) {
                        continue;
                    }
                    int ground = terrain.naturalAt(x, z);
                    assertTrue(inlet + " at " + x + "," + z + " stays clear of the trough", x < troughFrom - clearance);
                    if (column[1] == SeaInlet.OPEN) {
                        assertTrue(inlet + " open slot at " + x + "," + z + " stays at the coast", x < InletTerrain.COAST_X + 3 * InletTerrain.CELL);
                    } else {
                        covered++;
                        assertTrue(inlet + " roof at " + x + "," + z + " is " + (ground - column[1]), ground - column[1] >= clearance);
                    }
                }
            }
        }
        assertTrue("covered columns " + covered, covered > 200);
    }

    @Test
    public void openSlotHoldsAtMostTwoLandStationsFromTheSea() {
        for (InletTerrain terrain : List.of(InletTerrain.cliff(30D), InletTerrain.cliff(9D), InletTerrain.pocket(152, 256))) {
            for (SeaInlet inlet : terrain.plan(InletTerrain.settings())) {
                assertTrue(inlet + " opens at the sea", inlet.open(0));
                int open = 1;
                while (open < inlet.stations() && inlet.open(open)) {
                    open++;
                }
                assertTrue(inlet + " open land stations " + (open - 1), open - 1 <= 2);
                for (int station = open; station < inlet.stations(); station++) {
                    assertFalse(inlet + " stays covered after the slot at station " + station, inlet.open(station));
                }
            }
        }
    }

    @Test
    public void slotsNeedingAThirdOpenLandStationAreRejected() {
        List<SeaInlet> twoOpen = InletTerrain.steppedCoast(164D).plan(InletTerrain.settings());
        assertFalse("a cliff that holds a roof two stations in keeps its inlets", twoOpen.isEmpty());
        for (SeaInlet inlet : twoOpen) {
            assertTrue(inlet.open(1) && inlet.open(2) && !inlet.open(3));
        }
        assertTrue("a slot three land stations long is refused", InletTerrain.steppedCoast(172D).plan(InletTerrain.settings()).isEmpty());
    }

    @Test
    public void ceilingFollowsSmoothNoiseAlongThePath() {
        HydrologySettings settings = InletTerrain.settings();
        int distinct = 0;
        for (SeaInlet inlet : InletTerrain.cliff(30D).plan(settings)) {
            Set<Integer> heights = new HashSet<>();
            for (int station = 1; station < inlet.stations(); station++) {
                if (inlet.open(station) || inlet.open(station - 1)) {
                    continue;
                }
                heights.add(inlet.ceiling()[station]);
                int step = Math.abs(inlet.ceiling()[station] - inlet.ceiling()[station - 1]);
                assertTrue(inlet + " ceiling steps " + step + " at station " + station, step <= 3);
            }
            distinct += heights.size() > 1 ? 1 : 0;
        }
        assertTrue("ceilings vary along the passage", distinct > 0);
    }

    @Test
    public void inletsSharingAnOceanMouthKeepDistinctIds() {
        IrisHydrology config = new IrisHydrology();
        config.getUnderground().getSeaInlets().setSpacing(32);
        List<SeaInlet> inlets = InletTerrain.pocket(152, 256).plan(InletTerrain.settings(config));
        long shared = inlets.stream().filter(inlet -> inlet.xs()[0] == 152f && inlet.zs()[0] == 256f).count();
        assertTrue("inlets opening from the one-cell pocket " + shared, shared >= 2);
        assertEquals(inlets.size(), inlets.stream().mapToLong(SeaInlet::id).distinct().count());
    }

    @Test
    public void noInletRunsCoveredUnderOrBesideAnotherInletsOpenSlot() {
        IrisHydrology dense = new IrisHydrology();
        dense.getUnderground().getSeaInlets().setSpacing(32);
        int pairs = 0;
        for (InletTerrain terrain : List.of(InletTerrain.pocket(152, 256), InletTerrain.cliff(30D), InletTerrain.steppedCoast(164))) {
            List<SeaInlet> inlets = terrain.plan(InletTerrain.settings(dense));
            for (int first = 0; first < inlets.size(); first++) {
                for (int second = first + 1; second < inlets.size(); second++) {
                    pairs++;
                    assertFalse(inlets.get(first) + " and " + inlets.get(second) + " cross a covered passage with an open slot",
                            coveredMeetsOpen(inlets.get(first), inlets.get(second)) || coveredMeetsOpen(inlets.get(second), inlets.get(first)));
                }
            }
        }
        assertTrue("inlet pairs " + pairs, pairs > 10);
    }

    @Test
    public void competingInletsResolveTheSameWayInEveryGridThatSeesBoth() {
        IrisHydrology dense = new IrisHydrology();
        dense.getUnderground().getSeaInlets().setSpacing(32);
        HydrologySettings settings = InletTerrain.settings(dense);
        InletTerrain pocket = InletTerrain.pocket(152, 256);
        List<SeaInlet> here = fromPocket(pocket.shifted(-232, 0).plan(settings));
        assertTrue("inlets opening from the pocket " + here, here.size() >= 2);
        assertEquals("another grid holding every competitor well inside decides the same", here,
                fromPocket(pocket.shifted(-200, -24).plan(settings)));
        for (int first = 0; first < here.size(); first++) {
            for (int second = first + 1; second < here.size(); second++) {
                assertFalse(here.get(first) + " and " + here.get(second) + " cross a covered passage with an open slot",
                        coveredMeetsOpen(here.get(first), here.get(second)) || coveredMeetsOpen(here.get(second), here.get(first)));
            }
        }
    }

    private static List<SeaInlet> fromPocket(List<SeaInlet> inlets) {
        return inlets.stream().filter(inlet -> inlet.xs()[0] == 152f && inlet.zs()[0] == 256f).toList();
    }

    private static boolean coveredMeetsOpen(SeaInlet covered, SeaInlet open) {
        int[] bounds = SeaInletRasterizer.bounds(open);
        int[] column = new int[2];
        int[] near = new int[2];
        for (int z = bounds[1]; z <= bounds[3]; z++) {
            for (int x = bounds[0]; x <= bounds[2]; x++) {
                if (!SeaInletRasterizer.column(open, x, z, column) || column[1] != SeaInlet.OPEN) {
                    continue;
                }
                for (int dz = -SeaInletRasterizer.SHELL; dz <= SeaInletRasterizer.SHELL; dz++) {
                    for (int dx = -SeaInletRasterizer.SHELL; dx <= SeaInletRasterizer.SHELL; dx++) {
                        if (SeaInletRasterizer.column(covered, x + dx, z + dz, near) && near[1] != SeaInlet.OPEN) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Test
    public void siteOnAWindowEdgeIsPlannedOnlyFromItsTargetWindow() {
        HydrologySettings settings = InletTerrain.settings();
        int spacing = settings.underground().seaInlets().spacing();
        int site = Math.floorDiv(WindowKey.SIZE, spacing);
        assertTrue("the site straddles the window edge", site * spacing < WindowKey.SIZE && (site + 1) * spacing > WindowKey.SIZE);
        double coastZ = 160D;
        InletTerrain terrain = InletTerrain.southCoast(coastZ);
        List<SeaInlet> open = inSite(terrain.plan(settings), spacing, site);
        assertEquals("the straddling site holds one inlet", 1, open.size());
        WindowKey owner = WindowKey.containing((int) open.getFirst().xs()[1], (int) open.getFirst().zs()[1]);
        float fromX = owner.wx() == 0 ? site * spacing - 16f : WindowKey.SIZE - 16f;
        float toX = owner.wx() == 0 ? WindowKey.SIZE + 16f : (site + 1) * spacing + 16f;
        Reach blocking = InletTerrain.eastWestReach(settings, fromX, toX, (float) coastZ + 4f, InletTerrain.SEA + 28);
        List<SeaInlet> blocked = inSite(terrain.plan(settings, new ShapedNetwork(List.of(blocking), List.of())), spacing, site);
        int doubled = 0;
        for (SeaInlet inlet : blocked) {
            doubled += WindowKey.containing((int) inlet.xs()[1], (int) inlet.zs()[1]).equals(owner) ? 0 : 1;
        }
        assertEquals("a window that cannot use the target window's coast plans nothing for the site", 0, doubled);
    }

    private static List<SeaInlet> inSite(List<SeaInlet> inlets, int spacing, int site) {
        return inlets.stream().filter(inlet -> Math.floorDiv((int) inlet.xs()[1], spacing) == site).toList();
    }

    @Test
    public void staysUnderSurfaceWaterBeds() {
        HydrologySettings settings = InletTerrain.settings();
        int clearance = settings.underground().seaInlets().roofClearance();
        InletTerrain terrain = InletTerrain.cliff(30D);
        Reach deep = InletTerrain.northSouthReach(settings, 232f, SEA + 26, 2f, 1.5f);
        List<SeaInlet> under = terrain.plan(settings, new ShapedNetwork(List.of(deep), List.of()));
        assertFalse(under.isEmpty());
        int[] column = new int[2];
        int crossings = 0;
        for (SeaInlet inlet : under) {
            for (int z = SeaInletRasterizer.bounds(inlet)[1]; z <= SeaInletRasterizer.bounds(inlet)[3]; z++) {
                if (SeaInletRasterizer.column(inlet, 232, z, column)) {
                    crossings++;
                    assertTrue(inlet + " roof under the river bed", (SEA + 26 - 1.5f) - column[1] >= clearance);
                }
            }
        }
        assertTrue("inlets pass under the deep river", crossings > 0);
        Reach shallow = InletTerrain.northSouthReach(settings, 232f, SEA + 12, 2f, 1.5f);
        for (SeaInlet inlet : terrain.plan(settings, new ShapedNetwork(List.of(shallow), List.of()))) {
            int[] bounds = SeaInletRasterizer.bounds(inlet);
            assertTrue(inlet + " stops before the low river at x=232", bounds[2] < 232 - settings.tiers().get(0).bankBand());
        }
    }

    @Test
    public void endsInChamber() {
        HydrologySettings settings = InletTerrain.settings();
        HydrologySettings.SeaInlets config = settings.underground().seaInlets();
        InletTerrain terrain = InletTerrain.cliff(30D);
        int[] column = new int[2];
        List<SeaInlet> inlets = terrain.plan(settings);
        assertFalse(inlets.isEmpty());
        for (SeaInlet inlet : inlets) {
            int last = inlet.stations() - 1;
            assertEquals(inlet.xs()[last], inlet.chamberX(), 0f);
            assertEquals(inlet.zs()[last], inlet.chamberZ(), 0f);
            assertTrue(inlet + " chamber radius", inlet.chamberRadius() >= config.chamberRadius().min()
                    && inlet.chamberRadius() <= config.chamberRadius().max());
            int reach = inlet.chamberRadius() - 1;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (dx * dx + dz * dz > reach * reach) {
                        continue;
                    }
                    assertTrue(inlet + " chamber column " + dx + "," + dz,
                            SeaInletRasterizer.column(inlet, inlet.chamberX() + dx, inlet.chamberZ() + dz, column));
                    assertTrue(column[0] < SEA);
                }
            }
            float length = InletTerrain.length(inlet);
            assertTrue(inlet + " length " + length, length >= config.length().min() && length <= config.length().max() + InletTerrain.CELL * 1.5f);
        }
    }

    @Test
    public void continuesInlandPastTheHeadOfTheCoastalCatchment() {
        double crest = 40D;
        InletTerrain terrain = InletTerrain.coastalRidge(crest);
        List<SeaInlet> inlets = terrain.plan(InletTerrain.settings());
        assertFalse("a short coastal catchment still holds inlets", inlets.isEmpty());
        for (SeaInlet inlet : inlets) {
            assertTrue(inlet + " passes under the ridge crest", inlet.xs()[inlet.stations() - 1] > InletTerrain.COAST_X + crest + InletTerrain.CELL);
            assertTrue(inlet + " length", InletTerrain.length(inlet) >= InletTerrain.settings().underground().seaInlets().length().min());
        }
    }

    @Test
    public void noInletWithoutCliff() {
        assertTrue(InletTerrain.cliff(4D).plan(InletTerrain.settings()).isEmpty());
        IrisHydrology disabled = new IrisHydrology();
        disabled.getUnderground().getSeaInlets().setEnabled(false);
        assertTrue(InletTerrain.cliff(30D).plan(InletTerrain.settings(disabled)).isEmpty());
    }

    @Test
    public void deterministic() {
        HydrologySettings settings = InletTerrain.settings();
        List<SeaInlet> first = InletTerrain.cliff(30D).plan(settings);
        List<SeaInlet> second = InletTerrain.cliff(30D).plan(InletTerrain.settings());
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().mapToLong(SeaInlet::id).distinct().count());
        InletTerrain terrain = InletTerrain.cliff(30D);
        List<SeaInlet> otherSeed = new SeaInletPlanner(HydrologySettings.compile(new IrisHydrology(), 99L, SEA, InletTerrain.MIN_Y,
                InletTerrain.MAX_Y)).plan(terrain.grid, terrain.samples, terrain.receivers, terrain.accumulation,
                new ShapedNetwork(List.of(), List.of()), 99L);
        assertNotEquals(first, otherSeed);
    }

    @Test
    public void inletsReachingPastTheNextWindowAreRejected() {
        IrisHydrology fits = new IrisHydrology();
        fits.getUnderground().getSeaInlets().setLength(new KList<>(64, 485));
        InletTerrain.settings(fits);
        IrisHydrology last = new IrisHydrology();
        last.getUnderground().getSeaInlets().setLength(new KList<>(64, 486));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> InletTerrain.settings(last));
        assertTrue(failure.getMessage(), failure.getMessage().contains("last step"));
    }
}
