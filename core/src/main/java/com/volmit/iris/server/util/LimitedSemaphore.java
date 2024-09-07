package com.volmit.iris.server.util;

import lombok.Getter;

import java.util.concurrent.Semaphore;

@Getter
public class LimitedSemaphore extends Semaphore {
    private final int permits;

    public LimitedSemaphore(int permits) {
        super(permits);
        this.permits = permits;
    }

    public void runBlocking(Runnable runnable) throws InterruptedException {
        try {
            acquire();
            runnable.run();
        } finally {
            release();
        }
    }

    public void runAllBlocking(Runnable runnable) throws InterruptedException {
        try {
            acquireAll();
            runnable.run();
        } finally {
            releaseAll();
        }
    }

    public void acquireAll() throws InterruptedException {
        acquire(permits);
    }

    public void releaseAll() {
        release(permits);
    }
}
