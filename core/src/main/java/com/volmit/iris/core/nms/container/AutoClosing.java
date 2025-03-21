package com.volmit.iris.core.nms.container;

import com.volmit.iris.util.function.NastyRunnable;
import lombok.AllArgsConstructor;

import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor
public class AutoClosing implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final NastyRunnable action;

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;
        try {
            action.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
