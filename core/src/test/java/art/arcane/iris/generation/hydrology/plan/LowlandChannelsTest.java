package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;

import static org.junit.Assert.assertEquals;

public class LowlandChannelsTest {
    private static final int SEA_LEVEL = 40;
    private static final int CELLS = 12;
    private static final int RIVER_END = 4;
    private static final int COAST = 10;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;

    @Test
    public void riverCrossesLowLandToTheSea() {
        Row row = new Row(44f, 43f, 41f, 39f, 38f);
        assertEquals("every lowland cell up to the sea becomes channel", channelsUpTo(COAST), row.extend());
    }

    @Test
    public void riverNeverClimbsOutOfTheLowland() {
        Row ridge = new Row(44f, 43f, 46f, 41f, 38f);
        assertEquals("the river stops before ground that rises over the lowland it crossed", channelsUpTo(RIVER_END + 3), ridge.extend());
        Row bump = new Row(44f, 43f, 45f, 41f, 38f);
        assertEquals("a rise within the bound is crossed", channelsUpTo(COAST), bump.extend());
        Row belowSea = new Row(39f, 36f, 41.5f, 38f, 37f);
        assertEquals("ground up to just above the sea is crossed from below it", channelsUpTo(COAST), belowSea.extend());
    }

    @Test
    public void riverStopsAtInlandHollowsHighFillsAndBlockedStreams() {
        Row hollow = new Row(44f, 43f, 41f, 39f, 38f);
        hollow.inland.set(RIVER_END + 3);
        assertEquals("an inland hollow is not crossed", channelsUpTo(RIVER_END + 3), hollow.extend());
        Row high = new Row(44f, 43f, 41f, 39f, 38f);
        high.filled[RIVER_END + 2] = SEA_LEVEL + 1f;
        assertEquals("ground that fills above the sea is not lowland", channelsUpTo(RIVER_END + 2), high.extend());
        Row blocked = new Row(44f, 43f, 41f, 39f, 38f);
        blocked.samples.permission()[RIVER_END + 4] = HydrologySampleBuffer.ALLOW_PONDS;
        assertEquals("streams stop where the policy blocks them", channelsUpTo(RIVER_END + 4), blocked.extend());
    }

    private static BitSet channelsUpTo(int exclusive) {
        BitSet channels = new BitSet(CELLS);
        channels.set(0, exclusive);
        return channels;
    }

    private static final class Row {
        private final HydrologySampleBuffer samples = new HydrologySampleBuffer(CELLS);
        private final float[] filled = new float[CELLS];
        private final int[] receivers = new int[CELLS];
        private final BitSet inland = new BitSet(CELLS);

        private Row(float... lowland) {
            Arrays.fill(samples.permission(), ALLOW_ALL);
            for (int cell = 0; cell < CELLS; cell++) {
                receivers[cell] = cell + 1 < CELLS ? cell + 1 : -1;
                float natural = cell <= RIVER_END ? 50f - cell : cell < COAST ? lowland[cell - RIVER_END - 1] : 10f;
                samples.height()[cell] = natural;
                samples.ocean()[cell] = cell >= COAST;
                filled[cell] = cell <= RIVER_END ? natural : Math.min(natural, SEA_LEVEL - 0.5f);
            }
        }

        private BitSet extend() {
            BitSet channels = channelsUpTo(RIVER_END + 1);
            LowlandChannels.extend(channels, filled, receivers, samples, inland, SEA_LEVEL);
            return channels;
        }
    }
}
