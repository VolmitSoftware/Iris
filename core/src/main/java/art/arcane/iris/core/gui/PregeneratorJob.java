/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.gui;

import art.arcane.iris.core.localization.DesktopUiMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.core.pregenerator.IrisPregenerator;
import art.arcane.iris.core.pregenerator.PregenApiPhase;
import art.arcane.iris.core.pregenerator.PregenApiSink;
import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregenPhaseTracker;
import art.arcane.iris.core.pregenerator.PregenRates;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.format.MemoryMonitor;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.iris.util.common.scheduling.J;

import java.awt.Color;
import java.awt.EventQueue;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class PregeneratorJob implements PregenListener, PregenRenderSource {
    // Must exceed the worker's own worst-case teardown budget (AsyncPregenMethod close:
    // 60s permit drain + 120s flush + plate reclaim), or a routine drain trips the deadline
    // and aborts the engine shutdown sequence mid-teardown.
    private static final long WORLD_SHUTDOWN_TIMEOUT_MILLIS = 200_000L;
    private static final Color COLOR_EXISTS = parseColor("#4d7d5b");
    private static final Color COLOR_BLACK = parseColor("#4d7d5b");
    private static final Color COLOR_MANTLE = parseColor("#3c2773");
    private static final Color COLOR_GENERATING = parseColor("#66967f");
    private static final Color COLOR_NETWORK = parseColor("#a863c2");
    private static final Color COLOR_NETWORK_GENERATING = parseColor("#836b8c");
    private static final Color COLOR_GENERATED = parseColor("#65c295");
    private static final Color COLOR_CLEANED = parseColor("#34eb93");
    private static final AtomicReference<PregeneratorJob> instance = new AtomicReference<>();
    private final MemoryMonitor monitor;
    private final PregenTask task;
    private final AtomicBoolean saving;
    private final AtomicBoolean stopRequested;
    private final List<Consumer<Double>> onProgress = new CopyOnWriteArrayList<>();
    private final List<Runnable> whenDone = new CopyOnWriteArrayList<>();
    private final IrisPregenerator pregenerator;
    private final Position2 min;
    private final Position2 max;
    private final Engine engine;
    private final ExecutorService service;
    private final Thread worker;
    private final PregenPhaseTracker apiPhases = new PregenPhaseTracker();
    private PregenRenderer renderer;
    private String[] info;
    private volatile double lastChunksPerSecond = 0D;
    private volatile double lastOverallChunksPerSecond = 0D;
    private volatile double lastThirtySecondChunksPerSecond = 0D;
    private volatile double lastSixtySecondChunksPerSecond = 0D;
    private volatile long lastChunksRemaining = 0L;
    private volatile long lastGenerated = 0L;
    private volatile long lastTotalChunks = 0L;
    private volatile long lastEta = 0L;
    private volatile long lastElapsed = 0L;
    private volatile String lastMethod = IrisLanguage.plain(DesktopUiMessages.PREGEN_METHOD_PENDING);

    public PregeneratorJob(Configuration configuration) {
        PregenTask task = configuration.task();
        PregeneratorMethod method = configuration.method();
        Engine engine = configuration.engine();
        this.engine = engine;
        monitor = new MemoryMonitor(50);
        saving = new AtomicBoolean(false);
        stopRequested = new AtomicBoolean(false);
        info = new String[]{IrisLanguage.plain(DesktopUiMessages.PREGEN_INITIALIZING)};
        this.task = task;
        this.pregenerator = new IrisPregenerator(task, method, this);
        max = new Position2(Integer.MIN_VALUE, Integer.MIN_VALUE);
        min = new Position2(Integer.MAX_VALUE, Integer.MAX_VALUE);
        service = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1_024),
                runnable -> {
                    Thread thread = new Thread(runnable, "Iris Pregen Renderer");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setUncaughtExceptionHandler((activeThread, error) -> IrisLogging.reportError(error));
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());

        switch (GuiHost.serverGuiLaunch(task.isGui())) {
            case OPEN -> open();
            case UNAVAILABLE -> IrisLogging.info("Pregen GUI unavailable (headless), continuing");
            case DISABLED -> {
            }
        }

        worker = new Thread(() -> runWorker(configuration.preparation()), "Iris Pregenerator");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.setDaemon(true);
        worker.setUncaughtExceptionHandler((thread, ex) -> IrisLogging.reportError(ex));

        // Publish into the static only after every field is assigned (the volatile swap is
        // what makes them visible to metrics/shutdown readers), and start the worker after
        // publication so it also sees a complete object.
        // CAS-or-throw: updateAndGet must be side-effect free (it can re-apply on
        // contention), and silently killing the previous job overlapped its 60s+120s
        // teardown with the new job's generation. Replacement goes through
        // shutdownAndWait first, matching the modded adapter's rejection contract.
        if (!instance.compareAndSet(null, this)) {
            monitor.close();
            service.shutdown();
            throw new IllegalStateException("An Iris pregeneration job is already running; stop it first.");
        }
        try {
            worker.start();
        } catch (Throwable startFailure) {
            // Un-publish: a worker that never started can never run onClose(), so nothing
            // else would ever clear this instance via the normal path.
            instance.compareAndSet(this, null);
            monitor.close();
            service.shutdown();
            throw startFailure;
        }
    }

    private void runWorker(Runnable preparation) {
        try {
            J.sleep(1000);
            if (stopRequested.get()) {
                onClose();
                return;
            }
            preparation.run();
            if (stopRequested.get()) {
                onClose();
                return;
            }
            computeBounds();
            pregenerator.start();
        } catch (Throwable failure) {
            IrisLogging.reportError("Pregen startup failed.", failure);
            onClose();
        }
    }

    private void computeBounds() {
        task.iterateAllChunks((xx, zz) -> {
            min.setX(Math.min(xx, min.getX()));
            min.setZ(Math.min(zz, min.getZ()));
            max.setX(Math.max(xx, max.getX()));
            max.setZ(Math.max(zz, max.getZ()));
        });
    }

    public static boolean shutdownInstance() {
        PregeneratorJob inst = instance.get();
        if (inst == null) {
            return false;
        }

        if (!inst.worker.isAlive() && inst.worker.getState() != Thread.State.NEW) {
            // The worker died without running onClose (early abort); clear the phantom job so
            // it stops suppressing entity spawns and blocking future pregens. A NEW worker is
            // a job mid-construction, not a dead one.
            instance.compareAndSet(inst, null);
            return false;
        }

        inst.requestStop();
        return true;
    }

    public static boolean shutdownInstanceForWorld(String worldIdentity) {
        PregeneratorJob inst = instance.get();
        if (inst == null || !inst.targetsWorldIdentity(worldIdentity)) {
            return false;
        }

        return shutdownAndWait(inst, WORLD_SHUTDOWN_TIMEOUT_MILLIS);
    }

    public static boolean shutdownAndWait(long timeoutMs) {
        PregeneratorJob inst = instance.get();
        if (inst == null) {
            return false;
        }

        return shutdownAndWait(inst, timeoutMs);
    }

    private static boolean shutdownAndWait(PregeneratorJob inst, long timeoutMs) {
        inst.requestStop();
        try {
            inst.worker.join(Math.max(1L, timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping the Iris pregenerator.", e);
        }
        if (inst.worker.isAlive()) {
            throw new IllegalStateException("Timed out while stopping the Iris pregenerator after "
                    + Math.max(1L, timeoutMs) + "ms.");
        }
        instance.compareAndSet(inst, null);
        return true;
    }

    public static PregeneratorJob getInstance() {
        return instance.get();
    }

    public static boolean pauseResume() {
        PregeneratorJob inst = instance.get();
        if (inst == null) {
            return false;
        }

        if (isPaused()) {
            inst.pregenerator.resume();
        } else {
            inst.pregenerator.pause();
        }
        return true;
    }

    public static boolean isPaused() {
        PregeneratorJob inst = instance.get();
        if (inst == null) {
            return true;
        }

        return inst.paused();
    }

    public static double chunksPerSecond() {
        PregeneratorJob inst = instance.get();
        return inst == null ? 0D : Math.max(0D, inst.lastChunksPerSecond);
    }

    public static long chunksRemaining() {
        PregeneratorJob inst = instance.get();
        return inst == null ? -1L : Math.max(0L, inst.lastChunksRemaining);
    }

    public record PregenProgress(double percent, long generated, long totalChunks, double chunksPerSecond,
                                 double overallChunksPerSecond, double thirtySecondChunksPerSecond,
                                 double sixtySecondChunksPerSecond, long chunksRemaining, long eta, long elapsed,
                                 String method, boolean paused, long failed, String worldName, String worldIdentity) {
    }

    public static PregenProgress progressSnapshot() {
        PregeneratorJob inst = instance.get();
        return inst == null ? null : inst.snapshot();
    }

    public PregenProgress snapshot() {
        double percent = lastTotalChunks <= 0 ? 0D : ((double) lastGenerated / (double) lastTotalChunks) * 100D;
        return new PregenProgress(
                percent,
                lastGenerated,
                lastTotalChunks,
                Math.max(0D, lastChunksPerSecond),
                Math.max(0D, lastOverallChunksPerSecond),
                Math.max(0D, lastThirtySecondChunksPerSecond),
                Math.max(0D, lastSixtySecondChunksPerSecond),
                Math.max(0L, lastChunksRemaining),
                lastEta,
                lastElapsed,
                lastMethod,
                paused(),
                pregenerator.getFailedChunks(),
                worldName(),
                worldIdentity());
    }

    public String worldName() {
        if (engine == null || engine.getWorld() == null) {
            return null;
        }

        return engine.getWorld().name();
    }

    public String worldIdentity() {
        if (engine == null || engine.getWorld() == null) {
            return null;
        }
        return engine.getWorld().identity();
    }

    public boolean targetsWorldIdentity(String worldIdentity) {
        if (worldIdentity == null || engine == null || engine.getWorld() == null) {
            return false;
        }

        return worldIdentity.equals(engine.getWorld().identity());
    }

    private static Color parseColor(String c) {
        String v = (c.startsWith("#") ? c : "#" + c).trim();
        try {
            return Color.decode(v);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("Error Parsing 'color', (" + c + ")");
        }

        return Color.RED;
    }

    public Mantle getMantle() {
        return pregenerator.getMantle();
    }

    public PregeneratorJob onProgress(Consumer<Double> c) {
        onProgress.add(c);
        return this;
    }

    public PregeneratorJob whenDone(Runnable r) {
        whenDone.add(r);
        return this;
    }

    public void drawRegion(int x, int z, Color color) {
        J.a(() -> task.iterateChunks(x, z, (xx, zz) -> {
            draw(xx, zz, color);
            J.sleep(3);
        }));
    }

    public void draw(int x, int z, Color color) {
        try {
            PregenRenderer activeRenderer = renderer;
            if (activeRenderer != null && activeRenderer.isVisibleFrame()) {
                activeRenderer.submit(x, z, color);
            }
        } catch (Throwable error) {
            IrisLogging.reportError(error);
            IrisLogging.error("Failed to draw pregen");
        }
    }

    public void stop() {
        requestStop();
        close();
    }

    private void requestStop() {
        if (!stopRequested.compareAndSet(false, true)) {
            return;
        }
        pregenerator.close();
        worker.interrupt();
    }

    public void close() {
        try {
            monitor.close();
            PregenRenderer activeRenderer = renderer;
            if (activeRenderer != null) {
                activeRenderer.close();
            }
        } catch (Throwable error) {
            IrisLogging.reportError(error);
            IrisLogging.error("Error closing pregen gui");
        }
    }

    public void open() {
        EventQueue.invokeLater(() -> {
            try {
                renderer = PregenRenderer.open(IrisLanguage.plain(DesktopUiMessages.PREGEN_TITLE), this, PregeneratorJob::pauseResume);
            } catch (Throwable error) {
                IrisLogging.reportError(error);
                IrisLogging.error("Error opening pregen gui");
            }
        });
    }

    @Override
    public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generated, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
        PregenRates rateSnapshot = pregenerator.getRates();
        lastChunksPerSecond = chunksPerSecond;
        lastOverallChunksPerSecond = rateSnapshot.overall();
        lastThirtySecondChunksPerSecond = rateSnapshot.thirtySecond();
        lastSixtySecondChunksPerSecond = rateSnapshot.sixtySecond();
        lastChunksRemaining = chunksRemaining;
        lastGenerated = generated;
        lastTotalChunks = totalChunks;
        lastEta = eta;
        lastElapsed = elapsed;
        lastMethod = method;

        info = new String[]{
                IrisLanguage.plain(
                        paused() ? DesktopUiMessages.PREGEN_PROGRESS_PAUSED
                                : saving.getAndSet(false) ? DesktopUiMessages.PREGEN_PROGRESS_SAVING : DesktopUiMessages.PREGEN_PROGRESS_GENERATING,
                        MessageArgument.trusted("generated", Form.f(generated)),
                        MessageArgument.trusted("total", Form.f(totalChunks)),
                        MessageArgument.trusted("percent", Form.pc(percent, 0))
                ),
                IrisLanguage.plain(
                        cached ? DesktopUiMessages.PREGEN_SPEED_CACHED : DesktopUiMessages.PREGEN_SPEED,
                        MessageArgument.trusted("overall", Form.f(rateSnapshot.overall(), 1)),
                        MessageArgument.trusted("tenSecond", Form.f(rateSnapshot.tenSecond(), 1)),
                        MessageArgument.trusted("thirtySecond", Form.f(rateSnapshot.thirtySecond(), 1)),
                        MessageArgument.trusted("sixtySecond", Form.f(rateSnapshot.sixtySecond(), 1))
                ),
                IrisLanguage.plain(
                        DesktopUiMessages.PREGEN_TIME,
                        MessageArgument.trusted("remaining", Form.duration(eta, 2)),
                        MessageArgument.trusted("elapsed", Form.duration(elapsed, 2))
                ),
                IrisLanguage.plain(DesktopUiMessages.PREGEN_METHOD, MessageArgument.untrusted("method", String.valueOf(method))),
                IrisLanguage.plain(
                        DesktopUiMessages.PREGEN_MEMORY,
                        MessageArgument.trusted("used", Form.memSize(monitor.getUsedBytes(), 2)),
                        MessageArgument.trusted("usage", Form.pc(monitor.getUsagePercent(), 0)),
                        MessageArgument.trusted("pressure", Form.memSize(monitor.getPressure(), 0))
                )
        };

        for (Consumer<Double> i : onProgress) {
            i.accept(percent);
        }

        dispatchApiPhases(apiPhases.onTick(paused()));
    }

    private void dispatchApiPhases(List<PregenApiPhase> phases) {
        if (phases.isEmpty()) {
            return;
        }

        PregenApiSink sink = IrisServices.getOrNull(PregenApiSink.class);
        if (sink == null) {
            return;
        }

        PregenProgress progress = snapshot();
        for (PregenApiPhase phase : phases) {
            try {
                sink.pregen(phase, progress);
            } catch (Throwable error) {
                IrisLogging.reportError("Iris pregeneration API dispatch failed for phase " + phase + ".", error);
            }
        }
    }

    private boolean reachedTotal() {
        return lastTotalChunks > 0L && lastGenerated >= lastTotalChunks;
    }

    @Override
    public void onChunkGenerating(int x, int z) {
        draw(x, z, COLOR_GENERATING);
    }

    @Override
    public void onChunkGenerated(int x, int z, boolean cached) {
        drawChunkPreview(x, z, COLOR_GENERATED);
    }

    @Override
    public void onRegionGenerated(int x, int z) {
        // No forced System.gc() here: a wall-clock full STW collection mid-generation stalled
        // everything; MantleHeapPressure's 96% panic reclaim already owns heap pressure.
        broadcastRegionDelta(x, z, IrisMessage.PregenRegionDelta.STATE_DONE);
    }

    private void broadcastRegionDelta(int regionX, int regionZ, int state) {
        IrisProtocolServer protocolServer = IrisServices.getOrNull(IrisProtocolServer.class);
        if (protocolServer == null) {
            return;
        }
        protocolServer.broadcastPregenRegionDelta(pregenerator.getJobId(), regionX, regionZ, state);
    }

    @Override
    public void onRegionGenerating(int x, int z) {
        broadcastRegionDelta(x, z, IrisMessage.PregenRegionDelta.STATE_GENERATING);
    }

    @Override
    public void onChunkCleaned(int x, int z) {
        //draw(x, z, COLOR_CLEANED);
    }

    @Override
    public void onRegionSkipped(int x, int z) {

    }

    @Override
    public void onNetworkStarted(int x, int z) {
        drawRegion(x, z, COLOR_NETWORK);
    }

    @Override
    public void onNetworkFailed(int x, int z) {

    }

    @Override
    public void onNetworkReclaim(int revert) {

    }

    @Override
    public void onNetworkGeneratedChunk(int x, int z) {
        draw(x, z, COLOR_NETWORK_GENERATING);
    }

    @Override
    public void onNetworkDownloaded(int x, int z) {
        drawRegion(x, z, COLOR_NETWORK);
    }

    @Override
    public void onClose() {
        dispatchApiPhases(apiPhases.onClose(reachedTotal()));
        close();
        instance.compareAndSet(this, null);
        whenDone.forEach(Runnable::run);
        service.shutdownNow();
    }

    @Override
    public void onSaving() {
        saving.set(true);
        dispatchApiPhases(apiPhases.onSaving());
    }

    @Override
    public void onChunkExistsInRegionGen(int x, int z) {
        drawChunkPreview(x, z, COLOR_EXISTS);
    }

    @Override
    public Position2 max() {
        return max;
    }

    @Override
    public Position2 min() {
        return min;
    }

    @Override
    public boolean paused() {
        return pregenerator.paused();
    }

    @Override
    public String[] progress() {
        return info;
    }

    private void drawChunkPreview(int x, int z, Color statusColor) {
        PregenRenderer activeRenderer = renderer;
        if (activeRenderer == null || !activeRenderer.isVisibleFrame() || service.isShutdown()) {
            return;
        }
        draw(x, z, statusColor);
        if (engine != null) {
            service.execute(() -> renderChunkPreview(x, z));
        }
    }

    private void renderChunkPreview(int x, int z) {
        PregenRenderer activeRenderer = renderer;
        if (activeRenderer == null || !activeRenderer.isVisibleFrame() || service.isShutdown() || engine.isClosing()) {
            return;
        }
        try {
            draw(x, z, engine.drawForPreview((x << 4) + 8, (z << 4) + 8));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        } catch (SavedBiomeUnavailableException unavailable) {
            if (!unavailable.isLoading()) {
                IrisLogging.reportError("Unable to draw saved biome information for chunk " + x + "," + z + ".", unavailable);
            }
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Unable to draw the pregeneration preview for chunk " + x + "," + z + ".", failure);
        }
    }

    public record Configuration(
            PregenTask task,
            PregeneratorMethod method,
            Engine engine,
            Runnable preparation
    ) {
        public Configuration {
            Objects.requireNonNull(task, "Pregen task");
            Objects.requireNonNull(method, "Pregen method");
            Objects.requireNonNull(preparation, "Pregen preparation");
        }
    }
}
