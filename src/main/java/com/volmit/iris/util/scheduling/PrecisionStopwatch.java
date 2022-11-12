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

package com.volmit.iris.util.scheduling;

public class PrecisionStopwatch {
    private long nanos;
    private long startNano;
    private long millis;
    private long startMillis;
    private double time;
    private boolean profiling;

    public PrecisionStopwatch() {
        reset();
        profiling = false;
    }

    public static PrecisionStopwatch start() {
        PrecisionStopwatch p = new PrecisionStopwatch();
        p.begin();

        return p;
    }

    public void begin() {
        profiling = true;
        startNano = System.nanoTime();
        startMillis = System.currentTimeMillis();
    }

    public void end() {
        if (!profiling) {
            return;
        }

        profiling = false;
        nanos = System.nanoTime() - startNano;
        millis = System.currentTimeMillis() - startMillis;
        time = (double) nanos / 1000000.0;
        time = (double) millis - time > 1.01 ? millis : time;
    }

    public void reset() {
        nanos = -1;
        millis = -1;
        startNano = -1;
        startMillis = -1;
        time = -0;
        profiling = false;
    }

    public double getTicks() {
        return getMilliseconds() / 50.0;
    }

    public double getSeconds() {
        return getMilliseconds() / 1000.0;
    }

    public double getMinutes() {
        return getSeconds() / 60.0;
    }

    public double getHours() {
        return getMinutes() / 60.0;
    }

    public double getMilliseconds() {
        nanos = System.nanoTime() - startNano;
        millis = System.currentTimeMillis() - startMillis;
        time = (double) nanos / 1000000.0;
        time = (double) millis - time > 1.01 ? millis : time;
        return time;
    }

    public long getNanoseconds() {
        return (long) (time * 1000000.0);
    }

    public long getNanos() {
        return nanos;
    }

    public long getStartNano() {
        return startNano;
    }

    public long getMillis() {
        return millis;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public double getTime() {
        return time;
    }

    public boolean isProfiling() {
        return profiling;
    }

    public void rewind(long l) {
        startMillis -= l;
    }
}
