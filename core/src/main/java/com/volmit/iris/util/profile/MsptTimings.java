package com.volmit.iris.util.profile;

import com.volmit.iris.Iris;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.Task;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class MsptTimings extends Looper {
    private final AtomicInteger currentTick = new AtomicInteger(0);
    private int lastTick, lastMspt;
    private long lastTime;
    private Task task = null;

    public MsptTimings() {
        setName("MsptTimings");
        setPriority(9);
        setDaemon(true);
    }

    public static MsptTimings of(Consumer<Integer> update) {
        return new Simple(update);
    }

    @Override
    protected final long loop() {
        if (startTickTask())
            return 200;

        long now = M.ms();
        int tick = currentTick.get();
        int deltaTick = tick - lastTick;
        if (deltaTick == 0)
            return 200;
        lastTick = tick;
        int deltaTime = (int) (now - lastTime);
        lastTime = now;
        int mspt = deltaTime / deltaTick;
        mspt -= 50;
        mspt = Math.max(mspt, 0);
        lastMspt = mspt;
        update(mspt);
        return 200;
    }

    public final int getMspt() {
        return lastMspt;
    }

    protected abstract void update(int mspt);

    private boolean startTickTask() {
        if (task != null && !task.cancelled())
            return false;

        task = Iris.scheduler.global().runAtFixedRate(t -> {
            if (isInterrupted()) {
                t.cancel();
                return;
            }

            currentTick.incrementAndGet();
        }, 1, 1);
        return true;
    }

    private static class Simple extends MsptTimings {
        private final Consumer<Integer> update;

        private Simple(Consumer<Integer> update) {
            this.update = update;
            start();
        }

        @Override
        protected void update(int mspt) {
            if (update == null)
                return;
            update.accept(mspt);
        }
    }
}
