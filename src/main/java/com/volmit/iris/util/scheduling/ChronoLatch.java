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

public class ChronoLatch {
    private final long interval;
    private long since;

    public ChronoLatch(long interval, boolean openedAtStart) {
        this.interval = interval;
        since = System.currentTimeMillis() - (openedAtStart ? interval * 2 : 0);
    }

    public ChronoLatch(long interval) {
        this(interval, true);
    }

    public void flipDown() {
        since = System.currentTimeMillis();
    }

    public boolean couldFlip() {
        return System.currentTimeMillis() - since > interval;
    }

    public boolean flip() {
        if (System.currentTimeMillis() - since > interval) {
            since = System.currentTimeMillis();
            return true;
        }

        return false;
    }
}
