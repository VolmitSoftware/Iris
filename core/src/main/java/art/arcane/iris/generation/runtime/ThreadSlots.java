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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * Per-thread values owned by the object that holds this table, typically an engine-owned runtime. Unlike a
 * ThreadLocal, the values live here and not in the worker threads, so they become unreachable with their owner even
 * while the pooled workers that filled them stay alive. The table has a fixed size: a slot whose thread has died is
 * reused, and when every probed slot belongs to a live thread the first one is taken over, which only costs that
 * thread its memo.
 */
public final class ThreadSlots<T> {
    private static final int SLOT_COUNT = 256;
    private static final int SLOT_MASK = SLOT_COUNT - 1;
    private static final int PROBE_LIMIT = 8;

    private final AtomicReferenceArray<Slot<T>> slots = new AtomicReferenceArray<>(SLOT_COUNT);
    private final Supplier<T> factory;

    public ThreadSlots(Supplier<T> factory) {
        this.factory = Objects.requireNonNull(factory, "thread value factory");
    }

    public T get() {
        Thread thread = Thread.currentThread();
        int home = (int) thread.threadId() & SLOT_MASK;
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            Slot<T> slot = slots.getPlain((home + probe) & SLOT_MASK);
            if (slot != null && slot.owner == thread) {
                return slot.value;
            }
        }
        return claim(thread, home);
    }

    private T claim(Thread thread, int home) {
        Slot<T> created = new Slot<>(thread, factory.get());
        for (int probe = 0; probe < PROBE_LIMIT; probe++) {
            int index = (home + probe) & SLOT_MASK;
            Slot<T> current = slots.get(index);
            if ((current == null || !current.owner.isAlive()) && slots.compareAndSet(index, current, created)) {
                return created.value;
            }
        }
        slots.set(home, created);
        return created.value;
    }

    private record Slot<T>(Thread owner, T value) {
    }
}
