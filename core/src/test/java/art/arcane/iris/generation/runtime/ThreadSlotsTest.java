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

import static org.junit.Assert.assertNull;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.ArrayList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class ThreadSlotsTest {
    private static final int THREAD_CHURN = 2_000;

    @Test
    public void sameThreadGetsItsOwnValueBack() {
        ThreadSlots<Object> slots = new ThreadSlots<>(Object::new);

        assertSame(slots.get(), slots.get());
    }

    @Test
    public void otherThreadsGetTheirOwnValues() throws Exception {
        ThreadSlots<Object> slots = new ThreadSlots<>(Object::new);
        Object callerValue = slots.get();
        AtomicReference<Object> workerValue = new AtomicReference<>();

        Thread worker = new Thread(() -> workerValue.set(slots.get()));
        worker.start();
        worker.join();

        assertNotSame(callerValue, workerValue.get());
        assertSame(callerValue, slots.get());
    }

    @Test
    public void workerChurnReusesSlotsOfDeadThreads() throws Exception {
        AtomicInteger created = new AtomicInteger();
        ThreadSlots<Object> slots = new ThreadSlots<>(() -> {
            created.incrementAndGet();
            return new Object();
        });

        for (int index = 0; index < THREAD_CHURN; index++) {
            Thread worker = new Thread(slots::get);
            worker.start();
            worker.join();
        }

        assertEquals(THREAD_CHURN, created.get());
        assertEquals("The table must stay at its fixed size", 256, slotCount(slots));
    }

    @Test
    public void bindingsStayUnsetUntilBoundAndClearOnRemove() {
        ThreadSlots<Object> bindings = ThreadSlots.bindings();
        Object value = new Object();

        assertNull(bindings.get());
        bindings.set(value);
        assertSame(value, bindings.get());
        bindings.remove();
        assertNull(bindings.get());
    }

    @Test
    public void liveThreadsBeyondTheProbedSlotsKeepTheirValues() throws Exception {
        ThreadSlots<Object> slots = new ThreadSlots<>(Object::new);
        int threads = 300;
        CountDownLatch filled = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lost = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            Thread worker = new Thread(() -> {
                Object first = slots.get();
                filled.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (slots.get() != first) {
                    lost.incrementAndGet();
                }
            });
            workers.add(worker);
            worker.start();
        }
        filled.await();
        release.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(0, lost.get());
    }

    private static int slotCount(ThreadSlots<?> slots) throws ReflectiveOperationException {
        Field field = ThreadSlots.class.getDeclaredField("slots");
        field.setAccessible(true);
        return ((AtomicReferenceArray<?>) field.get(slots)).length();
    }
}
