package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReachFitterTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = 40;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int CELL = 8;
    private static final int FINE = 4;
    private static final int CHAIN = 24;
    private static final int CHAIN_ROW = 16;
    private static final float CATCHMENT = 2000f;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;

    @Test
    public void meanderSurvivesSteepSlopesAtReducedAmplitude() {
        HydrologyTier stream = HydrologySettings.compile(new IrisHydrology(), SEED, SEA_LEVEL, MIN_Y, MAX_Y).tiers().get(0);
        for (long shapeSeed = 11L; shapeSeed < 19L; shapeSeed++) {
            float chainZ = CHAIN_ROW * CELL;
            float steep = deviation(fit((x, z) -> 300D - 0.5D * x, stream, shapeSeed), chainZ);
            float gentle = deviation(fit((x, z) -> 300D - 0.02D * x, stream, shapeSeed), chainZ);
            String metric = "seed " + shapeSeed + " steep " + steep + " gentle " + gentle;
            assertTrue(metric, steep >= 1f);
            assertTrue(metric, gentle > steep);
        }
    }

    @Test
    public void softenedOffsetsStayOffABlockedNode() {
        HydrologyTier stream = HydrologySettings.compile(new IrisHydrology(), SEED, SEA_LEVEL, MIN_Y, MAX_Y).tiers().get(0);
        int chainZ = CHAIN_ROW * CELL;
        int blockedX = 96;
        DoubleBinaryOperator trench = (x, z) -> 200D - 0.02D * x - (x > blockedX && x < blockedX + 40 && Math.abs(z - chainZ - 4D) <= 2D ? 3D : 0D);
        PondMask mask = new PondMask(blockedX, chainZ + FINE, FINE, 1, 1, new long[]{1L});
        Pond pond = new Pond(5L, PondKind.NATURAL, 150, 149, blockedX, chainZ + FINE, 151f, mask, 0L, 0, "water", blockedX, chainZ + FINE);
        ReachFitter.Fitted fitted = fit(trench, stream, 11L, List.of(pond));
        int checked = 0;
        boolean entered = false;
        for (int point = 0; point < fitted.xs().length; point++) {
            if (Math.abs(fitted.xs()[point] - blockedX) < 0.5f) {
                checked++;
                assertEquals("the point beside the blocked node stays on its own node", chainZ, fitted.zs()[point], 1e-3f);
            }
            entered |= fitted.xs()[point] > blockedX + 8 && fitted.xs()[point] < blockedX + 32 && fitted.zs()[point] > chainZ + 2f;
        }
        assertEquals(1, checked);
        assertTrue("the line takes the trench just past the blocked node", entered);
    }

    private static float deviation(ReachFitter.Fitted fitted, float chainZ) {
        int last = fitted.xs().length - 1;
        assertEquals(0f, fitted.xs()[0], 0f);
        assertEquals(chainZ, fitted.zs()[0], 0f);
        assertEquals((CHAIN - 1) * CELL, fitted.xs()[last], 0f);
        assertEquals(chainZ, fitted.zs()[last], 0f);
        float deviation = 0f;
        for (int point = 1; point < last; point++) {
            deviation = Math.max(deviation, Math.abs(fitted.zs()[point] - chainZ));
        }
        return deviation;
    }

    private static ReachFitter.Fitted fit(DoubleBinaryOperator height, HydrologyTier tier, long shapeSeed) {
        return fit(height, tier, shapeSeed, List.of());
    }

    private static ReachFitter.Fitted fit(DoubleBinaryOperator height, HydrologyTier tier, long shapeSeed, List<Pond> ponds) {
        DemGrid grid = new DemGrid(0, 0, CELL, CHAIN, 2 * CHAIN_ROW + 1);
        for (int j = 0; j < grid.depth(); j++) {
            for (int i = 0; i < grid.width(); i++) {
                grid.values()[grid.index(i, j)] = (float) height.applyAsDouble(i * CELL, j * CELL);
            }
        }
        int[] cells = new int[CHAIN];
        for (int i = 0; i < CHAIN; i++) {
            cells[i] = grid.index(i, CHAIN_ROW);
        }
        float[] catchment = new float[CHAIN];
        Arrays.fill(catchment, CATCHMENT);
        HeightLattice fine = new HeightLattice(new Terrain(height), FINE, false, 1 << 20);
        return new ReachFitter(fine, new PondLookup(ponds)).fit(grid, cells, catchment, tier, shapeSeed, false);
    }

    private record Terrain(DoubleBinaryOperator height) implements HydrologyTerrainSource {
        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                out.height()[index] = (float) height.applyAsDouble(xs[index], zs[index]);
                out.ocean()[index] = false;
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
