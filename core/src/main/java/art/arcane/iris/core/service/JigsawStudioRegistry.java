package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.service.JigsawStudioChunkWriter.BayPopulation;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.World;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.iris.core.service.JigsawStudioService.AUTOSAVE_RETRY_TICKS;
import static art.arcane.iris.core.service.JigsawStudioService.INSTANCE;
import static art.arcane.iris.core.service.JigsawStudioService.disableNaturalStudioSpawning;

final class JigsawStudioRegistry {
    private final JigsawStudioService service;
    private final Set<UUID> unregisterRetries = ConcurrentHashMap.newKeySet();
    private final Set<UUID> unregisterDrainWarnings = ConcurrentHashMap.newKeySet();

    JigsawStudioRegistry(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    void quiesceForServerShutdown() {
        if (!service.disableStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            service.tileWatcher.finalizeAllJigsawTileWatches();
        } catch (Throwable exception) {
            IrisLogging.reportError("Failed to finalize Jigsaw Studio tile watches during shutdown.", exception);
        }
        try {
            service.autosaveScheduler.drainAutosavesBeforeDisable();
        } catch (Throwable exception) {
            IrisLogging.reportError("Failed to drain Jigsaw Studio autosaves during shutdown.", exception);
        }
        service.enabled = false;
        JigsawStudioMenuController activeMenuController = service.toolbelt.menuController;
        service.toolbelt.menuController = null;
        if (activeMenuController != null) {
            try {
                activeMenuController.closeAll();
            } catch (Throwable exception) {
                IrisLogging.reportError("Failed to close Jigsaw Studio menus during shutdown.", exception);
            }
        }
        service.particlesDisabled.clear();
        service.visualization.visualizationLoops.clear();
        service.visualization.assemblyPreviews.clear();
        service.playerContext.playerWorkcells.clear();
        service.autosaveScheduler.autosaves.clear();
        service.toolbelt.deferredDuplications.clear();
        service.tileWatcher.jigsawTileWatches.clear();
        service.toolbelt.toolConfirmations.clear();
        service.tripleSneakTracker.clearAll();
        service.evaluator.evaluations.clear();
        try {
            service.previewRenderer.removeAll();
        } catch (Throwable exception) {
            IrisLogging.reportError("Failed to remove Jigsaw Studio previews during shutdown.", exception);
        }
        service.saveLifecycle.reopenRequiredRequests.clear();
        unregisterRetries.clear();
        unregisterDrainWarnings.clear();
        synchronized (service.saveLifecycleLock) {
            service.studios.clear();
            service.saveLifecycle.savesInProgress.clear();
            service.graphMutations.graphMutationsInProgress.clear();
            service.materializer.materializationsInProgress.clear();
            service.saveLifecycle.exportsInProgress.clear();
            service.saveLifecycle.closingRequests.clear();
            service.saveLifecycle.discardingRequests.clear();
        }
        INSTANCE = null;
    }

    void register(Engine engine, JigsawStudioGenerator generator) {
        if (!service.enabled || service.disableStarted.get()) {
            return;
        }
        Engine activeEngine = Objects.requireNonNull(engine, "Jigsaw Studio engine");
        JigsawStudioGenerator activeGenerator = Objects.requireNonNull(generator, "Jigsaw Studio generator");
        World world = BukkitWorldBinding.world(activeEngine.getTarget().getWorld());
        if (world == null) {
            return;
        }
        ActiveStudio existing = service.studios.get(world.getUID());
        if (existing != null && existing.generator() == activeGenerator) {
            return;
        }
        if (existing != null) {
            UUID existingRequestId = existing.generator().getRequest().requestId();
            service.tileWatcher.finalizeJigsawTileWatches(existingRequestId);
            service.autosaveScheduler.drainAutosavesBeforeRemoval(existing);
            if (service.saveLifecycle.requiresLifecycleDrain(existing)
                    && !service.saveLifecycle.discardingRequest(existingRequestId)) {
                IrisLogging.warn("Jigsaw Studio registration deferred while request %s finishes its final autosave drain",
                        existingRequestId);
                return;
            }
        }
        ActiveStudio next = new ActiveStudio(
                world.getUID(),
                world,
                activeEngine,
                activeGenerator,
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        UUID displacedRequestId = null;
        synchronized (service.saveLifecycleLock) {
            if (!service.enabled || service.disableStarted.get()) {
                return;
            }
            ActiveStudio previous = service.studios.get(world.getUID());
            if (previous != null && previous.generator() == activeGenerator) {
                return;
            }
            ActiveStudio displaced = service.studios.put(world.getUID(), next);
            if (displaced != null) {
                displacedRequestId = displaced.generator().getRequest().requestId();
                service.saveLifecycle.savesInProgress.remove(displacedRequestId);
                service.graphMutations.graphMutationsInProgress.remove(displacedRequestId);
                service.materializer.materializationsInProgress.remove(displacedRequestId);
                service.saveLifecycle.exportsInProgress.remove(displacedRequestId);
                service.saveLifecycle.closingRequests.remove(displacedRequestId);
                service.saveLifecycle.discardingRequests.remove(displacedRequestId);
                service.saveLifecycle.reopenRequiredRequests.remove(displacedRequestId);
            }
        }
        if (displacedRequestId != null) {
            service.autosaveScheduler.clearAutosaves(displacedRequestId);
            service.tileWatcher.clearJigsawTileWatches(displacedRequestId);
            unregisterRetries.remove(displacedRequestId);
            unregisterDrainWarnings.remove(displacedRequestId);
            service.tripleSneakTracker.clearRequest(displacedRequestId);
            service.evaluator.evaluations.remove(displacedRequestId);
            service.previewRenderer.removeRequest(displacedRequestId);
        }
        IrisLogging.debug("Jigsaw Studio authoring registered: world=%s structure=%s bays=%d",
                world.getName(), activeGenerator.getSession().structureKey(), activeGenerator.getLayout().bays().size());
        service.evaluator.scheduleInitialEvaluation(next);
        service.playerContext.scheduleOnlinePlayers(world.getUID());
    }

    void activationCommitted(World world, UUID requestId) {
        if (!service.enabled || service.disableStarted.get() || world == null || requestId == null) {
            return;
        }
        ActiveStudio studio = service.studios.get(world.getUID());
        if (studio == null || !requestId.equals(studio.generator().getRequest().requestId())) {
            return;
        }
        disableNaturalStudioSpawning(world);
        service.evaluator.scheduleInitialEvaluation(studio);
    }

    void markChunkGenerated(
            Engine engine,
            JigsawStudioGenerator generator,
            int chunkX,
            int chunkZ
    ) {
        if (!service.enabled || service.disableStarted.get() || engine == null || generator == null) {
            return;
        }
        World world = BukkitWorldBinding.world(engine.getTarget().getWorld());
        if (world == null) {
            return;
        }
        ActiveStudio studio = service.studios.get(world.getUID());
        if (!service.enabled || service.disableStarted.get()
                || studio == null || studio.engine() != engine || studio.generator() != generator) {
            return;
        }
        markChunkAvailable(studio, chunkX, chunkZ);
    }

    void markChunkAvailable(ActiveStudio studio, int chunkX, int chunkZ) {
        long chunkKey = JigsawStudioService.chunkKey(chunkX, chunkZ);
        boolean relevant = false;
        for (JigsawStudioBay bay : studio.generator().getLayout().bays()) {
            BayPopulation population = studio.population(bay);
            if (population.markGenerated(chunkKey)) {
                relevant = true;
            }
        }
        if (relevant) {
            service.materializer.scheduleHydration(studio, studio.world(), chunkX, chunkZ, 0);
        }
    }

    void unregister(World world) {
        if (world == null) {
            return;
        }
        ActiveStudio active = service.studios.get(world.getUID());
        if (active != null) {
            UUID activeRequestId = active.generator().getRequest().requestId();
            service.tileWatcher.finalizeJigsawTileWatches(activeRequestId);
            service.autosaveScheduler.drainAutosavesBeforeRemoval(active);
            if (service.saveLifecycle.requiresLifecycleDrain(active)
                    && !service.saveLifecycle.discardingRequest(activeRequestId)) {
                if (unregisterDrainWarnings.add(activeRequestId)) {
                    IrisLogging.warn("Jigsaw Studio unregister deferred while request %s finishes its final autosave drain",
                            activeRequestId);
                }
                scheduleUnregisterRetry(world, activeRequestId);
                return;
            }
        }
        ActiveStudio removed;
        JigsawStudioActivation.Request request;
        synchronized (service.saveLifecycleLock) {
            removed = service.studios.remove(world.getUID());
            if (removed == null) {
                return;
            }
            request = removed.generator().getRequest();
            service.saveLifecycle.savesInProgress.remove(request.requestId());
            service.graphMutations.graphMutationsInProgress.remove(request.requestId());
            service.materializer.materializationsInProgress.remove(request.requestId());
            service.saveLifecycle.exportsInProgress.remove(request.requestId());
            service.saveLifecycle.closingRequests.remove(request.requestId());
            service.saveLifecycle.discardingRequests.remove(request.requestId());
            service.saveLifecycle.reopenRequiredRequests.remove(request.requestId());
        }
        service.autosaveScheduler.clearAutosaves(request.requestId());
        service.toolbelt.deferredDuplications.remove(request.requestId());
        service.tileWatcher.clearJigsawTileWatches(request.requestId());
        unregisterRetries.remove(request.requestId());
        unregisterDrainWarnings.remove(request.requestId());
        service.toolbelt.toolConfirmations.entrySet().removeIf(
                entry -> entry.getValue().payload().requestId().equals(request.requestId()));
        service.tripleSneakTracker.clearRequest(request.requestId());
        service.evaluator.evaluations.remove(request.requestId());
        service.previewRenderer.forgetRequest(request.requestId());
        JigsawStudioActivation.deactivate(request.packKey(), request.requestId());
        service.playerContext.clearWorldPlayerContexts(world.getUID());
        IrisLogging.debug("Jigsaw Studio authoring unregistered: world=%s", world.getName());
    }

    private void scheduleUnregisterRetry(World world, UUID requestId) {
        if (!service.enabled || !unregisterRetries.add(requestId)) {
            return;
        }
        try {
            J.s(() -> {
                unregisterRetries.remove(requestId);
                ActiveStudio current = service.studios.get(world.getUID());
                if (current != null
                        && current.generator().getRequest().requestId().equals(requestId)) {
                    unregister(world);
                }
            }, AUTOSAVE_RETRY_TICKS);
        } catch (Throwable exception) {
            unregisterRetries.remove(requestId);
            IrisLogging.reportError(exception);
        }
    }
}
