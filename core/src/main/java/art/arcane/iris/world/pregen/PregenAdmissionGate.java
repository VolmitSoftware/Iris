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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class PregenAdmissionGate {
    public record Wait(long adaptiveMs, long permitMs) {
    }

    private final int permits;
    private final long boundMs;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition changed = lock.newCondition();
    private int free;
    private volatile int freeSnapshot;

    public PregenAdmissionGate(int permits, long boundMs, LongSupplier clock) {
        if (permits < 1) {
            throw new IllegalArgumentException("A pregen admission gate needs at least one permit");
        }
        if (boundMs < 1L) {
            throw new IllegalArgumentException("A pregen admission gate needs a positive wait bound");
        }
        this.permits = permits;
        this.boundMs = boundMs;
        this.clock = clock;
        this.free = permits;
        this.freeSnapshot = permits;
    }

    public int permits() {
        return permits;
    }

    public int availablePermits() {
        return freeSnapshot;
    }

    public Wait admit(BooleanSupplier overAdaptiveLimit, BooleanSupplier cancelled) throws InterruptedException {
        long adaptiveMs = 0L;
        long permitMs = 0L;
        lock.lock();
        try {
            while (true) {
                if (cancelled.getAsBoolean()) {
                    return null;
                }

                boolean blockedByLimit = overAdaptiveLimit.getAsBoolean();
                if (!blockedByLimit && free > 0) {
                    free--;
                    freeSnapshot = free;
                    return new Wait(adaptiveMs, permitMs);
                }

                long start = clock.getAsLong();
                changed.await(boundMs, TimeUnit.MILLISECONDS);
                long waited = Math.max(0L, clock.getAsLong() - start);
                if (blockedByLimit) {
                    adaptiveMs += waited;
                } else {
                    permitMs += waited;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            if (free < permits) {
                free++;
                freeSnapshot = free;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void wake() {
        lock.lock();
        try {
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public boolean awaitDrain(long warningInterval, TimeUnit timeUnit, Runnable onWait) {
        boolean interrupted = false;
        lock.lock();
        try {
            while (free < permits) {
                boolean signalled = false;
                try {
                    signalled = changed.await(warningInterval, timeUnit);
                } catch (InterruptedException e) {
                    interrupted = true;
                    continue;
                }
                if (!signalled && free < permits) {
                    onWait.run();
                }
            }
        } finally {
            lock.unlock();
        }

        return interrupted;
    }
}
