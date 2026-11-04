package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.underground.GrottoPlanner;
import art.arcane.iris.generation.hydrology.underground.GrottoShape;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.iris.generation.hydrology.water.TidalPlanner;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GrottoPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA = SyntheticTerrain.SEA_LEVEL;
    private static final double COAST_X = 300D;
    private static final ShapedNetwork NOTHING = new ShapedNetwork(List.of(), List.of());
    private static final List<WindowKey> WINDOWS = List.of(new WindowKey(0, -1), new WindowKey(0, 0), new WindowKey(0, 1), new WindowKey(-1, 0), new WindowKey(1, 0), new WindowKey(0, 2));

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static SyntheticTerrain tallCoast() {
        return SyntheticTerrain.cliffCoast(COAST_X);
    }

    private record Fixture(GrottoPlanner planner, HeightLattice fine) {
        Fixture(HydrologySettings settings, SyntheticTerrain terrain) {
            this(settings, new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21));
        }

        Fixture(HydrologySettings settings, HeightLattice lattice) {
            this(new GrottoPlanner(settings, lattice, new TidalPlanner(settings, lattice)), lattice);
        }

        List<Grotto> plan(WindowKey window, ShapedNetwork network, List<Reach> arms) {
            int span = 3 * WindowKey.SIZE / WindowPlanner.CELL;
            int originX = (window.wx() - 1) * WindowKey.SIZE;
            int originZ = (window.wz() - 1) * WindowKey.SIZE;
            HydrologySampleBuffer samples = new HydrologySampleBuffer(span * span);
            fine.fill(originX / WindowPlanner.FINE_CELL, originZ / WindowPlanner.FINE_CELL, span, span, WindowPlanner.CELL / WindowPlanner.FINE_CELL, samples);
            DemGrid natural = new DemGrid(originX, originZ, WindowPlanner.CELL, span, span);
            System.arraycopy(samples.height(), 0, natural.values(), 0, span * span);
            return planner.plan(window, natural, samples, network, arms);
        }

        List<Grotto> plan(List<WindowKey> windows) {
            List<Grotto> grottos = new ArrayList<>();
            for (WindowKey window : windows) {
                grottos.addAll(plan(window, NOTHING, List.of()));
            }
            return grottos;
        }
    }

    private static double length(Grotto grotto) {
        double total = 0D;
        for (int point = grotto.coast() + 1; point < grotto.xs().length; point++) {
            total += Math.hypot(grotto.xs()[point] - grotto.xs()[point - 1], grotto.zs()[point] - grotto.zs()[point - 1]);
        }
        return total;
    }

    @Test
    public void tallCoastHoldsLongWindingGrottosThatStartInTheSea() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = tallCoast();
        List<Grotto> grottos = new Fixture(settings, terrain).plan(WINDOWS);
        assertTrue("grottos " + grottos.size(), grottos.size() >= 2);
        int winding = 0;
        for (Grotto grotto : grottos) {
            assertEquals(SEA, grotto.seaLevel());
            assertTrue(grotto + " starts at sea " + grotto.xs()[0], grotto.xs()[0] < COAST_X);
            int last = grotto.xs().length - 1;
            assertTrue("head inland " + grotto.xs()[last], grotto.xs()[last] > COAST_X + 16f);
            assertTrue("length " + length(grotto), length(grotto) >= settings.underground().grottos().length().min());
            double straight = Math.hypot(grotto.xs()[last] - grotto.xs()[grotto.coast()], grotto.zs()[last] - grotto.zs()[grotto.coast()]);
            winding += length(grotto) / straight >= 1.15D ? 1 : 0;
            assertTrue(new GrottoShape(grotto).inside((int) Math.floor(grotto.xs()[0]), SEA, (int) Math.floor(grotto.zs()[0])));
        }
        assertTrue("winding " + winding + " of " + grottos.size(), winding * 2 >= grottos.size());
    }

    @Test
    public void lowCoastHoldsNoGrottos() {
        assertTrue(new Fixture(settings(new IrisHydrology()), SyntheticTerrain.lowCoast(COAST_X)).plan(WINDOWS).isEmpty());
    }

    @Test
    public void disabledHoldsNoGrottos() {
        IrisHydrology config = new IrisHydrology();
        config.getUnderground().getGrottos().setEnabled(false);
        assertTrue(new Fixture(settings(config), tallCoast()).plan(WINDOWS).isEmpty());
    }

    @Test
    public void everyWetCellConnectsToTheSea() {
        HydrologySettings settings = settings(new IrisHydrology());
        for (Grotto grotto : new Fixture(settings, tallCoast()).plan(WINDOWS)) {
            GrottoShape shape = new GrottoShape(grotto);
            int[] bounds = grotto.bounds();
            int layers = SEA - shape.minY() + 1;
            int width = bounds[2] - bounds[0] + 1;
            int depth = bounds[3] - bounds[1] + 1;
            boolean[] wet = new boolean[width * depth * layers];
            long total = 0L;
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    if (shape.near(bounds[0] + x, bounds[1] + z)) {
                        for (int layer = 0; layer < layers; layer++) {
                            if (shape.inside(bounds[0] + x, shape.minY() + layer, bounds[1] + z)) {
                                wet[(layer * depth + z) * width + x] = true;
                                total++;
                            }
                        }
                    }
                }
            }
            int start = ((layers - 1) * depth + ((int) Math.floor(grotto.zs()[0]) - bounds[1])) * width + (int) Math.floor(grotto.xs()[0]) - bounds[0];
            assertTrue(wet[start]);
            ArrayDeque<Integer> frontier = new ArrayDeque<>();
            frontier.add(start);
            wet[start] = false;
            long reached = 1L;
            while (!frontier.isEmpty()) {
                int cell = frontier.poll();
                for (int offset : new int[]{1, -1, width, -width, width * depth, -width * depth}) {
                    int next = cell + offset;
                    if (next >= 0 && next < wet.length && wet[next]) {
                        wet[next] = false;
                        reached++;
                        frontier.add(next);
                    }
                }
            }
            assertEquals("grotto " + grotto.id(), total, reached);
        }
    }

    @Test
    public void grottosNeverJoin() {
        HydrologySettings settings = settings(new IrisHydrology());
        List<Grotto> grottos = new Fixture(settings, tallCoast()).plan(WINDOWS);
        for (int a = 0; a < grottos.size(); a++) {
            for (int b = a + 1; b < grottos.size(); b++) {
                GrottoShape first = new GrottoShape(grottos.get(a));
                GrottoShape second = new GrottoShape(grottos.get(b));
                int[] bounds = grottos.get(a).bounds();
                for (int z = bounds[1]; z <= bounds[3]; z += 2) {
                    for (int x = bounds[0]; x <= bounds[2]; x += 2) {
                        if (first.near(x, z) && second.near(x, z)) {
                            for (int y = SEA - 8; y <= SEA + 20; y += 2) {
                                assertFalse(x + "," + y + "," + z, first.inside(x, y, z) && second.inside(x, y, z));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void planIgnoresWindowOrderAndRepeats() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = tallCoast();
        List<WindowKey> reversed = new ArrayList<>(WINDOWS);
        Collections.reverse(reversed);
        List<Grotto> forward = new Fixture(settings, terrain).plan(WINDOWS);
        List<Grotto> backward = new Fixture(settings, terrain).plan(reversed);
        forward.sort(Comparator.comparingLong(Grotto::id));
        backward.sort(Comparator.comparingLong(Grotto::id));
        assertEquals(forward.size(), backward.size());
        for (int index = 0; index < forward.size(); index++) {
            assertEquals(forward.get(index).id(), backward.get(index).id());
            assertArrayEquals(forward.get(index).xs(), backward.get(index).xs(), 0f);
            assertArrayEquals(forward.get(index).roof(), backward.get(index).roof(), 0f);
            assertArrayEquals(forward.get(index).plugs(), backward.get(index).plugs());
        }
    }

    @Test
    public void grottosKeepClearOfRiversAndArms() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = tallCoast();
        Fixture fixture = new Fixture(settings, terrain);
        WindowKey window = null;
        Grotto clear = null;
        for (WindowKey key : WINDOWS) {
            List<Grotto> grottos = fixture.plan(key, NOTHING, List.of());
            if (!grottos.isEmpty()) {
                window = key;
                clear = grottos.get(0);
                break;
            }
        }
        assertTrue(clear != null);
        int middle = clear.xs().length / 2;
        float[] xs = new float[81];
        float[] zs = new float[81];
        int[] grade = new int[81];
        float[] half = new float[81];
        float[] depth = new float[81];
        ReachStyle[] style = new ReachStyle[81];
        for (int point = 0; point < 81; point++) {
            xs[point] = clear.xs()[middle];
            zs[point] = clear.zs()[middle] - 200f + point * 5f;
            grade[point] = SEA + 10;
            half[point] = 4f;
            depth[point] = 2f;
            style[point] = ReachStyle.RUN;
        }
        Reach dense = new Reach(1L, settings.tidalTier(), 0L, xs, zs, grade, half, depth, style, 0f);
        float apart = dense.tier().bankBand();
        for (Grotto grotto : fixture.plan(window, new ShapedNetwork(List.of(dense), List.of()), List.of())) {
            for (int point = 0; point < grotto.xs().length; point++) {
                for (int river2 = 0; river2 < xs.length; river2++) {
                    assertTrue("station " + point, Math.hypot(grotto.xs()[point] - xs[river2], grotto.zs()[point] - zs[river2]) > half[river2] + Math.min(apart, 32f) - 12f);
                }
            }
        }
    }

    @Test
    public void walkKeepsOutOfTheTerritoryOfArmsOwnedByOtherWindows() {
        IrisHydrology config = new IrisHydrology();
        HydrologySettings settings = settings(config);
        SyntheticTerrain low = SyntheticTerrain.lowCoast(COAST_X);
        TidalPlanner tidal = new TidalPlanner(settings, new HeightLattice(low, WindowPlanner.FINE_CELL, false, 1 << 21));
        boolean foreign = false;
        boolean own = false;
        for (int z = -800; z <= 800; z += 16) {
            WindowKey window = WindowKey.containing((int) COAST_X, z);
            boolean here = tidal.foreignArmMayReach(window, COAST_X + 30D, z, 16f);
            boolean elsewhere = tidal.foreignArmMayReach(new WindowKey(window.wx() + 7, window.wz()), COAST_X + 30D, z, 16f);
            own |= !here;
            foreign |= elsewhere;
            assertTrue("every anchor is foreign to a far window", !here || elsewhere);
        }
        assertTrue(own);
        assertTrue(foreign);
        config.getUnderground().getSeaInlets().setEnabled(false);
        TidalPlanner off = new TidalPlanner(settings(config), new HeightLattice(low, WindowPlanner.FINE_CELL, false, 1 << 21));
        assertFalse(off.foreignArmMayReach(new WindowKey(9, 9), COAST_X + 30D, 0D, 16f));
    }

    @Test
    public void everyGrottoStaysWithinTheNextWindow() {
        HydrologySettings settings = settings(new IrisHydrology());
        Fixture fixture = new Fixture(settings, tallCoast());
        for (WindowKey window : WINDOWS) {
            for (Grotto grotto : fixture.plan(window, NOTHING, List.of())) {
                int[] bounds = grotto.bounds();
                assertTrue(bounds[0] >= (window.wx() - 1) * WindowKey.SIZE && bounds[2] < (window.wx() + 2) * WindowKey.SIZE);
                assertTrue(bounds[1] >= (window.wz() - 1) * WindowKey.SIZE && bounds[3] < (window.wz() + 2) * WindowKey.SIZE);
            }
        }
    }
}
