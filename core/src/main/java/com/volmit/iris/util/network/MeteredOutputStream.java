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

package com.volmit.iris.util.network;

import java.io.IOException;
import java.io.OutputStream;

public class MeteredOutputStream extends OutputStream {
    private final OutputStream os;
    private long written;
    private long totalWritten;
    private long since;
    private boolean auto;
    private long interval;
    private long bps;

    public MeteredOutputStream(OutputStream os, long interval) {
        this.os = os;
        written = 0;
        totalWritten = 0;
        auto = true;
        this.interval = interval;
        bps = 0;
        since = System.currentTimeMillis();
    }

    public MeteredOutputStream(OutputStream os) {
        this(os, 100);
        auto = false;
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
        written++;
        totalWritten++;

        if (auto && System.currentTimeMillis() - getSince() > interval) {
            pollWritten();
        }
    }

    public long getSince() {
        return since;
    }

    public long getWritten() {
        return written;
    }

    public long pollWritten() {
        long w = written;
        written = 0;
        double secondsElapsedSince = (double) (System.currentTimeMillis() - since) / 1000.0;
        bps = (long) ((double) w / secondsElapsedSince);
        since = System.currentTimeMillis();
        return w;
    }

    public void close() throws IOException {
        os.close();
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public long getTotalWritten() {
        return totalWritten;
    }

    public long getBps() {
        return bps;
    }
}