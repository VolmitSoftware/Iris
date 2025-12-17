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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.MeteredCache;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreservationSVC implements IrisService {
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final List<ExecutorService> services = new CopyOnWriteArrayList<>();
    private final List<WeakReference<MeteredCache>> caches = new CopyOnWriteArrayList<>();
    private Looper dereferencer;

    public void register(Thread t) {
        threads.add(t);
    }

    public void register(ExecutorService service) {
        services.add(service);
    }

    public void printCaches() {
        var c = getCaches();
        long s = 0;
        long m = 0;
        double p = 0;
        double mf = Math.max(c.size(), 1);

        for (MeteredCache i : c) {
            s += i.getSize();
            m += i.getMaxSize();
            p += i.getUsage();
        }

        Iris.info("Cached " + Form.f(s) + " / " + Form.f(m) + " (" + Form.pc(p / mf) + ") from " + caches.size() + " Caches");
    }

    public void dereference() {
        IrisContext.dereference();
        IrisData.dereference();
        threads.removeIf((i) -> !i.isAlive());
        services.removeIf(ExecutorService::isShutdown);
        updateCaches();
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

    public void updateCaches() {
        caches.removeIf(ref -> {
            var c = ref.get();
            return c == null || c.isClosed();
        });
    }

    public void registerCache(MeteredCache cache) {
        caches.add(new WeakReference<>(cache));
    }

    public List<KCache<?, ?>> caches() {
        return cacheStream().map(MeteredCache::getRawCache).collect(Collectors.toList());
    }

    @Unmodifiable
    public List<MeteredCache> getCaches() {
        return cacheStream().toList();
    }

    private Stream<MeteredCache> cacheStream() {
        return caches.stream()
                .map(WeakReference::get)
                .filter(Objects::nonNull)
                .filter(cache -> !cache.isClosed());
    }
}
