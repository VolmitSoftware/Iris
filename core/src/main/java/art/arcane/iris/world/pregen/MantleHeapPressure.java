/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.world.pregen;

import art.arcane.iris.spi.IrisLogging;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

public final class MantleHeapPressure {
    private static final double HIGH_WATER = 0.92D;
    private static final double LOW_WATER = 0.82D;
    private static final long MAXIMUM_HYSTERESIS_MS = 60_000L;
    private static final ObjectName DIAGNOSTIC_COMMAND = diagnosticCommandName();
    private static final PanicGcReclaimer PANIC_RECLAIMER = new PanicGcReclaimer(
            new PanicGcPolicy(10_000L, 60_000L, 60_000L, 15 * 60_000L),
            new PanicGcActions(
                    System::currentTimeMillis,
                    System::gc,
                    () -> invokeHotSpotDiagnosticGc(ManagementFactory.getPlatformMBeanServer()),
                    // An escalation step in the reclaim policy, rate limited by that policy rather than by
                    // anything the operator did. What is actionable is the wait budget being exceeded.
                    (double fraction) -> IrisLogging.info(
                            "Iris heap remained at %.1f%% after normal panic reclaim; invoking the current JVM's diagnostic full GC to keep generation live.",
                            fraction * 100.0D),
                    (String context, Throwable failure) -> IrisLogging.reportError(context, failure)));
    private static final HeapPressureGate PRESSURE_GATE = new HeapPressureGate(
            HIGH_WATER,
            LOW_WATER,
            MAXIMUM_HYSTERESIS_MS,
            System::currentTimeMillis,
            PANIC_RECLAIMER::resetEpisode);

    private MantleHeapPressure() {
    }

    public static double usedFraction() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long max = heap.getMax();
        if (max <= 0L) {
            Runtime runtime = Runtime.getRuntime();
            long runtimeMax = runtime.maxMemory();
            if (runtimeMax <= 0L) {
                return 0.0D;
            }
            long used = runtime.totalMemory() - runtime.freeMemory();
            return (double) used / (double) runtimeMax;
        }
        return (double) heap.getUsed() / (double) max;
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

    public static void requestPanicReclaim() {
        PANIC_RECLAIMER.request(usedFraction());
    }

    static void invokeHotSpotDiagnosticGc(MBeanServer server) throws Exception {
        if (!server.isRegistered(DIAGNOSTIC_COMMAND)) {
            throw new UnsupportedOperationException("HotSpot DiagnosticCommand MBean is not registered on this JVM");
        }
        server.invoke(
                DIAGNOSTIC_COMMAND,
                "gcRun",
                new Object[0],
                new String[0]);
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
        PanicGcPolicy {
            if (diagnosticDelayMs < 0L
                    || diagnosticCooldownMs < 0L
                    || initialFailureBackoffMs < 0L
                    || maximumFailureBackoffMs < initialFailureBackoffMs) {
                throw new IllegalArgumentException("Invalid panic GC timing policy");
            }
        }
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
        private long belowHighWaterSince;

        HeapPressureGate(double highWater, double lowWater, long maximumHysteresisMs, LongSupplier clock, Runnable releaseAction) {
            if (!Double.isFinite(highWater)
                    || !Double.isFinite(lowWater)
                    || lowWater < 0.0D
                    || highWater <= lowWater
                    || maximumHysteresisMs < 0L) {
                throw new IllegalArgumentException("Invalid heap pressure hysteresis policy");
            }
            this.highWater = highWater;
            this.lowWater = lowWater;
            this.maximumHysteresisMs = maximumHysteresisMs;
            this.clock = clock;
            this.releaseAction = releaseAction;
            this.belowHighWaterSince = NOT_BELOW_HIGH_WATER;
        }

        synchronized boolean update(double fraction) {
            if (!Double.isFinite(fraction)) {
                return engaged;
            }
            if (!engaged) {
                if (fraction >= highWater) {
                    engaged = true;
                    belowHighWaterSince = NOT_BELOW_HIGH_WATER;
                }
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
            if (elapsed(now, belowHighWaterSince) < maximumHysteresisMs) {
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

        private static long elapsed(long now, long then) {
            return now >= then ? now - then : Long.MAX_VALUE;
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
                if (fraction < HIGH_WATER) {
                    return;
                }
                beginEpisode(now);
                return;
            }
            if (elapsed(now, explicitAttemptAt) < policy.diagnosticDelayMs()
                    || now < nextDiagnosticAllowedAt) {
                return;
            }

            actions.diagnosticStart().accept(fraction);
            try {
                actions.diagnosticGc().run();
                failureBackoffMs = policy.initialFailureBackoffMs();
                nextDiagnosticAllowedAt = deadline(now, policy.diagnosticCooldownMs());
            } catch (Exception failure) {
                actions.failureSink().accept(
                        "Iris could not invoke the current JVM's DiagnosticCommand GC after normal panic reclaim was ineffective; generation remains pressure-limited to avoid an OOM.",
                        failure);
                nextDiagnosticAllowedAt = deadline(now, failureBackoffMs);
                failureBackoffMs = Math.min(policy.maximumFailureBackoffMs(), doubled(failureBackoffMs));
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
                        "Iris normal panic heap reclaim failed; the diagnostic fallback will be attempted if pressure remains critical.",
                        failure);
            }
        }

        private static long elapsed(long now, long then) {
            return now >= then ? now - then : Long.MAX_VALUE;
        }

        private static long deadline(long now, long delay) {
            return delay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + delay;
        }

        private static long doubled(long value) {
            return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
        }
    }
}
