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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;

import java.util.concurrent.ExecutorService;

public class PreservationSVC implements IrisService {
    private final KList<Thread> threads = new KList<>();
    private final KList<ExecutorService> services = new KList<>();
    private Looper dereferencer;

    public void register(Thread t) {
        threads.add(t);
    }

    public void register(MultiBurst burst) {

    }

    public void register(ExecutorService service) {
        services.add(service);
    }

    public void dereference() {
        IrisContext.dereference();
        IrisData.dereference();
    }

    @Override
    public void onEnable() {
        /*
         * Dereferences copies of Engine instances that are closed to prevent memory from
         * hanging around and keeping copies of complex, caches and other dead objects.
         */
        dereferencer = new Looper() {
            @Override
            protected long loop() {
                dereference();
                return 60000;
            }
        };
    }

    @Override
    public void onDisable() {
        dereferencer.interrupt();
        dereference();

        postShutdown(() -> {
            for (Thread i : threads) {
                if (i.isAlive()) {
                    try {
                        i.interrupt();
                        Iris.info("Shutdown Thread " + i.getName());
                    } catch (Throwable e) {
                        Iris.reportError(e);
                    }
                }
            }

            for (ExecutorService i : services) {
                try {
                    i.shutdownNow();
                    Iris.info("Shutdown Executor Service " + i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                }
            }
        });
    }
}
