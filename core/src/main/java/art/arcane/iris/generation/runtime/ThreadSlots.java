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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * Per-thread values owned by the object that holds this table, typically an engine-owned runtime. Unlike a
 * ThreadLocal, the values live here and not in the worker threads, so they become unreachable with their owner even
 * while the pooled workers that filled them stay alive. A live thread never loses its value: a thread whose probed
 * slots all belong to other live threads keeps its value in an overflow map, which drops dead threads whenever it
 * takes a new one. The size therefore follows the live threads and not the worker churn.
 * <p>
 * Owners and values sit in parallel arrays so the common lookup, a thread in its home slot, is one owner compare and
 * one value read that do not depend on each other. Only the owning thread writes a slot's value, and a slot changes
 * owner only once its previous owner removed its value or died, so an owner match always pairs with that owner's
 * value. Until some thread has had to store away from its home slot, a thread missing from its home slot has no
 * value at all, so unbound threads skip the probe too.
 */
public final class ThreadSlots<T> {
    private static final int SLOT_COUNT = 256;
    private static final int SLOT_MASK = SLOT_COUNT - 1;
    private static final int PROBE_LIMIT = 8;
    private static final int NOT_FOUND = -1;

    private final AtomicReferenceArray<Thread> owners = new AtomicReferenceArray<>(SLOT_COUNT);
    private final AtomicReferenceArray<T> values = new AtomicReferenceArray<>(SLOT_COUNT);
    private final ConcurrentHashMap<Thread, T> overflow = new ConcurrentHashMap<>();
    private final Supplier<T> factory;
    private volatile boolean storedAwayFromHome;

    /** A table whose threads each get a value from the factory on first use. */
    public ThreadSlots(Supplier<T> factory) {
        this.factory = Objects.requireNonNull(factory, "thread value factory");
    }

    private ThreadSlots() {
        this.factory = null;
    }

    /** A table of values each thread binds with {@link #set} and clears with {@link #remove}. */
    public static <T> ThreadSlots<T> bindings() {
        return new ThreadSlots<>();
    }

    /** This thread's value, created by the factory on first use; null for an unbound thread of a bindings table. */
    public T get() {
        Thread thread = Thread.currentThread();
        int home = home(thread);
        if (owners.getPlain(home) == thread) {
            return values.getPlain(home);
        }
        T awayFromHome = storedAwayFromHome ? getAwayFromHome(thread) : null;
        if (awayFromHome != null || factory == null) {
            return awayFromHome;
        }
        T created = factory.get();
        store(thread, created);
        return created;
    }

    public void set(T value) {
        if (value == null) {
            remove();
            return;
        }
        Thread thread = Thread.currentThread();
        int index = find(thread);
        if (index != NOT_FOUND) {
            values.set(index, value);
            return;
        }
        if (overflow.replace(thread, value) == null) {
            store(thread, value);
        }
    }

    public void remove() {
        Thread thread = Thread.currentThread();
        int index = find(thread);
        if (index != NOT_FOUND) {
            values.set(index, null);
            owners.set(index, null);
            return;
        }
        if (!overflow.isEmpty()) {
            overflow.remove(thread);
        }
    }

    private T getAwayFromHome(Thread thread) {
        int index = find(thread);
        if (index != NOT_FOUND) {
            return values.getPlain(index);
        }
        return overflow.isEmpty() ? null : overflow.get(thread);
    }

    private int find(Thread thread) {
        int home = home(thread);
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            int index = (home + probe) & SLOT_MASK;
            if (owners.getPlain(index) == thread) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    private void store(Thread thread, T value) {
        int home = home(thread);
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            int index = (home + probe) & SLOT_MASK;
            Thread current = owners.get(index);
            if ((current == null || !current.isAlive()) && owners.compareAndSet(index, current, thread)) {
                values.set(index, value);
                if (probe > 0) {
                    storedAwayFromHome = true;
                }
                return;
            }
        }
        storedAwayFromHome = true;
        overflow.keySet().removeIf(owner -> !owner.isAlive());
        overflow.put(thread, value);
    }

    private static int home(Thread thread) {
        return (int) thread.threadId() & SLOT_MASK;
    }
}
