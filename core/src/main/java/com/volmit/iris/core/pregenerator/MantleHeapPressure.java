package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

public final class MantleHeapPressure {
    private static final double HIGH_WATER = 0.92D;
    private static final double LOW_WATER = 0.82D;
    private static final long MAXIMUM_HYSTERESIS_MS = 60_000L;
    private static final ObjectName DIAGNOSTIC_COMMAND = diagnosticCommandName();
    private static final List<MemoryPoolMXBean> HEAP_POOLS = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP)
            .toList();
    private static final PanicGcReclaimer PANIC_RECLAIMER = new PanicGcReclaimer(
            new PanicGcPolicy(10_000L, 60_000L, 60_000L, 15 * 60_000L),
            new PanicGcActions(
                    System::currentTimeMillis,
                    System::gc,
                    () -> invokeHotSpotDiagnosticGc(ManagementFactory.getPlatformMBeanServer()),
                    fraction -> Iris.info("Iris heap remained at %.1f%% after a normal reclaim; running the JVM's diagnostic full GC to keep generation going.",
                            fraction * 100.0D),
                    (context, failure) -> {
                        Iris.warn("%s", context);
                        Iris.reportError(failure);
                    }));
    private static final HeapPressureGate PRESSURE_GATE = new HeapPressureGate(
            HIGH_WATER,
            LOW_WATER,
            MAXIMUM_HYSTERESIS_MS,
            System::currentTimeMillis,
            PANIC_RECLAIMER::resetEpisode);

    private MantleHeapPressure() {
    }

    // Usage between collections includes garbage, which G1 lets climb close to the maximum on a healthy heap.
    public static double usedFraction() {
        long max = Runtime.getRuntime().maxMemory();
        if (max <= 0L || max == Long.MAX_VALUE) {
            return 0.0D;
        }

        long usedAfterGc = 0L;
        for (MemoryPoolMXBean pool : HEAP_POOLS) {
            MemoryUsage afterGc = pool.getCollectionUsage();
            usedAfterGc += afterGc == null ? pool.getUsage().getUsed() : afterGc.getUsed();
        }
        return (double) usedAfterGc / (double) max;
    }

    public static boolean overHighWater() {
        return PRESSURE_GATE.update(usedFraction());
    }

    public static double reclaimUrgency(double fraction) {
        if (!Double.isFinite(fraction) || fraction <= LOW_WATER) {
            return 0D;
        }
        if (fraction >= HIGH_WATER) {
            return 1D;
        }
        return (fraction - LOW_WATER) / (HIGH_WATER - LOW_WATER);
    }

    // Paused generation barely allocates, so the collector may not run again and the last reading can stay high after
    // most of it has become garbage.
    public static void requestPanicReclaim() {
        PANIC_RECLAIMER.request(usedFraction());
    }

    // Unlike System.gc(), still honoured by servers started with -XX:+DisableExplicitGC.
    static void invokeHotSpotDiagnosticGc(MBeanServer server) throws Exception {
        if (!server.isRegistered(DIAGNOSTIC_COMMAND)) {
            throw new UnsupportedOperationException("HotSpot DiagnosticCommand MBean is not registered on this JVM");
        }
        server.invoke(DIAGNOSTIC_COMMAND, "gcRun", new Object[0], new String[0]);
    }

    private static ObjectName diagnosticCommandName() {
        try {
            return new ObjectName("com.sun.management:type=DiagnosticCommand");
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    record PanicGcPolicy(
            long diagnosticDelayMs,
            long diagnosticCooldownMs,
            long initialFailureBackoffMs,
            long maximumFailureBackoffMs
    ) {
    }

    record PanicGcActions(
            LongSupplier clock,
            Runnable explicitGc,
            DiagnosticGc diagnosticGc,
            DoubleConsumer diagnosticStart,
            BiConsumer<String, Throwable> failureSink
    ) {
    }

    @FunctionalInterface
    interface DiagnosticGc {
        void run() throws Exception;
    }

    static final class HeapPressureGate {
        private static final long NOT_BELOW_HIGH_WATER = Long.MIN_VALUE;

        private final double highWater;
        private final double lowWater;
        private final long maximumHysteresisMs;
        private final LongSupplier clock;
        private final Runnable releaseAction;
        private boolean engaged;
        private long belowHighWaterSince = NOT_BELOW_HIGH_WATER;

        HeapPressureGate(double highWater, double lowWater, long maximumHysteresisMs, LongSupplier clock, Runnable releaseAction) {
            this.highWater = highWater;
            this.lowWater = lowWater;
            this.maximumHysteresisMs = maximumHysteresisMs;
            this.clock = clock;
            this.releaseAction = releaseAction;
        }

        synchronized boolean update(double fraction) {
            if (!Double.isFinite(fraction)) {
                return engaged;
            }
            if (!engaged) {
                engaged = fraction >= highWater;
                return engaged;
            }
            if (fraction <= lowWater) {
                release();
                return false;
            }
            if (fraction >= highWater) {
                belowHighWaterSince = NOT_BELOW_HIGH_WATER;
                return true;
            }

            long now = clock.getAsLong();
            if (belowHighWaterSince == NOT_BELOW_HIGH_WATER) {
                belowHighWaterSince = now;
                return true;
            }
            // A collector that rarely shrinks the heap could otherwise keep generation paused between the two marks.
            if (now - belowHighWaterSince < maximumHysteresisMs) {
                return true;
            }

            release();
            return false;
        }

        private void release() {
            engaged = false;
            belowHighWaterSince = NOT_BELOW_HIGH_WATER;
            releaseAction.run();
        }
    }

    static final class PanicGcReclaimer {
        private final PanicGcPolicy policy;
        private final PanicGcActions actions;
        private boolean panicEpisode;
        private long explicitAttemptAt;
        private long nextDiagnosticAllowedAt;
        private long failureBackoffMs;

        PanicGcReclaimer(PanicGcPolicy policy, PanicGcActions actions) {
            this.policy = policy;
            this.actions = actions;
            this.failureBackoffMs = policy.initialFailureBackoffMs();
        }

        synchronized void request(double fraction) {
            if (!Double.isFinite(fraction) || fraction <= LOW_WATER) {
                return;
            }

            long now = actions.clock().getAsLong();
            if (!panicEpisode) {
                beginEpisode(now);
                return;
            }
            if (now - explicitAttemptAt < policy.diagnosticDelayMs() || now < nextDiagnosticAllowedAt) {
                return;
            }

            actions.diagnosticStart().accept(fraction);
            try {
                actions.diagnosticGc().run();
                failureBackoffMs = policy.initialFailureBackoffMs();
                nextDiagnosticAllowedAt = now + policy.diagnosticCooldownMs();
            } catch (Exception failure) {
                actions.failureSink().accept(
                        "Iris could not run the JVM's diagnostic GC after a normal reclaim did not free enough memory; generation stays paused while the heap is full.",
                        failure);
                nextDiagnosticAllowedAt = now + failureBackoffMs;
                failureBackoffMs = Math.min(policy.maximumFailureBackoffMs(), failureBackoffMs * 2L);
            }
        }

        synchronized void resetEpisode() {
            panicEpisode = false;
            explicitAttemptAt = 0L;
        }

        private void beginEpisode(long now) {
            panicEpisode = true;
            explicitAttemptAt = now;
            try {
                actions.explicitGc().run();
            } catch (RuntimeException failure) {
                actions.failureSink().accept(
                        "Iris could not run a normal heap reclaim; the diagnostic GC will be tried if the heap stays full.",
                        failure);
            }
        }
    }
}
