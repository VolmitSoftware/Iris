package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.Looper;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IrisWorldAnalytics {
    private final ChronoLatch latch;
    private final String world;
    private final AtomicInteger totalChunks;
    private final AtomicInteger processed;
    private final RollingSequence chunksPerSecond;
    private final AtomicLong startTime;
    private final Looper ticker;

    public IrisWorldAnalytics(String world) {
        this.world = world;

        totalChunks = new AtomicInteger();
        processed = new AtomicInteger(0);
        latch = new ChronoLatch(3000);
        chunksPerSecond = new RollingSequence(3000);
        startTime = new AtomicLong(M.ms());
        index();
        ticker = new Looper() {
            @Override
            protected long loop() {



                return 1000;
            }
        };

    }

    public void execute() {
        Iris.info("Starting world analyser..");
        long startTime = System.currentTimeMillis();

    }

    private long computeETA() {
        return (long) (totalChunks.get() > 1024 ? // Generated chunks exceed 1/8th of total?
                // If yes, use smooth function (which gets more accurate over time since its less sensitive to outliers)
                ((totalChunks.get() - processed.get()) * ((double) (M.ms() - startTime.get()) / (double) processed.get())) :
                // If no, use quick function (which is less accurate over time but responds better to the initial delay)
                ((totalChunks.get() - processed.get()) / chunksPerSecond.getAverage()) * 1000
        );
    }

    private void index() {
        try {
            AtomicInteger chunks = new AtomicInteger();
            AtomicInteger pr = new AtomicInteger();
            AtomicInteger pl = new AtomicInteger(0);
            RollingSequence rps = new RollingSequence(5);
            ChronoLatch cl = new ChronoLatch(3000);
            File[] McaFiles = new File(world, "region").listFiles((dir, name) -> name.endsWith(".mca"));
            Supplier<Long> eta = () -> (long) ((McaFiles.length - pr.get()) / rps.getAverage()) * 1000;
            ScheduledFuture<?> sc = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                int sp = pr.get() - pl.get();
                pl.set(pr.get());
                rps.put(sp);
                if (cl.flip()) {
                    double pc = ((double) pr.get() / (double) McaFiles.length) * 100;
                    Iris.info("Indexing: " + Form.f(pr.get()) + " of " + Form.f(McaFiles.length) + " (%.0f%%) " + Form.f((int) rps.getAverage()) + "/s  ETA: " + Form.duration(eta.get(), 2), pc);
                }
            }, 3,1, TimeUnit.SECONDS);
            BurstExecutor b = MultiBurst.burst.burst(McaFiles.length);
            for (File mca : McaFiles) {
                b.queue(() -> {
                    try {
                        MCAFile region = MCAUtil.read(mca, 0);
                        var array = region.getChunks();
                        for (int i = 0; i < array.length(); i++) {
                            if (array.get(i) != null) {
                                chunks.incrementAndGet();
                            }
                        }
                        pr.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            b.complete();
            sc.cancel(true);
            totalChunks.set(chunks.get());
            Iris.info("Indexing completed!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
