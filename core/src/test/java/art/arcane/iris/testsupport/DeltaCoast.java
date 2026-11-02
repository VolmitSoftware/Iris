package art.arcane.iris.testsupport;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

public final class DeltaCoast {
    public static final long SEED = 1337L;
    public static final int SEA_LEVEL = 40;
    public static final int ROW = 128;
    public static final int SEA_X = 240;
    public static final double COAST_X = 236D;
    public static final int HILL_Z = ROW + 12;
    public static final int NO_NEIGHBOUR = Integer.MIN_VALUE;
    private static final double RISE = 0.1D;
    private static final double BACKED_PLAIN = 30D;
    private static final double BACKED_RISE = 0.5D;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int CELL = 8;
    private static final int FINE = 4;
    private static final float ROW_CATCHMENT = 20000f;
    private static final WindowKey WINDOW = new WindowKey(0, 0);
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;

    private DeltaCoast() {
    }

    public static HydrologySettings settings(int channels) {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(2).setHalfWidth(new KList<>(3D, 5D));
        config.getTiers().get(2).getDelta().setChannels(channels);
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y);
    }

    public static ShapedNetwork shape(HydrologySettings settings, int originX, int originZ, List<Pond> ponds, int neighbourZ, long seed) {
        return shape(settings, originX, originZ, ponds, neighbourZ, seed, DeltaCoast::ground);
    }

    public static ShapedNetwork shape(HydrologySettings settings, int originX, int originZ, List<Pond> ponds, int neighbourZ, long seed,
                                      DoubleBinaryOperator ground) {
        int width = (SEA_X + 32 - originX) / CELL + 1;
        int depth = (ROW + 96 - originZ) / CELL + 1;
        DemGrid surface = new DemGrid(originX, originZ, CELL, width, depth);
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                surface.set(i, j, originX + i * CELL >= SEA_X ? 30f : 60f);
            }
        }
        int[] receivers = new int[width * depth];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * depth];
        BitSet channels = new BitSet(width * depth);
        row(surface, receivers, accumulation, channels, ROW);
        if (neighbourZ != NO_NEIGHBOUR) {
            row(surface, receivers, accumulation, channels, neighbourZ);
        }
        HeightLattice fine = new HeightLattice(new Terrain(ground), FINE, false, 1 << 16);
        return new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels, ponds, fine, seed);
    }

    public static double ground(double x, double z) {
        return x < COAST_X ? SEA_LEVEL + 0.5D + RISE * (COAST_X - x) : SEA_LEVEL - 4D;
    }

    public static double cliffBacked(double x, double z) {
        return x < COAST_X - BACKED_PLAIN ? ground(COAST_X - BACKED_PLAIN, z) + BACKED_RISE * (COAST_X - BACKED_PLAIN - x) : ground(x, z);
    }

    public static double northHill(double x, double z) {
        return z > HILL_Z && x < COAST_X ? SEA_LEVEL + 10D : ground(x, z);
    }

    public static Reach river(ShapedNetwork shaped, int row) {
        for (Reach reach : shaped.reaches()) {
            if (reach.style()[0] != ReachStyle.MOUTH && Math.abs(reach.zs()[0] - row) < 1f) {
                return reach;
            }
        }
        throw new AssertionError("no river along z=" + row + " in " + shaped.reaches());
    }

    public static List<Reach> deltaChannels(ShapedNetwork shaped) {
        List<Reach> fan = new ArrayList<>();
        for (Reach reach : shaped.reaches()) {
            if (reach.style()[0] == ReachStyle.MOUTH) {
                fan.add(reach);
            }
        }
        return fan;
    }

    private static void row(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels, int z) {
        int j = (z - grid.originZ()) / CELL;
        for (int x = 0; x < SEA_X; x += CELL) {
            int i = (x - grid.originX()) / CELL;
            int cell = grid.index(i, j);
            receivers[cell] = grid.index(i + 1, j);
            accumulation[cell] = ROW_CATCHMENT * (x / CELL + 1);
            channels.set(cell);
        }
    }

    private static final class Terrain implements HydrologyTerrainSource {
        private final DoubleBinaryOperator height;

        private Terrain(DoubleBinaryOperator height) {
            this.height = height;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                float value = (float) height.applyAsDouble(xs[index], zs[index]);
                out.height()[index] = value;
                out.ocean()[index] = value <= SEA_LEVEL;
                out.rainfall()[index] = 1f;
                out.pondDensity()[index] = 1f;
                out.permission()[index] = ALLOW_ALL;
                out.fluidProfile()[index] = null;
            }
        }

        @Override
        public int seaLevel() {
            return SEA_LEVEL;
        }

        @Override
        public int minY() {
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }
    }
}
