package com.volmit.iris.server.util;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.Looper;
import lombok.Setter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CPSLooper extends Looper {
    private final RollingSequence chunksPerSecond = new RollingSequence(10);
    private final AtomicInteger generated = new AtomicInteger();
    private final AtomicInteger generatedLast = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final IrisConnection connection;
    private int nodeCount = 0;

    public CPSLooper(String name, IrisConnection connection) {
        this.connection = connection;
        setName(name);
        setPriority(Thread.MAX_PRIORITY);
        start();
    }

    public void addChunks(int count) {
        generated.addAndGet(count);
    }

    public void exit() {
        running.set(false);
    }

    public synchronized void setNodeCount(int count) {
        if (nodeCount != 0 || count < 1)
            return;
        nodeCount = count;
        Packets.INFO.newPacket()
                .setNodeCount(nodeCount)
                .send(connection);
    }

    @Override
    protected long loop() {
        if (!running.get())
            return -1;
        long t = M.ms();

        int secondGenerated = generated.get() - generatedLast.get();
        generatedLast.set(generated.get());
        chunksPerSecond.put(secondGenerated);

        if (secondGenerated > 0 && nodeCount > 0) {
            Packets.INFO.newPacket()
                    .setNodeCount(nodeCount)
                    .setCps((int) Math.round(chunksPerSecond.getAverage()))
                    .setGenerated(secondGenerated)
                    .send(connection);
        }

        return Math.max(5000 - (M.ms() - t), 0);
    }
}
