package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteOptions;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.LocalPosition;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.BlockClick;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Box;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Cell;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.CellContent;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Decision;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Frame;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Identity;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Operation;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Ray;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.ReseatResult;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Target;
import art.arcane.iris.studio.jigsaw.JigsawStudioMaterializer.ConnectorEditReservation;
import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.studio.jigsaw.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.AUTOSAVE_RETRY_TICKS;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.chunkKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.failureMessage;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.message;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.writeFailure;

final class JigsawStudioConnectorTool {
    private static final int MAX_QUEUED_ATTEMPTS = 48;
    private static final double HOVER_REACH = 5.0D;
    private static final String HUD_PURPOSE = "iris:jigsaw-connector";
    private static final long HUD_MILLIS = 4000L;

    private final JigsawStudioService service;
    final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    final Map<UUID, PendingClick> pendingClicks = new ConcurrentHashMap<>();

    JigsawStudioConnectorTool(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean handleClick(
            Player player,
            JigsawStudioToolPayload payload,
            boolean primary,
            Block clicked,
            BlockFace face
    ) {
        if (player == null || payload == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> handleClick(player, payload, primary, clicked, face));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !studio.generator().getRequest().requestId().equals(payload.requestId())) {
            actionBar(player, ChatColor.RED + "This Connector Tool belongs to a different or closed Jigsaw Studio.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (!primary && clicked != null && clicked.getType() == Material.JIGSAW) {
            player.closeInventory();
        }
        Resolution resolution = resolve(player, studio, primary, blockClick(clicked, face));
        if (resolution.click().isEmpty()) {
            actionBar(player, ChatColor.YELLOW + resolution.failure());
            return false;
        }
        process(player, studio, resolution.click().get(), 0);
        return true;
    }

    Optional<Feedback> feedback(Player player, ActiveStudio studio, boolean holdingTool) {
        Optional<Cell> selected = selectedCell(player, studio);
        Optional<Hover> hover = Optional.empty();
        if (holdingTool) {
            RayTraceResult trace = player.rayTraceBlocks(HOVER_REACH);
            Optional<BlockClick> click = trace == null
                    ? Optional.empty()
                    : blockClick(trace.getHitBlock(), trace.getHitBlockFace());
            Resolution resolution = resolve(player, studio, false, click);
            if (resolution.click().isPresent() && resolution.box() != null) {
                ToolClick toolClick = resolution.click().get();
                hover = Optional.of(new Hover(
                        resolution.box().world(toolClick.target()),
                        toolClick.facing(),
                        resolution.existingConnector()));
            }
        }
        if (selected.isEmpty() && hover.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Feedback(selected, hover));
    }

    void clearPlayer(UUID playerId) {
        selections.remove(playerId);
        pendingClicks.remove(playerId);
    }

    private Optional<Cell> selectedCell(Player player, ActiveStudio studio) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.requestId().equals(studio.generator().getRequest().requestId())) {
            return Optional.empty();
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(selection.workcellId());
        if (workcell == null || session.activeVariant(workcell.stableId())
                .map(JigsawStudioVariant::pieceKey)
                .filter(selection.pieceKey()::equals)
                .isEmpty()) {
            return Optional.empty();
        }
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(workcell);
        if (!rendered.valid() || !displayedConnectors(rendered).containsKey(selection.position())) {
            return Optional.empty();
        }
        return Optional.of(Box.of(workcell.bounds(), rendered.dimensions()).world(selection.position()));
    }

