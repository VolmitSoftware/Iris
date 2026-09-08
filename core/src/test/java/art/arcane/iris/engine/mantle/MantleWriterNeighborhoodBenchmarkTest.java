package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.object.IrisPosition;
import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MantleWriterNeighborhoodBenchmarkTest {
    private static final int WARMUP = 400;
    private static final int ROUNDS = 40;
    private static final int PASSES = 6;
    private static final double RADIUS = 5.0D;

    @Test
    public void hollowingAllocatesLessThanThePositionProbeImplementation() {
        Set<IrisPosition> ballooned = MantleWriter.getBallooned(line(), RADIUS);
        assertTrue("benchmark input is too small to be meaningful: " + ballooned.size(), ballooned.size() > 4_000);

        Comparison comparison = compare(
                () -> MantleWriter.getHollowed(ballooned),
                () -> MantleWriterNeighborhoodTest.referenceHollowed(ballooned));

        System.out.println("[bench] getHollowed voxels=" + ballooned.size()
                + " primitiveKeys=" + comparison.candidate()
                + " positionProbes=" + comparison.reference());

        assertEquals(comparison.reference().result(), comparison.candidate().result());

        Assume.assumeTrue("per-thread allocation counters are unavailable on this JVM",
                comparison.candidate().bytes() >= 0L);
        assertTrue("primitive-keyed hollowing allocated " + comparison.candidate().bytes()
                        + " bytes against " + comparison.reference().bytes() + " for the position-probe implementation",
                comparison.candidate().bytes() < comparison.reference().bytes());
    }

    @Test
    public void ballooningMatchesTheVarargsDistanceImplementationExactly() {
        Set<IrisPosition> seeds = line();

        Comparison comparison = compare(
                () -> MantleWriter.getBallooned(seeds, RADIUS),
                () -> MantleWriterNeighborhoodTest.referenceBallooned(seeds, RADIUS));

        System.out.println("[bench] getBallooned seeds=" + seeds.size()
                + " fixedArityDistance=" + comparison.candidate()
                + " varargsDistance=" + comparison.reference());

        assertEquals(comparison.reference().result(), comparison.candidate().result());
    }

    private static Set<IrisPosition> line() {
        Set<IrisPosition> positions = new LinkedHashSet<>();
        for (int step = 0; step <= 48; step++) {
            positions.add(new IrisPosition(step, 60 + (step % 5), step / 2));
        }
        return positions;
    }

    private static Comparison compare(Supplier<Set<IrisPosition>> candidate, Supplier<Set<IrisPosition>> reference) {
        for (int round = 0; round < WARMUP; round++) {
            candidate.get();
            reference.get();
        }

        Measurement bestCandidate = null;
        Measurement bestReference = null;
        for (int pass = 0; pass < PASSES; pass++) {
            bestCandidate = best(bestCandidate, measure(candidate));
            bestReference = best(bestReference, measure(reference));
        }

        return new Comparison(bestCandidate, bestReference);
    }

    private static Measurement best(Measurement current, Measurement measured) {
        if (current == null) {
            return measured;
        }

        return new Measurement(
                measured.result(),
                current.bytes() < 0L || measured.bytes() < 0L ? -1L : Math.min(current.bytes(), measured.bytes()),
                Math.min(current.nanos(), measured.nanos()));
    }

    private static Measurement measure(Supplier<Set<IrisPosition>> work) {
        Set<IrisPosition> result = null;
        long startBytes = allocatedBytes();
        long startNanos = System.nanoTime();
        for (int round = 0; round < ROUNDS; round++) {
            result = work.get();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long endBytes = allocatedBytes();
        long bytes = startBytes < 0L || endBytes < 0L ? -1L : (endBytes - startBytes) / ROUNDS;

        return new Measurement(result, bytes, elapsedNanos / ROUNDS);
    }

    private static long allocatedBytes() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!(threads instanceof com.sun.management.ThreadMXBean hotspot) || !hotspot.isThreadAllocatedMemoryEnabled()) {
            return -1L;
        }
        return hotspot.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private record Comparison(Measurement candidate, Measurement reference) {
    }

    private record Measurement(Set<IrisPosition> result, long bytes, long nanos) {
        @Override
        public String toString() {
            return "{bytes/op=" + bytes + ", ns/op=" + nanos + ", voxels=" + result.size() + "}";
        }
    }
}
