package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.D8Router;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.FlowAccumulator;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;

import java.util.BitSet;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

final class InletTerrain {
    static final long SEED = 1337L;
    static final int SEA = 40;
    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    static final int COAST_X = 160;
    static final int CELL = 8;
    static final int WIDTH = 96;
    static final int DEPTH = 64;
    static final int SPAN = 3 * WindowKey.SIZE / CELL;

    final DoubleBinaryOperator natural;
    final WindowKey window;
    final DemGrid grid;
    final HydrologySampleBuffer samples;
    final int[] receivers;
    final float[] accumulation;

    private InletTerrain(DoubleBinaryOperator natural) {
        this(natural, new WindowKey(0, 0));
    }

    private InletTerrain(DoubleBinaryOperator natural, WindowKey window) {
        this(natural, window, (window.wx() - 1) * WindowKey.SIZE, (window.wz() - 1) * WindowKey.SIZE, SPAN, SPAN);
    }

    private InletTerrain(DoubleBinaryOperator natural, WindowKey window, int originX, int originZ, int width, int depth) {
        this.natural = natural;
        this.window = window;
        this.grid = new DemGrid(originX, originZ, CELL, width, depth);
        int count = width * depth;
        this.samples = new HydrologySampleBuffer(count);
        BitSet seeds = new BitSet(count);
        for (int cell = 0; cell < count; cell++) {
            int x = grid.blockX(cell);
            int z = grid.blockZ(cell);
            float height = (float) natural.applyAsDouble(x, z);
            grid.values()[cell] = height;
            samples.height()[cell] = height;
            samples.ocean()[cell] = Math.round(height) < SEA;
            int i = cell % width;
            int j = cell / width;
            if (samples.ocean()[cell] || i == 0 || j == 0 || i == width - 1 || j == depth - 1) {
                seeds.set(cell);
            }
        }
        float[] filled = PriorityFlood.fill(grid, seeds, 0.001f);
        DemGrid surface = new DemGrid(originX, originZ, CELL, width, depth);
        System.arraycopy(filled, 0, surface.values(), 0, count);
        this.receivers = D8Router.receivers(surface, filled, SEED);
        float[] weights = new float[count];
        for (int cell = 0; cell < count; cell++) {
            if (samples.ocean()[cell]) {
                receivers[cell] = -1;
            }
            weights[cell] = CELL * CELL;
        }
        this.accumulation = FlowAccumulator.accumulate(receivers, filled, weights, 4194304f);
    }

    static InletTerrain cliff(double cliffHeight) {
        return new InletTerrain((x, z) -> x < COAST_X ? SEA - 20D : SEA + cliffHeight + 0.08D * (x - COAST_X));
    }

    InletTerrain wide(WindowKey window, int windows) {
        int cells = (2 * windows + 1) * WindowKey.SIZE / CELL;
        int originX = (window.wx() - windows) * WindowKey.SIZE;
        int originZ = (window.wz() - windows) * WindowKey.SIZE;
        return new InletTerrain(natural, window, originX, originZ, cells, cells);
    }

    static InletTerrain bay(int westX, int eastX, int bayZ) {
        return new InletTerrain((x, z) -> {
            if (z > bayZ - CELL / 2D) {
                return Math.abs(z - bayZ) < CELL / 2D && x > westX - CELL / 2D && x < eastX + CELL / 2D ? SEA - 20D : SEA + 5D;
            }
            return SEA + 30D + 0.08D * (bayZ - z);
        });
    }

    static InletTerrain eastEdgeCoast() {
        double coast = WindowKey.SIZE - CELL;
        return new InletTerrain((x, z) -> x < coast - CELL / 2D ? SEA - 20D : SEA + 30D + 0.08D * (x - coast));
    }

    static InletTerrain openCoast() {
        return new InletTerrain((x, z) -> {
            double coast = 768D + 120D * Math.sin(z / 180D) + 40D * Math.sin(z / 53D);
            return x < coast ? SEA - 20D : SEA + 30D + 0.08D * (x - coast);
        });
    }

