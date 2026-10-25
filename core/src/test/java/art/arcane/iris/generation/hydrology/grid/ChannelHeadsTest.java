package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelHeadsTest {
    private static final IntPredicate NONE = cell -> false;
    private static final IntPredicate ALL = cell -> true;

    @Test
    public void candidatesNeedCatchmentLandAndPermission() {
        DemGrid surface = new DemGrid(0, 0, 8, 4, 1);
        System.arraycopy(new float[]{100f, 100f, 30f, 100f}, 0, surface.values(), 0, 4);
        float[] accumulation = {2000f, 1000f, 2000f, 2000f};
        byte[] permission = {HydrologySampleBuffer.ALLOW_STREAMS, HydrologySampleBuffer.ALLOW_STREAMS, HydrologySampleBuffer.ALLOW_STREAMS,
                HydrologySampleBuffer.ALLOW_PONDS};
        BitSet candidates = ChannelHeads.candidates(surface, accumulation, permission, 1500f, 40);
        assertEquals(1, candidates.cardinality());
        assertTrue(candidates.get(0));
    }

    @Test
    public void headNeedsReliefWithinSixtyFourBlocks() {
        assertEquals(20, keptOnRow(8, 8, 1f).cardinality());
        assertEquals(0, keptOnRow(8, 9, 1f).cardinality());
        assertEquals(20, keptOnRow(8, 9, 0f).cardinality());
    }

    @Test
    public void regionCellsLookTwoHundredFiftySixBlocksAhead() {
        assertEquals(20, keptOnRow(64, 4, 1f).cardinality());
        assertEquals(0, keptOnRow(64, 5, 1f).cardinality());
    }

    @Test
    public void diagonalStepsCountRootTwoCells() {
        assertEquals(20, keptOnDiagonal(5).cardinality());
        assertEquals(0, keptOnDiagonal(6).cardinality());
    }

    @Test
    public void pooledHeadsSkipReliefCheck() {
        Row row = row(8, 30);
        row.surface().values()[0] += 0.6f;
        assertEquals(20, keep(row, 1f, NONE, ALL).cardinality());
        row.surface().values()[0] -= 0.2f;
        assertEquals(0, keep(row, 1f, NONE, ALL).cardinality());
    }

    @Test
    public void anchoredCellsStayAndRejectedHeadsDropTheirChannel() {
        Row row = row(8, 30);
        BitSet anchored = keep(row, 1f, cell -> cell == 10, ALL);
        assertEquals(10, anchored.cardinality());
        assertFalse(anchored.get(9));
        assertTrue(anchored.get(10) && anchored.get(19));
        Row relieved = row(8, 3);
        assertEquals(20, keep(relieved, 1f, NONE, ALL).cardinality());
        assertEquals(0, keep(relieved, 1f, NONE, NONE).cardinality());
    }

    private static BitSet keptOnRow(int cell, int dropStep, float relief) {
        return keep(row(cell, dropStep), relief, NONE, ALL);
    }

    private static BitSet keep(Row row, float relief, IntPredicate anchored, IntPredicate keepHead) {
        float[] accumulation = new float[row.receivers().length];
        Arrays.fill(accumulation, 2000f);
        byte[] permission = new byte[row.receivers().length];
        Arrays.fill(permission, HydrologySampleBuffer.ALLOW_STREAMS);
        BitSet candidates = ChannelHeads.candidates(row.surface(), accumulation, permission, 1500f, 40);
        return ChannelHeads.keep(row.surface(), row.heights(), row.receivers(), candidates, relief, anchored, keepHead);
    }

    private static Row row(int cell, int dropStep) {
        int count = 20;
        DemGrid surface = new DemGrid(0, 0, cell, count, 1);
        float[] heights = new float[count];
        int[] receivers = new int[count];
        for (int index = 0; index < count; index++) {
            heights[index] = index < dropStep ? 100f : 99f;
            surface.values()[index] = heights[index] + 0.001f * (count - index);
            receivers[index] = index + 1 < count ? index + 1 : -1;
        }
        return new Row(surface, heights, receivers);
    }

    private static BitSet keptOnDiagonal(int dropStep) {
        int size = 20;
        DemGrid surface = new DemGrid(0, 0, 8, size, size);
        float[] heights = new float[size * size];
        int[] receivers = new int[size * size];
        Arrays.fill(heights, 200f);
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[size * size];
        byte[] permission = new byte[size * size];
        Arrays.fill(permission, HydrologySampleBuffer.ALLOW_STREAMS);
        for (int step = 0; step < size; step++) {
            int cell = surface.index(step, step);
            heights[cell] = step < dropStep ? 100f : 99f;
            accumulation[cell] = 2000f;
            receivers[cell] = step + 1 < size ? surface.index(step + 1, step + 1) : -1;
        }
        System.arraycopy(heights, 0, surface.values(), 0, heights.length);
        BitSet candidates = ChannelHeads.candidates(surface, accumulation, permission, 1500f, 40);
        return ChannelHeads.keep(surface, heights, receivers, candidates, 1f, NONE, ALL);
    }

    private record Row(DemGrid surface, float[] heights, int[] receivers) {
    }
}
