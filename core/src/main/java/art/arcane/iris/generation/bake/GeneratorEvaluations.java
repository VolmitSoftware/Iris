package art.arcane.iris.generation.bake;

import java.util.concurrent.atomic.LongAdder;

public final class GeneratorEvaluations {
    private static final LongAdder BAKED_CHUNKS = new LongAdder();
    private static final LongAdder BAKED = new LongAdder();
    private static final LongAdder BATCHED = new LongAdder();
    private static final LongAdder SCALAR = new LongAdder();

    private GeneratorEvaluations() {
    }

    public static void bakedChunk(long evaluations) {
        BAKED_CHUNKS.increment();
        BAKED.add(evaluations);
    }

    public static void batched(long evaluations) {
        BATCHED.add(evaluations);
    }

    public static void scalar(int evaluations) {
        SCALAR.add(evaluations);
    }

    public static Snapshot snapshot() {
        return new Snapshot(BAKED_CHUNKS.sum(), BAKED.sum(), BATCHED.sum(), SCALAR.sum());
    }

    public static void reset() {
        BAKED_CHUNKS.reset();
        BAKED.reset();
        BATCHED.reset();
        SCALAR.reset();
    }

    public record Snapshot(long bakedChunks, long bakedEvaluations, long batchedEvaluations, long scalarEvaluations) {
        public double bakedEvaluationsPerChunk() {
            return bakedChunks == 0L ? 0D : (double) bakedEvaluations / bakedChunks;
        }

        public Snapshot minus(Snapshot earlier) {
            return new Snapshot(bakedChunks - earlier.bakedChunks, bakedEvaluations - earlier.bakedEvaluations,
                    batchedEvaluations - earlier.batchedEvaluations, scalarEvaluations - earlier.scalarEvaluations);
        }
    }
}
