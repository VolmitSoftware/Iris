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

package com.volmit.iris.util.format;

import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.Looper;

public class MemoryMonitor {
    private final ChronoLatch cl;
    private final RollingSequence pressureAvg;
    private final Runtime runtime;
    private Looper looper;
    private long usedMemory;
    private long garbageMemory;
    private long garbageLast;
    private long garbageBin;
    private long pressure;

    public MemoryMonitor(int sampleDelay) {
        this.runtime = Runtime.getRuntime();
        usedMemory = -1;
        pressureAvg = new RollingSequence(Math.max(Math.min(100, 1000 / sampleDelay), 3));
        garbageBin = 0;
        garbageMemory = -1;
        cl = new ChronoLatch(1000);
        garbageLast = 0;
        pressure = 0;

        looper = new Looper() {
            @Override
            protected long loop() {
                sample();
                return sampleDelay;
            }
        };
        looper.setPriority(Thread.MIN_PRIORITY);
        looper.setName("Memory Monitor");
        looper.start();
    }

    public long getGarbageBytes() {
        return garbageMemory;
    }

    public long getUsedBytes() {
        return usedMemory;
    }

    public long getMaxBytes() {
        return runtime.maxMemory();
    }

    public long getPressure() {
        return (long) pressureAvg.getAverage();
    }

    public double getUsagePercent() {
        return usedMemory / (double) getMaxBytes();
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    private void sample() {
        long used = getVMUse();
        if (usedMemory == -1) {
            usedMemory = used;
            garbageMemory = 0;
            return;
        }

        if (used < usedMemory) {
            usedMemory = used;
        } else {
            garbageMemory = used - usedMemory;
        }

        long g = garbageMemory - garbageLast;

        if (g >= 0) {
            garbageBin += g;
            garbageLast = garbageMemory;
        } else {
            garbageMemory = 0;
            garbageLast = 0;
        }

        if (cl.flip()) {
            if (garbageMemory > 0) {
                pressure = garbageBin;
                garbageBin = 0;
            } else {
                pressure = 0;
                garbageBin = 0;
            }
        }

        pressureAvg.put(pressure);
    }

    private long getVMUse() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public void close() {
        if (looper != null) {
            looper.interrupt();
            looper = null;
        }
    }
}
