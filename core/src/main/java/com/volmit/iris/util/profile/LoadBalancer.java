package com.volmit.iris.util.profile;


import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.math.M;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Log
@Getter
public class LoadBalancer extends MsptTimings {
    private final Semaphore semaphore;
    private final int maxPermits;
    private final double range;
    @Setter
    private int minMspt, maxMspt;
    private int permits, lastMspt;
    private long lastTime = M.ms();
    private Future<?> future;

    public LoadBalancer(Semaphore semaphore, int maxPermits, IrisSettings.MsRange range) {
        this(semaphore, maxPermits, range.getMin(), range.getMax());
    }

    public LoadBalancer(Semaphore semaphore, int maxPermits, int minMspt, int maxMspt) {
        this.semaphore = semaphore;
        this.maxPermits = maxPermits;
        this.minMspt = minMspt;
        this.maxMspt = maxMspt;
        this.range = maxMspt - minMspt;
        setName("LoadBalancer");
        start();
    }

    @Override
    protected void update(int raw) {
        lastTime = M.ms();
        int mspt = raw;
        if (mspt < lastMspt) {
            int min = (int) Math.max(lastMspt * IrisSettings.get().getUpdater().getChunkLoadSensitivity(), 1);
            mspt = Math.max(mspt, min);
        }
        lastMspt = mspt;
        mspt = Math.max(mspt - minMspt, 0);
        double percent = mspt / range;

        int target = (int) (maxPermits * percent);
        target = Math.min(target, maxPermits - 20);

        int diff = target - permits;
        permits = target;

        if (diff == 0) return;
        log.info("Adjusting load to %s (%s) permits (%s mspt, %.2f)".formatted(target, diff, raw, percent));

        if (diff > 0) semaphore.acquireUninterruptibly(diff);
        else semaphore.release(Math.abs(diff));
    }

    public void close() {
        interrupt();
        semaphore.release(permits);
    }

    public void setRange(IrisSettings.MsRange range) {
        minMspt = range.getMin();
        maxMspt = range.getMax();
    }
}
