package com.volmit.iris.engine.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.scheduling.Looper;
import lombok.SneakyThrows;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

public class MantleCleanerSVC extends IrisEngineService {
    private static final AtomicInteger tectonicLimit = new AtomicInteger(30);

    static {
        // todo: Redo this
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        while (t > 200) {
            tectonicLimit.incrementAndGet();
            t = t - 200;
        }
    }

    private Ticker trimmer;
    private Ticker unloader;

    public MantleCleanerSVC(Engine engine) {
        super(engine);
    }

    public static int getTectonicLimit() {
        return tectonicLimit.get();
    }

    private static Ticker createTrimmer(Engine engine) {
        return new Ticker(() -> {
            if (engine.isClosed()) return -1;
            long start = M.ms();
            try {
                engine.getMantle().trim(tectonicLimit.get() / getEngineCount());
            } catch (Throwable e) {
                Iris.debug(C.RED + "Mantle: Failed to trim.");
                Iris.reportError(e);
                e.printStackTrace();
            }

            if (engine.isClosed()) return -1;
            int size = getEngineCount();
            return Math.max(1000 / size - (M.ms() - start), 0);
        }, "Iris Mantle Trimmer - " + engine.getWorld().name());
    }

    private static Ticker createUnloader(Engine engine) {
        return new Ticker(() -> {
            if (engine.isClosed()) return -1;
            long start = M.ms();
            try {
                engine.getMantle().unloadTectonicPlate(tectonicLimit.get() / getEngineCount());
            } catch (Throwable e) {
                Iris.debug(C.RED + "Mantle: Failed to unload.");
                Iris.reportError(e);
                e.printStackTrace();
            }

            if (engine.isClosed()) return -1;
            int size = getEngineCount();
            return Math.max(1000 / size - (M.ms() - start), 0);
        }, "Iris Mantle Unloader - " + engine.getWorld().name());
    }

    private static int getEngineCount() {
        return Math.max(EngineStatusSVC.getEngineCount(), 1);
    }

    @Override
    public void onEnable(boolean hotload) {
        if (engine.isStudio() && !IrisSettings.get().getPerformance().trimMantleInStudio)
            return;
        if (trimmer == null || !trimmer.isAlive())
            trimmer = createTrimmer(engine);
        if (unloader == null || !unloader.isAlive())
            unloader = createUnloader(engine);
    }

    @Override
    public void onDisable(boolean hotload) {
        if (hotload) return;
        if (trimmer != null) trimmer.await();
        if (unloader != null) unloader.await();
    }

    private static class Ticker extends Looper {
        private final LongSupplier supplier;

        private Ticker(LongSupplier supplier, String name) {
            this.supplier = supplier;
            setPriority(Thread.MIN_PRIORITY);
            setName(name);
            start();
        }

        @Override
        protected long loop() {
            try {
                return supplier.getAsLong();
            } catch (Throwable e) {
                Iris.error("Exception in Looper " + getName());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Iris.error(sw.toString());
                return 3000;
            }
        }

        @SneakyThrows
        public void await() {
            join();
        }
    }
}