    static InletTerrain cliffWithTrough(double cliffHeight, double troughFrom, double troughTo, double troughHeight) {
        return new InletTerrain((x, z) -> {
            if (x < COAST_X) {
                return SEA - 20D;
            }
            if (x >= troughFrom && x <= troughTo) {
                return troughHeight;
            }
            return SEA + cliffHeight + 0.08D * (x - COAST_X);
        });
    }

    static InletTerrain coastalRidge(double crestDistance) {
        return new InletTerrain((x, z) -> {
            if (x < COAST_X) {
                return SEA - 20D;
            }
            double crest = SEA + 30D + 0.3D * crestDistance;
            return x < COAST_X + crestDistance ? SEA + 30D + 0.3D * (x - COAST_X) : crest - 0.02D * (x - COAST_X - crestDistance);
        });
    }

    static InletTerrain southCoast(double coastZ) {
        return new InletTerrain((x, z) -> z < coastZ ? SEA - 20D : SEA + 30D + 0.08D * (z - coastZ));
    }

    static InletTerrain steppedCoast(double stepX) {
        return new InletTerrain((x, z) -> x < COAST_X ? SEA - 20D : x < stepX ? SEA + 9D : SEA + 40D + 0.05D * (x - stepX));
    }

    static InletTerrain pocket(int pocketX, int pocketZ) {
        return new InletTerrain((x, z) -> Math.abs(x - pocketX) < CELL / 2D && Math.abs(z - pocketZ) < CELL / 2D ? SEA - 20D
                : SEA + 30D + 0.08D * Math.hypot(x - pocketX, z - pocketZ));
    }

    static float length(SeaInlet inlet) {
        float length = 0f;
        for (int station = 1; station < inlet.stations(); station++) {
            length += (float) Math.hypot(inlet.xs()[station] - inlet.xs()[station - 1], inlet.zs()[station] - inlet.zs()[station - 1]);
        }
        return length;
    }

    static HydrologySettings settings() {
        return settings(new IrisHydrology());
    }

    static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA, MIN_Y, MAX_Y);
    }

    InletTerrain span(WindowKey window) {
        return new InletTerrain(natural, window);
    }

    InletTerrain narrow(int originX, int originZ) {
        return new InletTerrain(natural, WindowKey.containing(originX, originZ), originX, originZ, WIDTH, DEPTH);
    }

    List<SeaInlet> plan(HydrologySettings settings, ShapedNetwork network) {
        return new SeaInletPlanner(settings).plan(window, grid, samples, receivers, accumulation, network, SEED);
    }

    List<SeaInlet> plan(HydrologySettings settings) {
        return plan(settings, new ShapedNetwork(List.of(), List.of()));
    }

    boolean ocean(float x, float z) {
        int cell = grid.index(Math.round((x - grid.originX()) / CELL), Math.round((z - grid.originZ()) / CELL));
        return samples.ocean()[cell];
    }

    int naturalAt(int x, int z) {
        return (int) Math.round(natural.applyAsDouble(x, z));
    }

    static Reach eastWestReach(HydrologySettings settings, float fromX, float toX, float z, int grade) {
        int points = Math.max(2, (int) Math.ceil((toX - fromX) / 8f) + 1);
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grades = new int[points];
        float[] widths = new float[points];
        float[] depths = new float[points];
        ReachStyle[] styles = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = fromX + (toX - fromX) * point / (points - 1);
            zs[point] = z;
            grades[point] = grade;
            widths[point] = 2f;
            depths[point] = 1.5f;
            styles[point] = ReachStyle.RUN;
        }
        return new Reach(78L, settings.tiers().get(0), 0L, xs, zs, grades, widths, depths, styles, 2000f);
    }

    static Reach northSouthReach(HydrologySettings settings, float x, int grade, float halfWidth, float depth) {
        int points = 25;
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grades = new int[points];
        float[] widths = new float[points];
        float[] depths = new float[points];
        ReachStyle[] styles = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = x;
            zs[point] = point * 64f - WindowKey.SIZE;
            grades[point] = grade;
            widths[point] = halfWidth;
            depths[point] = depth;
            styles[point] = ReachStyle.RUN;
        }
        return new Reach(77L, settings.tiers().get(0), 0L, xs, zs, grades, widths, depths, styles, 2000f);
    }
}
