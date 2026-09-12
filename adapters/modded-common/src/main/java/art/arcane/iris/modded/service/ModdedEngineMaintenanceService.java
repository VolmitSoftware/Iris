/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.service;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.EngineMaintenance;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.modded.ModdedWorldEngines;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.context.IrisContext;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ModdedEngineMaintenanceService implements ModdedTickableService {
    private static final long MAINTENANCE_PERIOD_MILLIS = 2_000L;
    private static final long SAVE_PERIOD_MILLIS = 60_000L;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;
    private static final long INTERRUPT_DRAIN_TIMEOUT_SECONDS = 5L;

    private final Set<Engine> inFlight = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Map<Engine, Long> lastSavedAt = Collections.synchronizedMap(new IdentityHashMap<>());
    private volatile ExecutorService service;
    private long lastMaintenanceAt;

    @Override
    public void onEnable() {
        ExecutorService current = service;
        if (current != null) {
            if (!current.isTerminated()) {
                throw new IllegalStateException(
                        "Iris engine maintenance cannot restart while prior workers are still active");
            }
            service = null;
        }

        IrisSettings.IrisSettingsEngineSVC settings = IrisSettings.get().getPerformance().getEngineSVC();
        ThreadFactory factory = (settings.isUseVirtualThreads()
                ? Thread.ofVirtual()
                : Thread.ofPlatform().priority(settings.getPriority()))
                .name("Iris EngineSVC-", 0)
                .factory();
        service = Executors.newFixedThreadPool(EngineMaintenance.workerParallelism(), factory);
        lastMaintenanceAt = 0L;
        inFlight.clear();
        lastSavedAt.clear();
    }

    @Override
    public void onDisable() {
        ExecutorService active = service;
        boolean drained = shutdownAndDrain(active);
        if (drained) {
            service = null;
            inFlight.clear();
        }
        lastSavedAt.clear();
        if (!drained) {
            throw new IllegalStateException("Iris engine maintenance workers remained active during service shutdown");
        }
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        ExecutorService active = service;
        if (active == null || active.isShutdown()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMaintenanceAt < MAINTENANCE_PERIOD_MILLIS) {
            return;
        }
        lastMaintenanceAt = now;

        Collection<Engine> engines = ModdedWorldEngines.activeEngines();
        reconcileSaveState(engines, now);
        for (Engine engine : engines) {
            if (!inFlight.add(engine)) {
                continue;
            }
            try {
                active.execute(() -> {
                    try {
                        maintain(engine, now);
                    } finally {
                        inFlight.remove(engine);
                    }
                });
            } catch (RejectedExecutionException exception) {
                inFlight.remove(engine);
                if (active == service && !active.isShutdown()) {
                    ModdedIrisLog.error("Iris rejected engine maintenance for {}", engineName(engine), exception);
                }
            }
        }
    }

    private void maintain(Engine engine, long scheduledAt) {
        if (engine == null || engine.isClosing() || engine.isClosed()) {
            return;
        }
        try (GenerationSessionLease lease = engine.acquireGenerationLease("modded_engine_maintenance");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!EngineMaintenance.isAvailable(engine)) {
                return;
            }

            boolean pregeneratorTargetsWorld = EngineMaintenance.pregeneratorTargets(engine);
            if (pregeneratorTargetsWorld) {
                return;
            }

            saveIfDue(engine, scheduledAt);
            if (!EngineMaintenance.shouldRun(engine)) {
                return;
            }

            EngineMaintenance.Outcome outcome = EngineMaintenance.run(engine);
            if (outcome.unloadedTectonicPlates() > 0) {
                ModdedIrisLog.debug("Iris unloaded {} tectonic plates in {}ms for {}",
                        outcome.unloadedTectonicPlates(), outcome.unloadDurationMillis(), engineName(engine));
            }
        } catch (GenerationSessionException exception) {
            if (engine.isClosing() || exception.isExpectedTeardown()) {
                return;
            }
            IrisLogging.reportError(exception);
            ModdedIrisLog.error("Iris engine maintenance session failed for {}", engineName(engine), exception);
        } catch (Throwable exception) {
            if (EngineMaintenance.isMantleClosed(exception)) {
                return;
            }
            IrisLogging.reportError(exception);
            ModdedIrisLog.error("Iris engine maintenance failed for {}", engineName(engine), exception);
        }
    }

    private void saveIfDue(Engine engine, long scheduledAt) {
        Long lastSave = lastSavedAt.get(engine);
        if (lastSave == null || scheduledAt - lastSave < SAVE_PERIOD_MILLIS) {
            return;
        }

        try {
            engine.save();
            lastSavedAt.put(engine, System.currentTimeMillis());
        } catch (Throwable exception) {
            if (EngineMaintenance.isMantleClosed(exception)) {
                return;
            }
            IrisLogging.reportError(exception);
            ModdedIrisLog.error("Iris engine save failed for {}", engineName(engine), exception);
        }
    }

    private void reconcileSaveState(Collection<Engine> engines, long now) {
        Set<Engine> activeEngines = Collections.newSetFromMap(new IdentityHashMap<>());
        activeEngines.addAll(engines);
        synchronized (lastSavedAt) {
            lastSavedAt.keySet().removeIf(engine -> !activeEngines.contains(engine));
            for (Engine engine : activeEngines) {
                lastSavedAt.putIfAbsent(engine, now);
            }
        }
    }

    private boolean shutdownAndDrain(ExecutorService active) {
        if (active == null) {
            return true;
        }

        active.shutdown();
        try {
            if (active.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
            active.shutdownNow();
            if (active.awaitTermination(INTERRUPT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
            IllegalStateException failure = new IllegalStateException(
                    "Iris engine maintenance workers did not stop after shutdownNow");
            IrisLogging.reportError(failure);
            ModdedIrisLog.error("Iris engine maintenance did not terminate; active engine lifecycle leases will block unsafe shutdown", failure);
            return false;
        } catch (InterruptedException exception) {
            active.shutdownNow();
            Thread.currentThread().interrupt();
            IrisLogging.reportError(exception);
            ModdedIrisLog.error("Interrupted while draining Iris engine maintenance", exception);
            return false;
        }
    }

    private static String engineName(Engine engine) {
        return engine == null || engine.getWorld() == null ? "<unbound>" : engine.getWorld().name();
    }
}
