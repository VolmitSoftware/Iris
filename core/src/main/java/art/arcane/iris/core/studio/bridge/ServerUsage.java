/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.bridge;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * What the server asks of its machine: CPU counted like {@code top}, where 100 is one thread kept busy, and the Java
 * heap in use.
 *
 * @param cpuPercent    the process's CPU use with 100 per busy thread, or null when the JVM cannot tell
 * @param threads       the threads the server's CPU has, so the studio can show the most {@code cpuPercent} can reach
 * @param heapUsedBytes the Java heap in use
 * @param heapMaxBytes  the most the Java heap can grow to
 */
record ServerUsage(Double cpuPercent, int threads, long heapUsedBytes, long heapMaxBytes) {
    private static final double PERCENT_PER_THREAD = 100D;

    static ServerUsage sample() {
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
        return of(processCpuLoad(system), runtime.availableProcessors(), runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory());
    }

    /** @param processCpuLoad the share of every core the process uses, or below zero when unknown */
    static ServerUsage of(double processCpuLoad, int threads, long heapUsedBytes, long heapMaxBytes) {
        Double cpuPercent = processCpuLoad < 0D ? null : processCpuLoad * threads * PERCENT_PER_THREAD;
        return new ServerUsage(cpuPercent, threads, heapUsedBytes, heapMaxBytes);
    }

    private static double processCpuLoad(OperatingSystemMXBean system) {
        return system instanceof com.sun.management.OperatingSystemMXBean hotspot ? hotspot.getProcessCpuLoad() : -1D;
    }
}
