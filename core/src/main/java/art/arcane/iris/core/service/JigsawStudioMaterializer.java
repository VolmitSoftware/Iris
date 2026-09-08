package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.service.JigsawStudioCapture.ChunkCaptureArea;
import art.arcane.iris.core.service.JigsawStudioCapture.LocalPosition;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.core.service.JigsawStudioCapture.chunkIntersections;
import static art.arcane.iris.core.service.JigsawStudioChunkWriter.hydrateChunk;
import static art.arcane.iris.core.service.JigsawStudioChunkWriter.restoreConnectorChunk;
import static art.arcane.iris.core.service.JigsawStudioChunkWriter.validateMaterialization;
import static art.arcane.iris.core.service.JigsawStudioChunkWriter.writeMaterializedChunk;
import static art.arcane.iris.core.service.JigsawStudioService.chunkKey;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.message;

final class JigsawStudioMaterializer {
    private static final int HYDRATION_RETRY_TICKS = 2;
    private static final int MAX_HYDRATION_ATTEMPTS = 40;

    private final JigsawStudioService service;
    final Set<UUID> materializationsInProgress = new HashSet<>();

    JigsawStudioMaterializer(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean scheduleMaterialization(MaterializationWork work) {
        String validationFailure = validateMaterialization(work.target());
        if (!validationFailure.isEmpty()) {
            work.studio().generator().getSession().abortVariantSwitch(work.token());
            service.playerContext.refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
            message(work.player(), "Variant '" + work.token().targetVariant().pieceKey()
                    + "' cannot load: " + validationFailure);
            return false;
        }
        String reservationFailure = beginMaterialization(work);
        if (!reservationFailure.isEmpty()) {
            work.studio().generator().getSession().abortVariantSwitch(work.token());
            service.playerContext.refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
            message(work.player(), reservationFailure);
            return false;
        }
        List<ChunkCaptureArea> areas = chunkIntersections(work.workcell().bounds());
        MaterializationCoordinator coordinator = new MaterializationCoordinator(work, areas);
        for (ChunkCaptureArea area : areas) {
            boolean scheduled = J.runRegion(
                    work.world(),
                    area.chunkX(),
                    area.chunkZ(),
                    () -> materializeCandidateChunk(coordinator, area));
            if (!scheduled) {
                coordinator.candidateComplete(
                        area,
                        "Iris could not schedule workcell chunk "
                                + area.chunkX() + "," + area.chunkZ() + " on its owning region.",
                        null);
            } else {
                coordinator.markScheduled();
            }
        }
        return coordinator.scheduledAny();
    }

    private String beginMaterialization(MaterializationWork work) {
        UUID requestId = work.studio().generator().getRequest().requestId();
        synchronized (service.saveLifecycleLock) {
            if (!isCurrentVariantSwitch(work)) {
                return "The Jigsaw Studio changed before the variant load could start.";
            }
            if (service.saveLifecycle.closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot load another variant.";
            }
            if (service.saveLifecycle.savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio graph update to finish.";
            }
            if (service.saveLifecycle.exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before loading another variant.";
            }
            if (!materializationsInProgress.add(requestId)) {
                return "Another Jigsaw Studio variant load or rollback is already running.";
            }
            return "";
        }
    }

    String beginConnectorRepair(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        synchronized (service.saveLifecycleLock) {
            if (!service.isCurrentRequest(studio, requestId)) {
                return "This Jigsaw Studio session is no longer active.";
            }
            if (service.saveLifecycle.closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot reset connector blocks.";
            }
            if (service.saveLifecycle.savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio graph update to finish.";
            }
            if (service.saveLifecycle.exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (studio.generator().getSession().operationInProgress()) {
                return "Wait for the current workcell operation to finish.";
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before resetting connectors.";
            }
            if (!materializationsInProgress.add(requestId)) {
                return "Another Jigsaw Studio variant load or repair is already running.";
            }
            return "";
        }
    }

    boolean scheduleConnectorRepair(
            Player player,
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            boolean connectorsVisible
    ) {
        Map<Long, List<JigsawStudioGenerator.RenderedConnector>> connectorsByChunk = new HashMap<>();
        JigsawStudioBounds bounds = workcell.bounds();
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            int worldX = bounds.originX() + connector.x();
            int worldZ = bounds.originZ() + connector.z();
            connectorsByChunk.computeIfAbsent(
                    chunkKey(worldX >> 4, worldZ >> 4),
                    ignored -> new ArrayList<>()).add(connector);
        }
        AtomicInteger remaining = new AtomicInteger(connectorsByChunk.size());
        AtomicReference<String> failure = new AtomicReference<>("");
        AtomicReference<Throwable> cause = new AtomicReference<>();
        AtomicBoolean scheduledAny = new AtomicBoolean(false);
        Map<LocalPosition, JigsawStudioGenerator.RenderedBlock> renderedBlocks = new HashMap<>();
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            renderedBlocks.put(new LocalPosition(block.x(), block.y(), block.z()), block);
        }
        for (Map.Entry<Long, List<JigsawStudioGenerator.RenderedConnector>> entry
                : connectorsByChunk.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) entry.getKey().longValue();
            boolean scheduled = J.runRegion(studio.world(), chunkX, chunkZ, () -> {
                try {
                    if (!studio.world().isChunkLoaded(chunkX, chunkZ)) {
                        throw new IOException("connector chunk " + chunkX + "," + chunkZ
                                + " is not loaded");
                    }
                    restoreConnectorChunk(
                            studio.world(),
                            workcell,
                            entry.getValue(),
                            renderedBlocks,
                            connectorsVisible);
                } catch (Throwable exception) {
                    failure.compareAndSet("", failureMessage(exception));
                    cause.compareAndSet(null, exception);
                }
                if (remaining.decrementAndGet() == 0) {
                    completeConnectorRepair(
                            player,
                            studio,
                            rendered.connectors().size(),
                            failure.get(),
                            cause.get());
                }
            });
            if (scheduled) {
                scheduledAny.set(true);
            } else {
                failure.compareAndSet("", "connector chunk " + chunkX + "," + chunkZ
                        + " could not be scheduled on its owning region");
                if (remaining.decrementAndGet() == 0) {
                    completeConnectorRepair(
                            player,
                            studio,
                            rendered.connectors().size(),
                            failure.get(),
                            cause.get());
                }
            }
        }
        if (!scheduledAny.get()) {
            finishMaterialization(studio.generator().getRequest().requestId());
        }
        return scheduledAny.get();
    }

    private void completeConnectorRepair(
            Player player,
            ActiveStudio studio,
            int connectorCount,
            String failure,
            Throwable cause
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        boolean scheduled = J.runEntity(player, () -> {
            finishMaterialization(requestId);
            if (cause != null) {
                IrisLogging.reportError(cause);
            }
            if (!failure.isEmpty()) {
                message(player, "Connector reset was incomplete: " + failure + ". Retry after visiting the workcell.");
                return;
            }
            message(player, "Restored " + connectorCount
                    + " connector block(s) from the last saved iteration.");
        });
        if (!scheduled) {
            finishMaterialization(requestId);
            if (cause != null) {
                IrisLogging.reportError(cause);
            }
        }
    }

    private void finishMaterialization(UUID requestId) {
        synchronized (service.saveLifecycleLock) {
            materializationsInProgress.remove(requestId);
        }
    }

    private void materializeCandidateChunk(
            MaterializationCoordinator coordinator,
            ChunkCaptureArea area
    ) {
        MaterializationWork work = coordinator.work();
        if (!isCurrentVariantSwitch(work)) {
            coordinator.candidateComplete(
                    area,
                    "Jigsaw Studio changed before the variant could finish loading.",
                    null);
            return;
        }
        if (!work.world().isChunkLoaded(area.chunkX(), area.chunkZ())) {
            coordinator.candidateComplete(
                    area,
                    "Workcell chunk " + area.chunkX() + "," + area.chunkZ()
                            + " is not loaded. Visit the whole workcell and try again.",
                    null);
            return;
        }
        try {
            coordinator.markCandidateTouched(area);
            writeMaterializedChunk(
                    work.world(),
                    work.workcell(),
                    work.target(),
                    area,
                    work.targetConnectorsVisible());
            coordinator.candidateComplete(area, "", null);
        } catch (Throwable exception) {
            coordinator.candidateComplete(
                    area,
                    "Variant materialization failed in chunk "
                            + area.chunkX() + "," + area.chunkZ() + ": " + failureMessage(exception),
                    exception);
        }
    }

    private void materializeRollbackChunk(
            MaterializationCoordinator coordinator,
            ChunkCaptureArea area
    ) {
        MaterializationWork work = coordinator.work();
        if (!isRollbackVariantSwitch(work)) {
            coordinator.rollbackComplete(
                    area,
                    "Jigsaw Studio changed before the previous variant could be restored.",
                    null);
            return;
        }
        if (!work.world().isChunkLoaded(area.chunkX(), area.chunkZ())) {
            coordinator.rollbackComplete(
                    area,
                    "Rollback chunk " + area.chunkX() + "," + area.chunkZ() + " is not loaded.",
                    null);
            return;
        }
        try {
            writeMaterializedChunk(
                    work.world(),
                    work.workcell(),
                    work.previous(),
                    area,
                    work.previousConnectorsVisible());
            coordinator.rollbackComplete(area, "", null);
        } catch (Throwable exception) {
            coordinator.rollbackComplete(
                    area,
                    "Rollback failed in chunk " + area.chunkX() + "," + area.chunkZ()
                            + ": " + failureMessage(exception),
                    exception);
        }
    }

    private boolean isCurrentVariantSwitch(MaterializationWork work) {
        return service.isCurrentRequest(
                work.studio(),
                work.studio().generator().getRequest().requestId())
                && work.studio().generator().getSession().isVariantSwitchCurrent(work.token());
    }

    private boolean isRollbackVariantSwitch(MaterializationWork work) {
        return service.studios.get(work.studio().worldId()) == work.studio()
                && service.protection.materializationInProgress(work.studio());
    }

    void scheduleHydration(
            ActiveStudio studio,
            World world,
            int chunkX,
            int chunkZ,
            int attempt
    ) {
        if (!service.enabled || service.studios.get(world.getUID()) != studio) {
            return;
        }
        long key = chunkKey(chunkX, chunkZ);
        if (!studio.hydrationsInProgress().add(key)) {
            return;
        }
        boolean scheduled = J.runRegion(
                world,
                chunkX,
                chunkZ,
                () -> runHydrationAttempt(studio, world, chunkX, chunkZ, attempt, key),
                HYDRATION_RETRY_TICKS);
        if (!scheduled) {
            studio.hydrationsInProgress().remove(key);
        }
    }

    private void runHydrationAttempt(
            ActiveStudio studio,
            World world,
            int chunkX,
            int chunkZ,
            int attempt,
            long key
    ) {
        studio.hydrationsInProgress().remove(key);
        if (!service.enabled || service.studios.get(world.getUID()) != studio) {
            return;
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            if (attempt < MAX_HYDRATION_ATTEMPTS) {
                scheduleHydration(studio, world, chunkX, chunkZ, attempt + 1);
            }
            return;
        }
        if (hydrateChunk(studio, world, chunkX, chunkZ)) {
            scheduleHydration(studio, world, chunkX, chunkZ, 0);
        }
    }

    private final class MaterializationCoordinator {
        private final MaterializationWork work;
        private final List<ChunkCaptureArea> areas;
        private final Set<Long> candidateCompleted = new HashSet<>();
        private final Set<Long> candidateTouched = new HashSet<>();
        private final Set<Long> rollbackCompleted = new HashSet<>();
        private int candidateRemaining;
        private int rollbackRemaining;
        private String candidateFailure = "";
        private String rollbackFailure = "";
        private boolean rollbackStarted;
        private boolean finished;
        private boolean leaseReleased;
        private boolean scheduledAny;

        private MaterializationCoordinator(
                MaterializationWork work,
                List<ChunkCaptureArea> areas
        ) {
            this.work = Objects.requireNonNull(work, "Jigsaw Studio materialization work");
            this.areas = List.copyOf(areas);
            candidateRemaining = this.areas.size();
        }

        private MaterializationWork work() {
            return work;
        }

        private synchronized void markScheduled() {
            scheduledAny = true;
        }

        private synchronized boolean scheduledAny() {
            return scheduledAny;
        }

        private synchronized void markCandidateTouched(ChunkCaptureArea area) {
            candidateTouched.add(chunkKey(area.chunkX(), area.chunkZ()));
        }

        private void candidateComplete(
                ChunkCaptureArea area,
                String failure,
                Throwable exception
        ) {
            boolean finishSuccess = false;
            boolean startRollback = false;
            synchronized (this) {
                if (finished || rollbackStarted
                        || !candidateCompleted.add(chunkKey(area.chunkX(), area.chunkZ()))) {
                    return;
                }
                if (failure != null && !failure.isBlank() && candidateFailure.isEmpty()) {
                    candidateFailure = failure;
                }
                candidateRemaining--;
                if (candidateRemaining == 0) {
                    if (candidateFailure.isEmpty()) {
                        finishSuccess = true;
                    } else {
                        rollbackStarted = true;
                        startRollback = true;
                    }
                }
            }
            if (exception != null) {
                IrisLogging.reportError(exception);
            }
            if (finishSuccess) {
                finishSuccess();
            } else if (startRollback) {
                scheduleRollback();
            }
        }

        private void finishSuccess() {
            JigsawStudioSession session = work.studio().generator().getSession();
            if (!isCurrentVariantSwitch(work) || !session.completeVariantSwitch(work.token())) {
                beginLateRollback("Jigsaw Studio changed before the loaded variant could be activated.");
                return;
            }
            if (work.connectorVisibilityChange()) {
                session.setConnectorsVisible(work.workcell().stableId(), work.targetConnectorsVisible());
            }
            synchronized (this) {
                if (finished) {
                    return;
                }
                finished = true;
            }
            try {
                work.studio().generator().invalidateRender(work.workcell().stableId());
                work.studio().replacePopulation(work.workcell(), work.target(), "", true);
                service.playerContext.refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
                message(work.player(), work.connectorVisibilityChange()
                        ? "Connector blocks are now "
                        + (work.targetConnectorsVisible() ? "visible." : "hidden.")
                        : "Loaded variant '" + work.token().targetVariant().pieceKey()
                        + "' into " + work.workcell().stableId() + ".");
            } finally {
                releaseLease();
            }
        }

        private void beginLateRollback(String failure) {
            synchronized (this) {
                if (finished || rollbackStarted) {
                    return;
                }
                candidateFailure = failure;
                rollbackStarted = true;
            }
            scheduleRollback();
        }

        private void scheduleRollback() {
            List<ChunkCaptureArea> rollbackAreas = new ArrayList<>();
            synchronized (this) {
                for (ChunkCaptureArea area : areas) {
                    if (candidateTouched.contains(chunkKey(area.chunkX(), area.chunkZ()))) {
                        rollbackAreas.add(area);
                    }
                }
                rollbackRemaining = rollbackAreas.size();
                if (rollbackRemaining == 0) {
                    finished = true;
                }
            }
            if (rollbackAreas.isEmpty()) {
                finishRollback();
                return;
            }
            for (ChunkCaptureArea area : rollbackAreas) {
                boolean scheduled = J.runRegion(
                        work.world(),
                        area.chunkX(),
                        area.chunkZ(),
                        () -> materializeRollbackChunk(this, area));
                if (!scheduled) {
                    rollbackComplete(
                            area,
                            "Iris could not schedule rollback for workcell chunk "
                                    + area.chunkX() + "," + area.chunkZ() + ".",
                            null);
                }
            }
        }

        private void rollbackComplete(
                ChunkCaptureArea area,
                String failure,
                Throwable exception
        ) {
            boolean finalizeRollback = false;
            synchronized (this) {
                if (finished || !rollbackStarted
                        || !rollbackCompleted.add(chunkKey(area.chunkX(), area.chunkZ()))) {
                    return;
                }
                if (failure != null && !failure.isBlank() && rollbackFailure.isEmpty()) {
                    rollbackFailure = failure;
                }
                rollbackRemaining--;
                if (rollbackRemaining == 0) {
                    finished = true;
                    finalizeRollback = true;
                }
            }
            if (exception != null) {
                IrisLogging.reportError(exception);
            }
            if (finalizeRollback) {
                finishRollback();
            }
        }

        private void finishRollback() {
            JigsawStudioSession session = work.studio().generator().getSession();
            boolean released = session.abortVariantSwitch(work.token());
            try {
                work.studio().generator().invalidateRender(work.workcell().stableId());
                if (rollbackFailure.isEmpty() && released) {
                    work.studio().replacePopulation(work.workcell(), work.previous(), "", true);
                    service.playerContext.refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
                    message(work.player(), candidateFailure + " The previous variant was restored.");
                    return;
                }
                String failure = rollbackFailure.isEmpty()
                        ? "The previous variant could not be restored because the Studio session changed."
                        : rollbackFailure;
                work.studio().replacePopulation(work.workcell(), work.previous(), failure, false);
                service.playerContext.refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
                message(work.player(), candidateFailure + " Automatic rollback also failed: " + failure
                        + " Do not save this workcell until it is reopened.");
            } finally {
                releaseLease();
            }
        }

        private void releaseLease() {
            synchronized (this) {
                if (leaseReleased) {
                    return;
                }
                leaseReleased = true;
            }
            finishMaterialization(work.studio().generator().getRequest().requestId());
        }
    }

    record MaterializationWork(
            ActiveStudio studio,
            World world,
            Player player,
            JigsawStudioBay workcell,
            JigsawStudioSession.VariantSwitchToken token,
            JigsawStudioGenerator.RenderedBay previous,
            JigsawStudioGenerator.RenderedBay target,
            boolean previousConnectorsVisible,
            boolean targetConnectorsVisible,
            boolean connectorVisibilityChange
    ) {
        MaterializationWork {
            Objects.requireNonNull(studio, "Jigsaw Studio materialization studio");
            Objects.requireNonNull(world, "Jigsaw Studio materialization world");
            Objects.requireNonNull(player, "Jigsaw Studio materialization player");
            Objects.requireNonNull(workcell, "Jigsaw Studio materialization workcell");
            Objects.requireNonNull(token, "Jigsaw Studio materialization token");
            Objects.requireNonNull(previous, "Jigsaw Studio previous rendered variant");
            Objects.requireNonNull(target, "Jigsaw Studio target rendered variant");
        }
    }

    public record VariantReloadRequest(
            String workcellId,
            JigsawStudioGenerator.RenderedBay previous
    ) {
        public VariantReloadRequest {
            workcellId = workcellId == null ? "" : workcellId.trim();
            if (workcellId.isEmpty()) {
                throw new IllegalArgumentException("Jigsaw Studio reload workcell ID cannot be blank");
            }
            Objects.requireNonNull(previous, "Jigsaw Studio previous rendered variant");
        }
    }
}
