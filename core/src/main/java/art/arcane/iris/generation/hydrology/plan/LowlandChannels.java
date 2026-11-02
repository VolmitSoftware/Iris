package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;

import java.util.BitSet;

final class LowlandChannels {
    private static final float RISE = 2f;

    private LowlandChannels() {
    }

    static void extend(BitSet channels, float[] filled, int[] receivers, HydrologySampleBuffer samples, BitSet inland, int seaLevel) {
        float[] natural = samples.height();
        BitSet ends = new BitSet(receivers.length);
        for (int cell = channels.nextSetBit(0); cell >= 0; cell = channels.nextSetBit(cell + 1)) {
            int receiver = receivers[cell];
            if (receiver >= 0 && !channels.get(receiver) && crossable(filled, samples, inland, seaLevel, receiver, natural[cell])) {
                ends.set(cell);
            }
        }
        for (int end = ends.nextSetBit(0); end >= 0; end = ends.nextSetBit(end + 1)) {
            float lowest = natural[end];
            int cell = receivers[end];
            while (cell >= 0 && !channels.get(cell) && crossable(filled, samples, inland, seaLevel, cell, lowest)) {
                channels.set(cell);
                lowest = Math.min(lowest, natural[cell]);
                cell = receivers[cell];
            }
        }
    }

    private static boolean crossable(float[] filled, HydrologySampleBuffer samples, BitSet inland, int seaLevel, int cell, float lowest) {
        return !samples.ocean()[cell] && filled[cell] <= seaLevel && !inland.get(cell)
                && samples.height()[cell] <= Math.max(lowest, seaLevel) + RISE
                && (samples.permission()[cell] & HydrologySampleBuffer.ALLOW_STREAMS) != 0;
    }
}
