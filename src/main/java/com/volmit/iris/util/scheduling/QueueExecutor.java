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

import com.volmit.iris.Iris;

public class QueueExecutor extends Looper {
    private final Queue<Runnable> queue;
    private boolean shutdown;

    public QueueExecutor() {
        queue = new ShurikenQueue<>();
        shutdown = false;
    }

    public Queue<Runnable> queue() {
        return queue;
    }

    @Override
    protected long loop() {
        while (queue.hasNext()) {
            try {
                queue.next().run();
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        }

        if (shutdown && !queue.hasNext()) {
            interrupt();
            return -1;
        }

        return Math.max(500, (long) getRunTime() * 10);
    }

    public double getRunTime() {
        return 0;
    }

    public void shutdown() {
        shutdown = true;
    }
}
