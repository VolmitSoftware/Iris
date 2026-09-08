package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.runtime.InPlaceChunkRegenerator;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.service.JigsawStudioService.CommandGraphMutation;
import art.arcane.iris.core.service.JigsawStudioService.CommandGraphMutationResult;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.core.service.JigsawStudioChunkWriter.applyRenderedBayChunk;
import static art.arcane.iris.core.service.JigsawStudioChunkWriter.verifyRenderedBayChunk;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.message;

final class JigsawStudioGraphMutations {
    private final JigsawStudioService service;
    final Set<UUID> graphMutationsInProgress = new HashSet<>();

    JigsawStudioGraphMutations(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    String beginGraphMutation(
            ActiveStudio studio,
            JigsawStudioSession session
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        synchronized (service.saveLifecycleLock) {
            if (service.saveLifecycle.closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot update its graph.";
            }
            if (service.saveLifecycle.savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return "A Jigsaw Studio graph update is already running.";
            }
            if (service.saveLifecycle.exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (service.materializer.materializationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio variant load or rollback to finish.";
            }
            if (session.operationInProgress()) {
                return "Wait for the current workcell operation to finish.";
            }
            if (service.saveLifecycle.reopenRequiredRequests.contains(requestId)) {
                return "Close and reopen Jigsaw Studio before making another graph change.";
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before changing graph metadata.";
            }
            if (session.isDirty()) {
                return "Wait for every dirty workcell to finish autosaving before changing graph metadata.";
            }
            graphMutationsInProgress.add(requestId);
            return "";
        }
    }

    boolean runCommandGraphMutation(
            Player player,
            UUID expectedRequestId,
            CommandGraphMutation task
    ) {
        Objects.requireNonNull(expectedRequestId, "Jigsaw Studio command graph request ID");
        Objects.requireNonNull(task, "Jigsaw Studio command graph mutation");
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> runCommandGraphMutation(player, expectedRequestId, task));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null
                || !studio.generator().getRequest().requestId().equals(expectedRequestId)) {
            message(player, "This Jigsaw Studio session is no longer active.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, studio.generator().getSession());
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(player, studio, task);
    }

    boolean scheduleGraphMutation(
            Player player,
            ActiveStudio studio,
            CommandGraphMutation task
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        J.a(() -> {
            CommandGraphMutationResult result = null;
            Throwable failure = null;
            try {
                result = task.run();
            } catch (Throwable exception) {
                failure = exception;
            }
            CommandGraphMutationResult completedResult = result;
            Throwable completedFailure = failure;
            boolean scheduled = J.runEntity(
                    player,
                    () -> completeGraphMutation(
                            player,
                            studio,
                            requestId,
                            completedResult,
                            completedFailure),
                    0,
                    () -> finishGraphMutation(requestId));
            if (!scheduled) {
                finishGraphMutation(requestId);
                if (completedFailure != null) {
                    IrisLogging.reportError(completedFailure);
                }
            }
        });
        return true;
    }

    private void completeGraphMutation(
            Player player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            Throwable failure
    ) {
        try {
            if (failure != null) {
                IrisLogging.reportError(failure);
                message(player, "Graph update failed: " + failureMessage(failure));
                finishGraphMutation(requestId);
                return;
            }
            if (result == null) {
                message(player, "Graph update completed without a result; no Studio state changed.");
                finishGraphMutation(requestId);
                return;
            }
            if (!service.isCurrentRequest(studio, requestId)) {
                message(player, "The graph updated on disk after this Studio session changed; reopen it to continue.");
                finishGraphMutation(requestId);
                return;
            }
            JigsawStudioSession session = studio.generator().getSession();
            JigsawStudioLayout previousLayout = session.layout();
            if (result.rebindActiveVariants().isEmpty()) {
                session.replaceLayout(result.layout());
            } else {
                session.replaceLayoutAndRebind(result.layout(), result.rebindActiveVariants());
            }
            for (JigsawStudioBay workcell : result.layout().bays()) {
                studio.generator().invalidateRender(workcell.stableId());
            }
            if (layoutGeometryChanged(previousLayout, result.layout())) {
                scheduleLiveRelayout(player, studio, requestId, previousLayout, result);
                return;
            }
            finishGraphMutationSuccess(player, studio, requestId, result);
        } catch (Throwable exception) {
            IrisLogging.reportError(exception);
            message(player, "The graph updated on disk, but Studio could not refresh: "
                    + failureMessage(exception) + ". Reopen this project before editing further.");
            service.saveLifecycle.reopenRequiredRequests.add(requestId);
            finishGraphMutation(requestId);
        }
    }

    private void finishGraphMutationSuccess(
            Player player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result
    ) {
        for (JigsawStudioBay workcell : result.layout().bays()) {
            service.playerContext.refreshWorkcellContext(studio.worldId(), workcell.stableId());
        }
        service.evaluator.scheduleEvaluation(studio);
        message(player, result.message());
        finishGraphMutation(requestId);
        if (!result.activatePieceKey().isEmpty()) {
            service.variantEditor.switchVariant(
                    player,
                    result.activateWorkcellId(),
                    result.activatePieceKey(),
                    false);
        } else if (result.reload().isPresent()) {
            service.variantEditor.reloadActiveVariant(player, studio, result.reload().orElseThrow());
        }
    }

    private void scheduleLiveRelayout(
            Player player,
            ActiveStudio studio,
            UUID requestId,
            JigsawStudioLayout previousLayout,
            CommandGraphMutationResult result
    ) {
        Set<Long> chunks = relayoutChunks(previousLayout, result.layout());
        AtomicInteger remaining = new AtomicInteger(chunks.size());
        AtomicReference<String> failure = new AtomicReference<>("");
        AtomicReference<Throwable> cause = new AtomicReference<>();
        if (chunks.isEmpty()) {
            completeLiveRelayout(player, studio, requestId, result, "", null);
            return;
        }
        for (long chunkKey : chunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            boolean scheduled = J.runRegion(studio.world(), chunkX, chunkZ, () -> {
                try {
                    repaintStudioChunk(studio, chunkX, chunkZ);
                } catch (Throwable exception) {
                    failure.compareAndSet("", "chunk " + chunkX + "," + chunkZ
                            + " could not regenerate: " + failureMessage(exception));
                    cause.compareAndSet(null, exception);
                }
                if (remaining.decrementAndGet() == 0) {
                    scheduleLiveRelayoutCompletion(
                            player,
                            studio,
                            requestId,
                            result,
                            failure.get(),
                            cause.get());
                }
            });
            if (!scheduled) {
                failure.compareAndSet("", "chunk " + chunkX + "," + chunkZ
                        + " could not be scheduled on its owning region");
                if (remaining.decrementAndGet() == 0) {
                    scheduleLiveRelayoutCompletion(
                            player,
                            studio,
                            requestId,
                            result,
                            failure.get(),
                            cause.get());
                }
            }
        }
    }

    private void scheduleLiveRelayoutCompletion(
            Player player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            String failure,
            Throwable cause
    ) {
        boolean scheduled = J.runEntity(
                player,
                () -> completeLiveRelayout(player, studio, requestId, result, failure, cause));
        if (!scheduled) {
            service.saveLifecycle.reopenRequiredRequests.add(requestId);
            finishGraphMutation(requestId);
            if (cause != null) {
                IrisLogging.reportError(cause);
            }
        }
    }

    private void completeLiveRelayout(
            Player player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            String failure,
            Throwable cause
    ) {
        if (!failure.isEmpty() || !service.isCurrentRequest(studio, requestId)) {
            service.saveLifecycle.reopenRequiredRequests.add(requestId);
            studio.populations().clear();
            if (cause != null) {
                IrisLogging.reportError(cause);
            }
            message(player, "The workcell size was saved, but live regeneration failed"
                    + (failure.isEmpty() ? "." : ": " + failure + ".")
                    + " Close and reopen this project before editing further.");
            finishGraphMutation(requestId);
            return;
        }
        studio.populations().clear();
        for (JigsawStudioBay workcell : result.layout().bays()) {
            JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(workcell);
            studio.replacePopulation(
                    workcell,
                    rendered,
                    rendered.valid() ? "" : rendered.failure(),
                    rendered.valid());
        }
        service.saveLifecycle.reopenRequiredRequests.remove(requestId);
        finishGraphMutationSuccess(player, studio, requestId, result);
        studio.generator().getSession().selectedBayId().ifPresent(workcellId -> service.teleportTo(player, workcellId));
    }

    static boolean layoutGeometryChanged(
            JigsawStudioLayout previous,
            JigsawStudioLayout current
    ) {
        if (previous.bays().size() != current.bays().size()) {
            return true;
        }
        for (JigsawStudioBay previousBay : previous.bays()) {
            JigsawStudioBay currentBay = current.get(previousBay.stableId());
            if (currentBay == null || !previousBay.bounds().equals(currentBay.bounds())) {
                return true;
            }
        }
        return false;
    }

    static Set<Long> relayoutChunks(
            JigsawStudioLayout previous,
            JigsawStudioLayout current
    ) {
        Set<Long> chunks = new HashSet<>();
        addRelayoutChunks(chunks, previous);
        addRelayoutChunks(chunks, current);
        return Set.copyOf(chunks);
    }

    private static void addRelayoutChunks(Set<Long> chunks, JigsawStudioLayout layout) {
        for (JigsawStudioBay workcell : layout.bays()) {
            JigsawStudioBounds bounds = workcell.bounds();
            int minimumChunkX = (bounds.originX() - 1) >> 4;
            int maximumChunkX = (bounds.maxX() + 1) >> 4;
            int minimumChunkZ = (bounds.originZ() - 1) >> 4;
            int maximumChunkZ = (bounds.maxZ() + 1) >> 4;
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    chunks.add(JigsawStudioService.chunkKey(chunkX, chunkZ));
                }
            }
        }
    }

