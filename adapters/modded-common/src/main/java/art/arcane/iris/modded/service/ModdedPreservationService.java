/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.service;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedPreservationService implements ModdedService, PreservationRegistry {
    private static final long DEREFERENCE_INTERVAL_MILLIS = 60000L;

    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final List<ExecutorService> services = new CopyOnWriteArrayList<>();
    private final List<WeakReference<MeteredCache>> caches = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dereferencer;

    @Override
    public void register(Thread thread) {
        threads.add(thread);
    }

    public void register(ExecutorService service) {
        services.add(service);
    }

    @Override
    public void registerCache(MeteredCache cache) {
        caches.add(new WeakReference<>(cache));
    }

    @Override
    public void dereference() {
        IrisData.dereference();
        threads.removeIf((Thread thread) -> !thread.isAlive());
        services.removeIf(ExecutorService::isShutdown);
        caches.removeIf((WeakReference<MeteredCache> ref) -> {
            MeteredCache cache = ref.get();
            return cache == null || cache.isClosed();
        });
    }

    @Override
    public void onEnable() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        dereferencer = new Thread(this::loop, "iris-modded-preservation");
        dereferencer.setDaemon(true);
        dereferencer.start();
    }

    @Override
    public void onDisable() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (dereferencer != null) {
            dereferencer.interrupt();
            dereferencer = null;
        }
        dereference();
        shutdownTrackedResources();
    }

    private void loop() {
        while (running.get()) {
            try {
                Thread.sleep(DEREFERENCE_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            dereference();
        }
    }

    private void shutdownTrackedResources() {
        for (Thread thread : threads) {
            if (!thread.isAlive()) {
                continue;
            }
            try {
                thread.interrupt();
                ModdedIrisLog.info("Iris preservation interrupted thread {}", thread.getName());
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris preservation failed to interrupt thread {}", thread.getName(), error);
            }
        }
        for (ExecutorService service : services) {
            try {
                service.shutdownNow();
                ModdedIrisLog.info("Iris preservation shut down executor {}", service);
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris preservation failed to shut down executor {}", service, error);
            }
        }
    }
}
