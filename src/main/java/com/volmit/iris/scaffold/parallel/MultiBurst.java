/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.Iris;
import com.volmit.iris.util.KList;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MultiBurst {
    public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
    private final ExecutorService service;
    private ExecutorService syncService;
    private int tid;

    public MultiBurst(int tc) {
        service = Executors.newFixedThreadPool(tc, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                tid++;
                Thread t = new Thread(r);
                t.setName("Iris Generator " + tid);
                t.setPriority(6);
                t.setUncaughtExceptionHandler((et, e) ->
                {
                    Iris.info("Exception encountered in " + et.getName());
                    e.printStackTrace();
                });

                return t;
            }
        });
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void burst(KList<Runnable> r) {
        burst(r.size()).queue(r).complete();
    }

    public void sync(Runnable... r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(service, estimate);
    }

    public BurstExecutor burst() {
        return burst(16);
    }

    public void lazy(Runnable o) {
        service.execute(o);
    }

    public void shutdownNow() {
        service.shutdownNow().forEach(Runnable::run);
    }

    public void shutdown() {
        service.shutdown();
    }
}