    private static void repaintStudioChunk(ActiveStudio studio, int chunkX, int chunkZ) throws IOException {
        World world = studio.world();
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        TerrainChunk generated = TerrainChunk.create(world);
        studio.generator().paintChunk(generated, chunkX, chunkZ);
        if (!INMS.get().applyChunkBlocks(chunk, generated)) {
            InPlaceChunkRegenerator.applyBlockDiffs(
                    chunk,
                    generated.getChunkData(),
                    world.getMinHeight(),
                    world.getMaxHeight());
        }
        for (JigsawStudioBay workcell : studio.generator().getLayout().bays()) {
            JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(workcell);
            if (!rendered.valid()) {
                continue;
            }
            boolean connectorsVisible = studio.generator().getSession()
                    .workcellSnapshot(workcell.stableId())
                    .connectorsVisible();
            applyRenderedBayChunk(world, workcell, rendered, chunkX, chunkZ, connectorsVisible);
            verifyRenderedBayChunk(world, workcell, rendered, chunkX, chunkZ, connectorsVisible);
        }
        world.refreshChunk(chunkX, chunkZ);
    }

    boolean graphMutationInProgress(UUID requestId) {
        synchronized (service.saveLifecycleLock) {
            return graphMutationsInProgress.contains(requestId);
        }
    }

    private void finishGraphMutation(UUID requestId) {
        synchronized (service.saveLifecycleLock) {
            graphMutationsInProgress.remove(requestId);
        }
    }

    static JigsawStudioLayout loadMappedLayout(ActiveStudio studio) throws IOException {
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        IrisData source = request.source();
        source.invalidateStructureResources();
        IrisStructure structure = source.load(IrisStructure.class, request.structureKey(), false);
        if (structure == null) {
            throw new IOException("The structure could not be reloaded after the graph transaction");
        }
        return JigsawStudioGraphMapper.map(source, structure);
    }
}
