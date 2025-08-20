package com.volmit.iris.util.scheduling.jobs;

import com.volmit.iris.Iris;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.BlockVector;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ScanJob implements Job {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicInteger completed = new AtomicInteger();
    private final String name;
    private final Cuboid cuboid;
    private final BiConsumer<BlockVector, Block> action;
    private final int msPerTick, total;
    private volatile Chunk chunk;

    public ScanJob(String name,
                   Cuboid cuboid,
                   int msPerTick,
                   BiConsumer<BlockVector, Block> action
    ) {
        this.name = name;
        this.cuboid = cuboid;
        this.action = action;
        this.msPerTick = msPerTick;
        total = cuboid.volume();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void execute() {
        Thread.ofVirtual()
                .name("Iris Job - " + name)
                .start(this::executeTask);
        await();
    }

    public void await() {
        try {
            latch.await();
        } catch (InterruptedException ignored) {}
    }

    private void executeTask() {
        var it = cuboid.chunkedIterator();
        var region = Iris.platform.getRegionScheduler();

        var tmp = it.next();
        while (tmp != null) {
            Location finalTmp = tmp;
            tmp = region.run(tmp, () -> {
                var time = M.ms() + msPerTick;
                var next = finalTmp;

                while (time > M.ms()) {
                    if (!consume(next)) break;
                    completed.incrementAndGet();
                    next = it.hasNext() ? it.next() : null;
                }

                return next;
            }).getResult().join();
        }

        latch.countDown();
    }

    private boolean consume(Location next) {
        if (!Iris.platform.isOwnedByCurrentRegion(next))
            return true;

        final Chunk nextChunk = next.getChunk();
        if (chunk == null) {
            chunk.removePluginChunkTicket(Iris.instance);
            chunk = next.getChunk();
        } else if (chunk != nextChunk) {
            chunk.removePluginChunkTicket(Iris.instance);
            chunk = nextChunk;
            chunk.addPluginChunkTicket(Iris.instance);
        }

        final Block block = next.getBlock();
        action.accept(next.subtract(cuboid.getLowerNE().toVector()).toVector().toBlockVector(), block);
        return false;
    }

    @Override
    public void completeWork() {
    }

    @Override
    public int getTotalWork() {
        return total;
    }

    @Override
    public int getWorkCompleted() {
        return completed.get();
    }

    @Override
    public void execute(VolmitSender sender, boolean silentMsg, Runnable whenComplete) {
        Job.super.execute(sender, silentMsg, whenComplete);
        await();
    }
}
