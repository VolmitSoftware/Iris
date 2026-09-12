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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.data.KCache;
import art.arcane.volmlib.util.format.Form;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.volmlib.util.scheduling.Looper;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreservationSVC implements IrisService, PreservationRegistry {
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
        List<MeteredCache> c = getCaches();
        long s = 0;
        long m = 0;
        double p = 0;
        double mf = Math.max(c.size(), 1);

        for (MeteredCache i : c) {
            s += i.getSize();
            m += i.getMaxSize();
            p += i.getUsage();
        }

        IrisLogging.debug("Cached " + Form.f(s) + " / " + Form.f(m) + " (" + Form.pc(p / mf) + ") from " + caches.size() + " Caches");
    }

    public void dereference() {
        IrisData.dereference();
        threads.removeIf((i) -> !i.isAlive());
        services.removeIf(ExecutorService::isTerminated);
        updateCaches();
    }

    public boolean hasActiveResources() {
        if (dereferencer != null && dereferencer.isAlive()) {
            return true;
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return true;
            }
        }
        for (ExecutorService executor : services) {
            if (!executor.isTerminated()) {
                return true;
            }
        }
        return false;
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
        dereferencer.setName("Iris Preservation");
        dereferencer.setDaemon(true);
        dereferencer.setPriority(Thread.MIN_PRIORITY);
        dereferencer.start();
    }

    @Override
    public void onDisable() {
        if (dereferencer != null) {
            dereferencer.interrupt();
        }
        dereference();

        postShutdown(() -> {
            for (Thread i : threads) {
                if (i.isAlive()) {
                    try {
                        i.interrupt();
                        IrisLogging.debug("Shutdown Thread " + i.getName());
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                    }
                }
            }

            for (ExecutorService i : services) {
                try {
                    i.shutdownNow();
                    IrisLogging.debug("Shutdown Executor Service " + i);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }
        });
    }

    public void updateCaches() {
        caches.removeIf(ref -> {
            MeteredCache c = ref.get();
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