    private Resolution resolve(
            Player player,
            ActiveStudio studio,
            boolean primary,
            Optional<BlockClick> click
    ) {
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioLayout layout = session.layout();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 1.0E-9D) {
            return Resolution.failed("Look at a workcell to use the Connector Tool.");
        }
        Ray ray = new Ray(eye.getX(), eye.getY(), eye.getZ(), direction.getX(), direction.getY(), direction.getZ());
        JigsawStudioBay workcell = targetWorkcell(layout, player.getLocation(), ray, click);
        if (workcell == null) {
            return Resolution.failed("Point the Connector Tool at a workcell.");
        }
        JigsawStudioVariant variant = session.activeVariant(workcell.stableId()).orElse(null);
        if (variant == null) {
            return Resolution.failed(workcell.displayName() + " has no loaded variant.");
        }
        if (!variant.owned()) {
            return Resolution.failed("Variant '" + variant.pieceKey() + "' is read-only.");
        }
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(workcell);
        if (!rendered.valid()) {
            return Resolution.failed(workcell.displayName() + " cannot be edited: " + rendered.failure());
        }
        Box box = Box.of(workcell.bounds(), rendered.dimensions());
        Map<LocalPosition, IrisDirection> connectors = displayedConnectors(rendered);
        Set<Cell> cells = new HashSet<>(connectors.size());
        for (LocalPosition position : connectors.keySet()) {
            cells.add(box.world(position));
        }
        Optional<Target> target = JigsawStudioConnectorPlacement.target(box, ray, click, cells, player.isSneaking());
        if (target.isEmpty()) {
            return Resolution.failed("Point at a block or wall inside the "
                    + JigsawStudioObjectResizer.describeDimensions(rendered.dimensions()) + " variant.");
        }
        LocalPosition position = box.local(target.get());
        IrisDirection top = target.get().facing().isVertical()
                ? IrisDirection.getDirection(player.getFacing())
                : IrisDirection.UP_POSITIVE_Y;
        ToolClick toolClick = new ToolClick(
                studio.generator().getRequest().requestId(),
                workcell.stableId(),
                variant.pieceKey(),
                session.workcellSnapshot(workcell.stableId()).loadGeneration(),
                primary,
                position,
                target.get().facing(),
                top);
        return new Resolution(Optional.of(toolClick), box, connectors.containsKey(position), "");
    }

    private void process(Player player, ActiveStudio studio, ToolClick click, int attempt) {
        if (!service.isCurrentRequest(studio, click.requestId())) {
            actionBar(player, ChatColor.RED + "This Jigsaw Studio session is no longer active.");
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(click.workcellId());
        JigsawStudioVariant variant = workcell == null ? null : session.activeVariant(workcell.stableId()).orElse(null);
        if (variant == null
                || !variant.pieceKey().equals(click.pieceKey())
                || session.workcellSnapshot(workcell.stableId()).loadGeneration() != click.loadGeneration()) {
            actionBar(player, ChatColor.YELLOW + "The workcell changed before the connector edit ran; click again.");
            return;
        }
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcell.stableId());
        if (snapshot.dirty() || snapshot.saveInProgress() || snapshot.switchInProgress()) {
            queue(player, studio, click, attempt, "Saving your latest edits before changing connectors...");
            return;
        }
        JigsawStudioSaveLifecycle.BayReadiness readiness = studio.population(workcell).readiness();
        if (!readiness.ready()) {
            if (!readiness.failure().isEmpty()) {
                actionBar(player, ChatColor.RED + workcell.displayName() + " is not editable: " + readiness.failure());
                return;
            }
            queue(player, studio, click, attempt, "Waiting for " + workcell.displayName() + " to finish loading...");
            return;
        }
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(workcell);
        if (!rendered.valid()) {
            actionBar(player, ChatColor.RED + workcell.displayName() + " cannot be edited: " + rendered.failure());
            return;
        }
        Map<LocalPosition, IrisDirection> connectors = displayedConnectors(rendered);
        Optional<LocalPosition> selection = currentSelection(player, click);
        Decision decision = workcell.topology().isPresent()
                ? JigsawStudioConnectorPlacement.decidePlanar(
                        click.primary(),
                        click.target(),
                        click.facing(),
                        rendered.dimensions(),
                        workcell.topology().get(),
                        connectors,
                        workcell.displayName())
                : JigsawStudioConnectorPlacement.decideSpatial(
                        click.primary(),
                        click.target(),
                        click.facing(),
                        connectors,
                        selection);
        switch (decision.operation()) {
            case SELECT -> {
                selections.put(player.getUniqueId(), new Selection(
                        click.requestId(), click.workcellId(), click.pieceKey(), decision.position()));
                actionBar(player, ChatColor.AQUA + "Selected the connector at "
                        + JigsawStudioConnectorPlacement.describe(decision.position()) + " facing "
                        + directionName(connectors.get(decision.position()))
                        + ChatColor.GRAY + ". Right-click a new spot to move it, the same block to deselect, or left-click to remove it.");
            }
            case DESELECT, CLEAR_SELECTION -> {
                selections.remove(player.getUniqueId());
                actionBar(player, ChatColor.GRAY + "Connector deselected.");
            }
            case REFUSE -> actionBar(player, ChatColor.RED + decision.message());
            case NONE -> actionBar(player, ChatColor.GRAY + decision.message());
            case ADD, MOVE, REORIENT, REMOVE, RESEAT -> write(
                    player,
                    studio,
                    workcell,
                    variant,
                    new Edit(decision, click, selection),
                    attempt);
        }
    }

    private void write(
            Player player,
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioVariant variant,
            Edit edit,
            int attempt
    ) {
        ConnectorEditReservation reservation = service.materializer.beginConnectorEdit(studio, workcell.stableId());
        if (reservation.status() == JigsawStudioMaterializer.ConnectorEditStatus.BUSY) {
            queue(player, studio, edit.click(), attempt, reservation.message());
            return;
        }
        if (reservation.status() == JigsawStudioMaterializer.ConnectorEditStatus.BLOCKED) {
            actionBar(player, ChatColor.RED + reservation.message());
            return;
        }
        UUID requestId = edit.click().requestId();
        J.a(() -> {
            EditCommit commit = null;
            Throwable failure = null;
            try {
                commit = commitEdit(studio, workcell, variant, edit);
            } catch (Throwable exception) {
                failure = exception;
            }
            EditCommit completed = commit;
            Throwable completedFailure = failure;
            boolean written = completed != null && completed.changed();
            boolean scheduled = J.runEntity(
                    player,
                    () -> completeEdit(player, studio, edit, completed, completedFailure),
                    0,
                    () -> abandonEdit(requestId, written, completedFailure));
            if (!scheduled) {
                abandonEdit(requestId, written, completedFailure);
            }
        });
    }

    private EditCommit commitEdit(
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioVariant variant,
            Edit edit
    ) throws IOException {
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        IrisData source = request.source();
        Path packRoot = source.getDataFolder().toPath();
        IrisJigsawPiece piece = source.getJigsawPieceLoader().load(variant.pieceKey(), false);
        if (piece == null || piece.getConnectors() == null || piece.getObject() == null || piece.getObject().isBlank()) {
            throw new IOException("Piece '" + variant.pieceKey() + "' is missing or has no object");
        }
        IrisObject object = source.getObjectLoader().load(piece.getObject(), false);
        if (object == null || object.getW() < 1 || object.getH() < 1 || object.getD() < 1) {
            throw new IOException("Object '" + piece.getObject() + "' is missing or has invalid dimensions");
        }
        Frame frame = new Frame(
                new JigsawStudioCellDimensions(object.getW(), object.getH(), object.getD()),
                variant.sourceToCanonicalQuarterTurns());
        JigsawStudioConnectorPlacement.CellContents contents = position -> cellContent(object, position);
        List<IrisJigsawConnector> before = List.copyOf(piece.getConnectors());
        LocalPosition target = edit.click().target();
        IrisDirection facing = edit.click().facing();
        IrisDirection top = edit.click().top();
        List<IrisJigsawConnector> after;
        String summary;
        Selection selection = null;
        switch (edit.decision().operation()) {
            case ADD -> {
                after = JigsawStudioConnectorPlacement.add(
                        before, frame, target, facing, top, defaultIdentity(studio, variant, before, packRoot), contents);
                summary = "Placed a connector at " + JigsawStudioConnectorPlacement.describe(target)
                        + " facing " + directionName(facing) + ".";
            }
            case MOVE -> {
                LocalPosition from = edit.selection().orElseThrow();
                after = JigsawStudioConnectorPlacement.move(before, frame, from, target, facing, top, contents);
                summary = "Moved the connector from " + JigsawStudioConnectorPlacement.describe(from)
                        + " to " + JigsawStudioConnectorPlacement.describe(target)
                        + " facing " + directionName(facing) + ".";
                selection = new Selection(
                        edit.click().requestId(), edit.click().workcellId(), edit.click().pieceKey(), target);
            }
            case REORIENT -> {
                after = JigsawStudioConnectorPlacement.move(before, frame, target, target, facing, top, contents);
                summary = "Turned the connector at " + JigsawStudioConnectorPlacement.describe(target)
                        + " to face " + directionName(facing) + ".";
                selection = new Selection(
                        edit.click().requestId(), edit.click().workcellId(), edit.click().pieceKey(), target);
            }
            case REMOVE -> {
                after = JigsawStudioConnectorPlacement.remove(before, frame, target);
                summary = "Removed the connector at " + JigsawStudioConnectorPlacement.describe(target) + ".";
            }
            case RESEAT -> {
                JigsawPlanarDirection side = Objects.requireNonNull(edit.decision().side(), "Planar socket side");
                ReseatResult result = JigsawStudioConnectorPlacement.reseat(
                        before, frame, side, defaultIdentity(studio, variant, before, packRoot), contents);
                if (!result.changed()) {
                    return EditCommit.unchanged("The " + JigsawStudioConnectorPlacement.sideName(side)
                            + " socket is already seated at its face center.");
                }
                after = result.connectors();
                summary = "Seated the " + JigsawStudioConnectorPlacement.sideName(side) + " socket at its face center"
                        + (result.removedStrays() == 0 ? "." : " and removed " + result.removedStrays()
                        + " stray connector" + (result.removedStrays() == 1 ? "." : "s."));
            }
            default -> throw new IllegalStateException("Unsupported connector edit " + edit.decision().operation());
        }
        JigsawStudioCapture.requireWorkcellTopology(workcell, after, frame.quarterTurns());
        JigsawStudioResourceBundleAssembler.Assembly assembly = JigsawStudioResourceBundleAssembler.assemble(
                packRoot,
                request.structureKey(),
                variant.pieceKey(),
                JigsawStudioResourceBundleAssembler.ownedResourceContent(
                        packRoot,
                        "objects/" + piece.getObject() + ".iob"),
                after,
                false);
        StructureResourceBundleGraphCompiler.requireViable(assembly.bundle());
        JigsawStudioHistoryStore historyStore = new JigsawStudioHistoryStore(packRoot, request.structureKey());
        JigsawStudioHistoryStore.Snapshot previous = historyStore.snapshotCurrent(variant.pieceKey());
        if (!previous.matches(assembly.bundle())) {
            historyStore.append(previous);
        }
        StructureWriteResult result;
        synchronized (service.saveLifecycleLock) {
            result = new StructureTransactionWriter(packRoot)
                    .write(assembly.bundle(), StructureWriteOptions.overwriteExpected(assembly.expectedManifestHash()));
        }
        if (!result.successful()) {
            throw new IOException(writeFailure(result));
        }
        source.invalidateStructureResources();
        return new EditCommit(
                true,
                loadMappedLayout(studio),
                JigsawStudioConnectorPlacement.changedPositions(before, after, frame),
                summary,
                Optional.ofNullable(selection),
                edit.decision().operation() == Operation.REMOVE ? Optional.of(target) : Optional.empty());
    }

    private void completeEdit(
            Player player,
            ActiveStudio studio,
            Edit edit,
            EditCommit commit,
            Throwable failure
    ) {
        UUID requestId = edit.click().requestId();
        if (failure != null) {
            service.materializer.finishConnectorEdit(requestId);
            if (!(failure instanceof IllegalArgumentException)) {
                IrisLogging.reportError("Jigsaw Studio connector edit failed in workcell "
                        + edit.click().workcellId(), failure);
            }
            message(player, "Connector edit failed: " + failureMessage(failure));
            actionBar(player, ChatColor.RED + "Connector edit failed: " + failureMessage(failure));
            return;
        }
        if (!commit.changed()) {
            service.materializer.finishConnectorEdit(requestId);
            actionBar(player, ChatColor.GRAY + commit.summary());
            return;
        }
        if (!service.isCurrentRequest(studio, requestId)) {
            service.materializer.finishConnectorEdit(requestId);
            message(player, "The connector edit saved after this Studio session changed; reopen it to continue.");
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell;
        JigsawStudioGenerator.RenderedBay rendered;
        try {
            session.replaceLayout(commit.layout());
            for (JigsawStudioBay bay : commit.layout().bays()) {
                studio.generator().invalidateRender(bay.stableId());
            }
            workcell = session.layout().get(edit.click().workcellId());
            if (workcell == null) {
                throw new IOException("workcell '" + edit.click().workcellId() + "' left the layout");
            }
            rendered = studio.generator().renderBay(workcell);
            if (!rendered.valid()) {
                throw new IOException(rendered.failure());
            }
        } catch (Throwable exception) {
            service.saveLifecycle.reopenRequiredRequests.add(requestId);
            service.materializer.finishConnectorEdit(requestId);
            IrisLogging.reportError("Jigsaw Studio saved a connector edit but could not refresh workcell "
                    + edit.click().workcellId(), exception);
            message(player, "The connector edit saved, but Studio could not refresh: "
                    + failureMessage(exception) + ". Reopen this project before editing further.");
            return;
        }
        restoreWorld(player, studio, workcell, rendered, commit,
                session.workcellSnapshot(workcell.stableId()).connectorsVisible());
    }

    private void restoreWorld(
            Player player,
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            EditCommit commit,
            boolean connectorsVisible
    ) {
        Map<LocalPosition, JigsawStudioGenerator.RenderedConnector> connectors = new HashMap<>();
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            connectors.put(new LocalPosition(connector.x(), connector.y(), connector.z()), connector);
        }
        Map<LocalPosition, JigsawStudioGenerator.RenderedBlock> blocks = new HashMap<>();
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            blocks.put(new LocalPosition(block.x(), block.y(), block.z()), block);
        }
        JigsawStudioBounds bounds = workcell.bounds();
        Map<Long, Set<LocalPosition>> positionsByChunk = new LinkedHashMap<>();
        for (LocalPosition position : commit.changedPositions()) {
            positionsByChunk.computeIfAbsent(
                    chunkKey((bounds.originX() + position.x()) >> 4, (bounds.originZ() + position.z()) >> 4),
                    ignored -> new HashSet<>()).add(position);
        }
        if (positionsByChunk.isEmpty()) {
            finishEdit(player, studio, workcell.stableId(), commit, "", null);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(positionsByChunk.size());
        AtomicReference<String> failure = new AtomicReference<>("");
        AtomicReference<Throwable> cause = new AtomicReference<>();
        for (Map.Entry<Long, Set<LocalPosition>> entry : positionsByChunk.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) entry.getKey().longValue();
            boolean scheduled = J.runRegion(studio.world(), chunkX, chunkZ, () -> {
                try {
                    if (!studio.world().isChunkLoaded(chunkX, chunkZ)) {
                        throw new IOException("workcell chunk " + chunkX + "," + chunkZ + " is not loaded");
                    }
                    JigsawStudioChunkWriter.restoreChunkPositions(
                            studio.world(), workcell, entry.getValue(), connectors, blocks, connectorsVisible);
                } catch (Throwable exception) {
                    failure.compareAndSet("", failureMessage(exception));
                    cause.compareAndSet(null, exception);
                }
                if (remaining.decrementAndGet() == 0) {
                    scheduleFinish(player, studio, workcell.stableId(), commit, failure.get(), cause.get());
                }
            });
            if (!scheduled) {
                failure.compareAndSet("", "workcell chunk " + chunkX + "," + chunkZ
                        + " could not be scheduled on its owning region");
                if (remaining.decrementAndGet() == 0) {
                    scheduleFinish(player, studio, workcell.stableId(), commit, failure.get(), cause.get());
                }
            }
        }
    }

    private void scheduleFinish(
            Player player,
            ActiveStudio studio,
            String workcellId,
            EditCommit commit,
            String failure,
            Throwable cause
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        boolean scheduled = J.runEntity(
                player,
                () -> finishEdit(player, studio, workcellId, commit, failure, cause),
                0,
                () -> {
                    service.materializer.finishConnectorEdit(requestId);
                    service.floorPainter.refresh(studio, workcellId);
                });
        if (!scheduled) {
            service.materializer.finishConnectorEdit(requestId);
            service.floorPainter.refresh(studio, workcellId);
        }
    }

    private void finishEdit(
            Player player,
            ActiveStudio studio,
            String workcellId,
            EditCommit commit,
            String failure,
            Throwable cause
    ) {
        service.materializer.finishConnectorEdit(studio.generator().getRequest().requestId());
        commit.selection().ifPresentOrElse(
                selection -> selections.put(player.getUniqueId(), selection),
                () -> commit.removed().ifPresent(removed -> selections.computeIfPresent(
                        player.getUniqueId(),
                        (ignored, current) -> current.workcellId().equals(workcellId)
                                && current.position().equals(removed) ? null : current)));
        service.floorPainter.refresh(studio, workcellId);
        service.playerContext.refreshWorkcellContext(studio.worldId(), workcellId);
        service.evaluator.scheduleEvaluation(studio);
        if (!failure.isEmpty()) {
            if (cause != null) {
                IrisLogging.reportError("Jigsaw Studio could not update connector blocks in workcell " + workcellId, cause);
            }
            message(player, commit.summary() + " The workcell blocks could not update: " + failure
                    + ". Visit the whole workcell and use Reset Connector Blocks.");
            return;
        }
        actionBar(player, ChatColor.GREEN + commit.summary());
        player.playSound(player.getLocation(), "minecraft:block.note_block.hat", 0.6F, 1.4F);
    }

    private void abandonEdit(UUID requestId, boolean written, Throwable failure) {
        if (written) {
            service.saveLifecycle.reopenRequiredRequests.add(requestId);
        }
        service.materializer.finishConnectorEdit(requestId);
        if (failure != null && !(failure instanceof IllegalArgumentException)) {
            IrisLogging.reportError("Jigsaw Studio connector edit failed after its player left", failure);
        }
    }

    private void queue(Player player, ActiveStudio studio, ToolClick click, int attempt, String reason) {
        int nextAttempt = attempt + 1;
        if (nextAttempt > MAX_QUEUED_ATTEMPTS) {
            pendingClicks.remove(player.getUniqueId());
            actionBar(player, ChatColor.RED + "The connector edit was cancelled: " + reason);
            return;
        }
        PendingClick pending = new PendingClick(click, nextAttempt);
        PendingClick previous = pendingClicks.put(player.getUniqueId(), pending);
        service.autosaveScheduler.expediteAutosaves(click.requestId());
        if (previous == null || attempt == 0) {
            actionBar(player, ChatColor.GRAY + reason);
        }
        boolean scheduled = J.runEntity(
                player,
                () -> resumePending(player, studio, pending),
                AUTOSAVE_RETRY_TICKS,
                () -> pendingClicks.remove(player.getUniqueId(), pending));
        if (!scheduled) {
            pendingClicks.remove(player.getUniqueId(), pending);
        }
    }

    private void resumePending(Player player, ActiveStudio studio, PendingClick pending) {
        if (!pendingClicks.remove(player.getUniqueId(), pending)) {
            return;
        }
        process(player, studio, pending.click(), pending.attempt());
    }

    private Optional<LocalPosition> currentSelection(Player player, ToolClick click) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null
                || !selection.requestId().equals(click.requestId())
                || !selection.workcellId().equals(click.workcellId())
                || !selection.pieceKey().equals(click.pieceKey())) {
            return Optional.empty();
        }
        return Optional.of(selection.position());
    }

    private Identity defaultIdentity(
            ActiveStudio studio,
            JigsawStudioVariant variant,
            List<IrisJigsawConnector> existing,
            Path packRoot
    ) {
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        boolean planar = variant.mode() == JigsawStudioMode.PLANAR_JIGSAW;
        String generatedPool = request.structureKey() + "/pieces";
        String pool;
        if (Files.isRegularFile(packRoot.resolve("jigsaw-pools/" + generatedPool + ".json"))) {
            pool = generatedPool;
        } else if (!existing.isEmpty() && existing.getFirst().getPool() != null
                && !existing.getFirst().getPool().isBlank()) {
            pool = existing.getFirst().getPool();
        } else if (!variant.memberships().isEmpty()) {
            pool = variant.memberships().getFirst().poolKey();
        } else {
            throw new IllegalArgumentException("Variant '" + variant.pieceKey()
                    + "' has no pool for a new connector; add it to a pool first.");
        }
        return new Identity(
                pool,
                planar ? "iris:planar" : "iris:spatial",
                planar ? "iris:planar" : "iris:spatial",
                planar ? JigsawJoint.ALIGNED : JigsawJoint.ROLLABLE);
    }

    private static Optional<CellContent> cellContent(IrisObject object, IrisPosition position) {
        NativeBlockState state = object.getBlocks().get(object.getSigned(position.getX(), position.getY(), position.getZ()));
        if (state == null || state.isAir()) {
            return Optional.empty();
        }
        boolean blockEntity = object.getStates().get(object.getSigned(position.getX(), position.getY(), position.getZ())) != null;
        return Optional.of(new CellContent(state.key(), blockEntity));
    }

    private static JigsawStudioBay targetWorkcell(
            JigsawStudioLayout layout,
            Location feet,
            Ray ray,
            Optional<BlockClick> click
    ) {
        if (click.isPresent()) {
            BlockClick value = click.get();
            JigsawStudioBay clicked = layout.findAt(value.x(), value.y(), value.z());
            if (clicked != null) {
                return clicked;
            }
            JigsawStudioBay adjacent = layout.findAt(
                    value.x() + value.face().x(),
                    value.y() + value.face().y(),
                    value.z() + value.face().z());
            if (adjacent != null) {
                return adjacent;
            }
        }
        JigsawStudioBay standing = layout.findColumn(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
        if (standing != null) {
            return standing;
        }
        JigsawStudioBay nearest = null;
        double nearestDistance = JigsawStudioConnectorPlacement.MAX_REACH;
        for (JigsawStudioBay bay : layout.bays()) {
            JigsawStudioBounds bounds = bay.bounds();
            double distance = JigsawStudioConnectorPlacement.entryDistance(ray, Box.of(bounds, bounds.dimensions()));
            if (distance < nearestDistance) {
                nearest = bay;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Optional<BlockClick> blockClick(Block clicked, BlockFace face) {
        if (clicked == null || face == null) {
            return Optional.empty();
        }
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST, UP, DOWN -> Optional.of(new BlockClick(
                    clicked.getX(), clicked.getY(), clicked.getZ(), IrisDirection.getDirection(face)));
            default -> Optional.empty();
        };
    }

    private static Map<LocalPosition, IrisDirection> displayedConnectors(JigsawStudioGenerator.RenderedBay rendered) {
        Map<LocalPosition, IrisDirection> connectors = new LinkedHashMap<>(rendered.connectors().size());
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            connectors.put(new LocalPosition(connector.x(), connector.y(), connector.z()),
                    connector.connector().getDirection());
        }
        return connectors;
    }

    static String directionName(IrisDirection direction) {
        if (direction == null) {
            return "nowhere";
        }
        return switch (direction) {
            case UP_POSITIVE_Y -> "up";
            case DOWN_NEGATIVE_Y -> "down";
            case NORTH_NEGATIVE_Z -> "north";
            case SOUTH_POSITIVE_Z -> "south";
            case EAST_POSITIVE_X -> "east";
            case WEST_NEGATIVE_X -> "west";
        };
    }

    private static void actionBar(Player player, String text) {
        if (BukkitPlatform.hasHud()) {
            BukkitPlatform.hudBar().publish(player, new HudSegment(
                    HUD_PURPOSE,
                    HudPriority.INTERACTIVE,
                    HUD_MILLIS,
                    List.of(HudSlot.CENTER),
                    text));
            return;
        }
        ComponentMessenger.sendActionBarLegacy(player, text);
    }

    record Selection(UUID requestId, String workcellId, String pieceKey, LocalPosition position) {
        Selection {
            Objects.requireNonNull(requestId, "Jigsaw connector selection request");
            Objects.requireNonNull(workcellId, "Jigsaw connector selection workcell");
            Objects.requireNonNull(pieceKey, "Jigsaw connector selection piece");
            Objects.requireNonNull(position, "Jigsaw connector selection position");
        }
    }

    record PendingClick(ToolClick click, int attempt) {
        PendingClick {
            Objects.requireNonNull(click, "Jigsaw connector pending click");
        }
    }

    record ToolClick(
            UUID requestId,
            String workcellId,
            String pieceKey,
            long loadGeneration,
            boolean primary,
            LocalPosition target,
            IrisDirection facing,
            IrisDirection top
    ) {
        ToolClick {
            Objects.requireNonNull(requestId, "Jigsaw connector click request");
            Objects.requireNonNull(workcellId, "Jigsaw connector click workcell");
            Objects.requireNonNull(pieceKey, "Jigsaw connector click piece");
            Objects.requireNonNull(target, "Jigsaw connector click target");
            Objects.requireNonNull(facing, "Jigsaw connector click facing");
            Objects.requireNonNull(top, "Jigsaw connector click top");
        }
    }

    record Hover(Cell cell, IrisDirection facing, boolean existingConnector) {
    }

    record Feedback(Optional<Cell> selected, Optional<Hover> hover) {
    }

    private record Resolution(Optional<ToolClick> click, Box box, boolean existingConnector, String failure) {
        private static Resolution failed(String failure) {
            return new Resolution(Optional.empty(), null, false, failure);
        }
    }

    private record Edit(Decision decision, ToolClick click, Optional<LocalPosition> selection) {
    }

    private record EditCommit(
            boolean changed,
            JigsawStudioLayout layout,
            Set<LocalPosition> changedPositions,
            String summary,
            Optional<Selection> selection,
            Optional<LocalPosition> removed
    ) {
        private static EditCommit unchanged(String summary) {
            return new EditCommit(false, null, Set.of(), summary, Optional.empty(), Optional.empty());
        }
    }
}
