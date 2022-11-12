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

package com.volmit.iris.util.parallel;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.function.NastyRunnable;
import com.volmit.iris.util.io.IORunnable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class HyperLock {
    private final ConcurrentLinkedHashMap<Long, ReentrantLock> locks;
    private boolean enabled = true;
    private boolean fair = false;

    public HyperLock() {
        this(1024, false);
    }

    public HyperLock(int capacity) {
        this(capacity, false);
    }

    public HyperLock(int capacity, boolean fair) {
        this.fair = fair;
        locks = new ConcurrentLinkedHashMap.Builder<Long, ReentrantLock>()
                .initialCapacity(capacity)
                .maximumWeightedCapacity(capacity)
                .listener((k, v) -> {
                    if (v.isLocked() || v.isHeldByCurrentThread()) {
                        Iris.warn("InfiniLock Eviction of " + k + " still has locks on it!");
                    }
                })
                .concurrencyLevel(32)
                .build();
    }

    public void with(int x, int z, Runnable r) {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public void withLong(long k, Runnable r) {
        lock(Cache.keyX(k), Cache.keyZ(k));
        r.run();
        unlock(Cache.keyX(k), Cache.keyZ(k));
    }

    public void withNasty(int x, int z, NastyRunnable r) throws Throwable {
        lock(x, z);
        Throwable ee = null;
        try {
            r.run();
        } catch (Throwable e) {
            ee = e;
        } finally {
            unlock(x, z);

            if (ee != null) {
                throw ee;
            }
        }
    }

    public void withIO(int x, int z, IORunnable r) throws IOException {
        lock(x, z);
        IOException ee = null;
        try {
            r.run();
        } catch (IOException e) {
            ee = e;
        } finally {
            unlock(x, z);

            if (ee != null) {
                throw ee;
            }
        }
    }

    public <T> T withResult(int x, int z, Supplier<T> r) {
        lock(x, z);
        T t = r.get();
        unlock(x, z);
        return t;
    }

    public boolean tryLock(int x, int z) {
        return getLock(x, z).tryLock();
    }

    public boolean tryLock(int x, int z, long timeout) {
        try {
            return getLock(x, z).tryLock(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Iris.reportError(e);
        }

        return false;
    }

    private ReentrantLock getLock(int x, int z) {
        return locks.computeIfAbsent(Cache.key(x, z), k -> new ReentrantLock(fair));
    }

    public void lock(int x, int z) {
        if (!enabled) {
            return;
        }

        getLock(x, z).lock();
    }

    public void unlock(int x, int z) {
        if (!enabled) {
            return;
        }

        getLock(x, z).unlock();
    }

    public void disable() {
        enabled = false;
    }
}
