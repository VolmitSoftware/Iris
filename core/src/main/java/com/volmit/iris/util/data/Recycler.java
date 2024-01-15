package com.volmit.iris.util.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Recycler<T> {
    private final List<RecycledObject<T>> pool;
    private final Supplier<T> factory;

    public Recycler(Supplier<T> factory) {
        pool = new CopyOnWriteArrayList<>();
        this.factory = factory;
    }

    public int getFreeObjects() {
        int m = 0;
        RecycledObject<T> o;
        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;

            if (!o.isUsed()) {
                m++;
            }
        }

        return m;
    }

    public int getUsedOjects() {
        int m = 0;
        RecycledObject<T> o;
        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;

            if (o.isUsed()) {
                m++;
            }
        }

        return m;
    }

    public void dealloc(T t) {
        RecycledObject<T> o;

        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;
            if (o.isUsed() && System.identityHashCode(t) == System.identityHashCode(o.getObject())) {
                o.dealloc();
                return;
            }
        }
    }

    public T alloc() {
        RecycledObject<T> o;

        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;
            if (o.alloc()) {
                return o.getObject();
            }
        }

        expand();

        return alloc();
    }

    public boolean contract() {
        return contract(Math.max(getFreeObjects() / 2, Runtime.getRuntime().availableProcessors()));
    }

    public boolean contract(int maxFree) {
        int remove = getFreeObjects() - maxFree;

        if (remove > 0) {
            RecycledObject<T> o;

            for (int i = pool.size() - 1; i > 0; i--) {
                o = pool.get(i);
                if (!o.isUsed()) {
                    pool.remove(i);
                    remove--;

                    if (remove <= 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void expand() {
        if (pool.isEmpty()) {
            expand(Runtime.getRuntime().availableProcessors());
            return;
        }

        expand(getUsedOjects() + Runtime.getRuntime().availableProcessors());
    }

    public void expand(int by) {
        for (int i = 0; i < by; i++) {
            pool.add(new RecycledObject<>(factory.get()));
        }
    }

    public int size() {
        return pool.size();
    }

    public void deallocAll() {
        pool.clear();
    }

    public static class RecycledObject<T> {
        private final T object;
        private final AtomicBoolean used;

        public RecycledObject(T object) {
            this.object = object;
            used = new AtomicBoolean(false);
        }

        public T getObject() {
            return object;
        }

        public boolean isUsed() {
            return used.get();
        }

        public void dealloc() {
            used.set(false);
        }

        public boolean alloc() {
            if (used.get()) {
                return false;
            }

            used.set(true);
            return true;
        }
    }
}
