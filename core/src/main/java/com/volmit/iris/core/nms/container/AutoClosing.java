package com.volmit.iris.core.nms.container;

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.function.NastyRunnable;
import lombok.AllArgsConstructor;

import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor
public class AutoClosing implements AutoCloseable {
    private static final KMap<Thread, AutoClosing> CONTEXTS = new KMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final NastyRunnable action;

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;
        try {
            removeContext();
            action.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void storeContext() {
        CONTEXTS.put(Thread.currentThread(), this);
    }

    public void removeContext() {
        CONTEXTS.values().removeIf(c -> c == this);
    }

    public static void closeContext() {
        AutoClosing closing = CONTEXTS.remove(Thread.currentThread());
        if (closing == null) return;
        closing.close();
    }
}
