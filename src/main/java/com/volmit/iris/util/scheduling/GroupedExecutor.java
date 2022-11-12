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
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.function.NastyRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

public class GroupedExecutor {
    private final ExecutorService service;
    private final KMap<String, Integer> mirror;
    private int xc;

    public GroupedExecutor(int threadLimit, int priority, String name) {
        xc = 1;
        mirror = new KMap<>();

        if (threadLimit == 1) {
            service = Executors.newSingleThreadExecutor((r) ->
            {
                Thread t = new Thread(r);
                t.setName(name);
                t.setPriority(priority);

                return t;
            });
        } else if (threadLimit > 1) {
            final ForkJoinWorkerThreadFactory factory = pool -> {
                final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName(name + " " + xc++);
                worker.setPriority(priority);
                return worker;
            };

            service = new ForkJoinPool(threadLimit, factory, null, false);
        } else {
            service = Executors.newCachedThreadPool((r) ->
            {
                Thread t = new Thread(r);
                t.setName(name + " " + xc++);
                t.setPriority(priority);

                return t;
            });
        }
    }

    public void waitFor(String g) {
        if (g == null) {
            return;
        }

        if (!mirror.containsKey(g)) {
            return;
        }

        while (true) {
            if (mirror.get(g) == 0) {
                break;
            }
        }
    }

    public void queue(String q, NastyRunnable r) {
        mirror.compute(q, (k, v) -> k == null || v == null ? 1 : v + 1);
        service.execute(() ->
        {
            try {
                r.run();
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }

            mirror.computeIfPresent(q, (k, v) -> v - 1);
        });
    }

    public void close() {
        J.a(() ->
        {
            J.sleep(100);
            service.shutdown();
        });
    }

    public void closeNow() {
        service.shutdown();
    }
}
