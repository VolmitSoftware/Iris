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
 */
public final class ThreadSlots<T> {
    private static final int SLOT_COUNT = 256;
    private static final int SLOT_MASK = SLOT_COUNT - 1;
    private static final int PROBE_LIMIT = 8;
    private static final int NOT_FOUND = -1;

    private final AtomicReferenceArray<Slot<T>> slots = new AtomicReferenceArray<>(SLOT_COUNT);
    private final ConcurrentHashMap<Thread, T> overflow = new ConcurrentHashMap<>();
    private final Supplier<T> factory;

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
        int index = find(thread);
        if (index != NOT_FOUND) {
            return slots.getPlain(index).value();
        }
        T overflowed = overflow.isEmpty() ? null : overflow.get(thread);
        if (overflowed != null || factory == null) {
            return overflowed;
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
            slots.set(index, new Slot<>(thread, value));
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
            slots.set(index, null);
            return;
        }
        if (!overflow.isEmpty()) {
            overflow.remove(thread);
        }
    }

    private int find(Thread thread) {
        int home = home(thread);
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            int index = (home + probe) & SLOT_MASK;
            Slot<T> slot = slots.getPlain(index);
            if (slot != null && slot.owner() == thread) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    private void store(Thread thread, T value) {
        Slot<T> created = new Slot<>(thread, value);
        int home = home(thread);
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            int index = (home + probe) & SLOT_MASK;
            Slot<T> current = slots.get(index);
            if ((current == null || !current.owner().isAlive()) && slots.compareAndSet(index, current, created)) {
                return;
            }
        }
        overflow.keySet().removeIf(owner -> !owner.isAlive());
        overflow.put(thread, value);
    }

    private static int home(Thread thread) {
        return (int) thread.threadId() & SLOT_MASK;
    }

    private record Slot<T>(Thread owner, T value) {
    }
}
