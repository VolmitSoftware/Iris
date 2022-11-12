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

package com.volmit.iris.util.scheduling.jobs;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;

public abstract class ParallelQueueJob<T> extends QueueJob<T> {
    @Override
    public void execute() {
        while (queue.isNotEmpty()) {
            BurstExecutor b = MultiBurst.burst.burst(queue.size());
            KList<T> q = queue.copy();
            queue.clear();
            for (T i : q) {
                b.queue(() -> {
                    execute(i);
                    completeWork();
                });
            }
            b.complete();
        }
    }
}
