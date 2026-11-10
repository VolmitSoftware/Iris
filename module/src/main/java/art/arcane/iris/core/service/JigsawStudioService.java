package art.arcane.iris.core.service;

import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarDirection;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMarkerKeyCodec;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolMembership;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioProjectDeletionService;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioStructureEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioTripleSneakTracker;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.framework.structure.StructureGraphCompilation;
import art.arcane.iris.engine.framework.structure.StructureGraphCompiler;
import art.arcane.iris.engine.framework.structure.StructureGraphDiagnostic;
import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.ObjectCodec;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.Particle;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolText;
import com.volmit.tooling.world.ToolUse;
import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldListener;
import com.volmit.tooling.world.ToolWorldSpec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class JigsawStudioService implements JigsawStudioMenuController.Actions {
    private static final String WORLD_NAME_PREFIX = "jigsaw-";
    private static final String MESSAGE_PREFIX = "[Iris Jigsaw Studio] ";
    private static final ResourceId SAVE_SOUND = ResourceId.parse("minecraft:block.note_block.bell");
    private static final float SAVE_SOUND_VOLUME = 0.65F;
    private static final float SAVE_SOUND_PITCH = 1.65F;
    private static final int CHUNK_SHIFT = 4;
    private static final float TELEPORT_PITCH_DEGREES = 45.0F;
    private static final int RECONCILE_RETRY_TICKS = 1;
    private static final double BLOCK_CENTER = 0.5D;
    private static final int AUTOSAVE_DEBOUNCE_TICKS = 40;
    private static final int AUTOSAVE_RETRY_TICKS = 5;
    private static final int REPLACEMENT_CLOSE_WAIT_TICKS = 2_400;
    private static final List<Integer> AUTOSAVE_PERSISTENT_RETRY_DELAYS =
            List.of(40, 80, 160, 320, 600);
    private static final long PREVIEW_SEED = 1337L;
    private static final int SPATIAL_PREVIEW_BASE_Y = JigsawStudioLayout.FLOOR_Y + 48;
    private static final JigsawStudioPieceRules DEFAULT_PIECE_RULES =
            new JigsawStudioPieceRules(0, 30, 0, 0, false);
    private static final long TOOL_CONFIRM_NANOS = 10_000_000_000L;
    private static final int VISUAL_INTERVAL_TICKS = 8;
    private static final int PARTICLE_BUDGET = 384;
    private static final int HYDRATION_RETRY_TICKS = 2;
    private static final int MAX_HYDRATION_ATTEMPTS = 40;
    private static final int JIGSAW_TILE_WATCH_INTERVAL_TICKS = 5;
    private static final double VISUAL_RANGE = 96.0D;
    private static final double VISUAL_RANGE_SQUARED = VISUAL_RANGE * VISUAL_RANGE;
    private static final int SELECTED_COLOR = 0x00FFFF;
    private static final int NEARBY_COLOR = rgb(92, 102, 118);
    private static final int INVALID_BAY_COLOR = 0xFF0000;
    private static final int INVALID_CONNECTOR_COLOR = 0xFF0000;
    private static final int VALID_CONNECTOR_COLOR = 0x00FF00;
    private static final int ASSEMBLY_PREVIEW_COLOR = rgb(180, 90, 255);
    private static final int LIVE_PREVIEW_WARNING_COLOR = rgb(255, 180, 55);
    private static final long ASSEMBLY_PREVIEW_MILLIS = 10_000L;
    private static final Set<String> MUTATING_COMMANDS = Set.of(
            "biome", "brush", "chunk", "clone", "copy", "cut", "data", "deform", "drain",
            "execute", "faces", "fill", "fillbiome", "fixlava", "fixwater", "flip", "flora",
            "forest", "function", "gmask", "green", "hollow", "item", "load", "mask", "move",
            "naturalize", "overlay", "paste", "place", "redo", "regen", "replace", "replacenear",
            "restore", "rotate", "schedule", "schem", "schematic", "set", "setblock", "smooth",
            "snow", "sphere", "stack", "thaw", "undo", "walls");
    private static final Set<String> SAFE_NON_OWNER_COMMANDS = Set.of(
            "help", "list", "me", "msg", "ping", "pl", "plugins", "r", "reply", "rules",
            "say", "tell", "ver", "version", "w", "whisper");
    private static final Comparator<IrisJigsawConnector> NEW_CONNECTOR_SOURCE_POSITION_ORDER = Comparator
            .comparingInt((IrisJigsawConnector connector) -> connector.getPosition().x())
            .thenComparingInt(connector -> connector.getPosition().y())
            .thenComparingInt(connector -> connector.getPosition().z());
    private final ModuleLog log;
    private final JigsawStudioRegistry registry = new JigsawStudioRegistry();
    private final JigsawStudioScheduler scheduler;
    private final Map<UUID, ActiveStudio> studios = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> earlyChunks = new ConcurrentHashMap<>();
    private final Set<UUID> particlesDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> visualizationLoops = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AssemblyPreview> assemblyPreviews = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerWorkcellContext> playerWorkcells = new ConcurrentHashMap<>();
    private final Map<AutosaveKey, AutosaveTicket> autosaves = new ConcurrentHashMap<>();
    private final Map<UUID, DeferredDuplication> deferredDuplications = new ConcurrentHashMap<>();
    private final Map<JigsawTileWatchKey, JigsawTileWatch> jigsawTileWatches = new ConcurrentHashMap<>();
    private final Map<UUID, ToolConfirmation> toolConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, JigsawStudioGraphEvaluation> evaluations = new ConcurrentHashMap<>();
    private final JigsawStudioTripleSneakTracker tripleSneakTracker = new JigsawStudioTripleSneakTracker();
    private final JigsawStudioToolCodec toolCodec = new JigsawStudioToolCodec();
    private final JigsawStudioPreviewRenderer previewRenderer;
    private final Object saveLifecycleLock = new Object();
    private final Set<UUID> savesInProgress = new HashSet<>();
    private final Set<UUID> graphMutationsInProgress = new HashSet<>();
    private final Set<UUID> materializationsInProgress = new HashSet<>();
    private final Set<UUID> exportsInProgress = new HashSet<>();
    private final Set<UUID> closingRequests = new HashSet<>();
    private final Set<UUID> discardingRequests = new HashSet<>();
    private final Set<UUID> reopenRequiredRequests = ConcurrentHashMap.newKeySet();
    private final Set<UUID> unregisterRetries = ConcurrentHashMap.newKeySet();
    private final Set<UUID> unregisterDrainWarnings = ConcurrentHashMap.newKeySet();
    private volatile JigsawStudioMenuController menuController;

    public JigsawStudioService(ModuleLog log) {
        this.log = Objects.requireNonNull(log, "Jigsaw Studio log");
        this.scheduler = new JigsawStudioScheduler(log);
        this.previewRenderer = new JigsawStudioPreviewRenderer(log);
        this.menuController = new JigsawStudioMenuController(this, scheduler);
    }

    public JigsawStudioRegistry registry() {
        return registry;
    }

    private void reportError(Throwable cause) {
        log.error("A Jigsaw Studio operation failed.", cause);
    }

    private void reportError(String message, Throwable cause) {
        log.error(message, cause);
    }

    private void warn(String format, Object... arguments) {
        log.warn(String.format(Locale.ROOT, format, arguments));
    }

    private void debug(String format, Object... arguments) {
        if (log.debugging()) {
            log.debug(String.format(Locale.ROOT, format, arguments));
        }
    }

    public static void clearAutosaveHistory(Path packRoot, String structureKey) throws IOException {
        new JigsawStudioHistoryStore(packRoot, structureKey).delete();
    }

    /**
     * Opens the tool world of a staged studio and registers the studio once its players can enter. The world is named
     * after the request, so every studio is a world of its own.
     */
    public CompletableFuture<ToolWorld> open(ToolPack pack, JigsawStudioRegistry.StagedActivation staged) {
        Objects.requireNonNull(pack, "Jigsaw Studio pack");
        Objects.requireNonNull(staged, "Jigsaw Studio staged activation");
        JigsawStudioGenerator generator = new JigsawStudioGenerator(pack, staged.request(), staged.session());
        ToolWorldSpec spec = new ToolWorldSpec(
                worldName(staged.request().requestId()),
                generator,
                new JigsawStudioWorldListener(this, staged.request().requestId()));
        return pack.worlds().open(spec).thenApply(world -> {
            register(world, generator);
            return world;
        });
    }

    /** The name of the tool world of the studio opened by {@code requestId}. */
    public static String worldName(UUID requestId) {
        return WORLD_NAME_PREFIX + requestId;
    }

    void register(ToolWorld world, JigsawStudioGenerator generator) {
        UUID worldId = generator.getRequest().requestId();
        ActiveStudio next = new ActiveStudio(
                worldId,
                world,
                generator,
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        synchronized (saveLifecycleLock) {
            studios.put(worldId, next);
        }
        debug("Jigsaw Studio authoring registered: world=%s structure=%s bays=%d",
                world.name(), generator.getSession().structureKey(), generator.getLayout().bays().size());
        replayChunksLoadedBeforeRegistration(next);
        scheduleInitialEvaluation(next);
        scheduleOnlinePlayers(worldId);
    }

    private void replayChunksLoadedBeforeRegistration(ActiveStudio studio) {
        Set<Long> early = earlyChunks.remove(studio.worldId());
        if (early == null) {
            return;
        }
        for (long chunkKey : early) {
            markChunkAvailable(studio, (int) (chunkKey >> 32), (int) chunkKey);
        }
    }

    /** The studio whose world the player stands in; null when they stand elsewhere. */
    private ActiveStudio studioOf(ToolPlayer player) {
        return player.world().flatMap(this::studioIn).orElse(null);
    }

    private Optional<ActiveStudio> studioIn(ToolWorld world) {
        return studios.values().stream().filter(studio -> studio.world() == world).findFirst();
    }

    public void activationCommitted(UUID requestId) {
        if (requestId == null) {
            return;
        }
        ActiveStudio studio = studios.get(requestId);
        if (studio == null) {
            return;
        }
        scheduleInitialEvaluation(studio);
    }

    private void markChunkAvailable(ActiveStudio studio, int chunkX, int chunkZ) {
        long chunkKey = chunkKey(chunkX, chunkZ);
        boolean relevant = false;
        for (JigsawStudioBay bay : studio.generator().getLayout().bays()) {
            BayPopulation population = studio.population(bay);
            if (population.markGenerated(chunkKey)) {
                relevant = true;
            }
        }
        if (relevant) {
            scheduleHydration(studio, studio.world(), chunkX, chunkZ, 0);
        }
    }

    public void unregister(UUID worldId) {
        if (worldId == null) {
            return;
        }
        ActiveStudio active = studios.get(worldId);
        if (active != null) {
            UUID activeRequestId = active.generator().getRequest().requestId();
            finalizeJigsawTileWatches(activeRequestId);
            drainAutosavesBeforeRemoval(active);
            if (requiresLifecycleDrain(active) && !discardingRequest(activeRequestId)) {
                if (unregisterDrainWarnings.add(activeRequestId)) {
                    warn("Jigsaw Studio unregister deferred while request %s finishes its final autosave drain",
                            activeRequestId);
                }
                scheduleUnregisterRetry(worldId, activeRequestId);
                return;
            }
        }
        ActiveStudio removed;
        JigsawStudioRequest request;
        synchronized (saveLifecycleLock) {
            removed = studios.remove(worldId);
            if (removed == null) {
                return;
            }
            request = removed.generator().getRequest();
            savesInProgress.remove(request.requestId());
            graphMutationsInProgress.remove(request.requestId());
            materializationsInProgress.remove(request.requestId());
            exportsInProgress.remove(request.requestId());
            closingRequests.remove(request.requestId());
            discardingRequests.remove(request.requestId());
            reopenRequiredRequests.remove(request.requestId());
        }
        clearAutosaves(request.requestId());
        deferredDuplications.remove(request.requestId());
        clearJigsawTileWatches(request.requestId());
        unregisterRetries.remove(request.requestId());
        unregisterDrainWarnings.remove(request.requestId());
        toolConfirmations.entrySet().removeIf(entry -> entry.getValue().payload().requestId().equals(request.requestId()));
        tripleSneakTracker.clearRequest(request.requestId());
        evaluations.remove(request.requestId());
        previewRenderer.forgetRequest(request.requestId());
        registry.deactivate(request.packKey(), request.requestId());
        clearWorldPlayerContexts(removed.world(), worldId);
        debug("Jigsaw Studio authoring unregistered: world=%s", removed.world().name());
    }

    private void scheduleUnregisterRetry(UUID worldId, UUID requestId) {
        if (!unregisterRetries.add(requestId)) {
            return;
        }
        scheduler.later(AUTOSAVE_RETRY_TICKS, () -> {
            unregisterRetries.remove(requestId);
            ActiveStudio current = studios.get(worldId);
            if (current != null
                    && current.generator().getRequest().requestId().equals(requestId)) {
                unregister(worldId);
            }
        });
    }

    public CloseStart tryBeginClose(UUID requestId, UUID ownerId, boolean discard) {
        if (requestId == null) {
            return CloseStart.NOT_ACTIVE;
        }
        finalizeJigsawTileWatches(requestId);
        synchronized (saveLifecycleLock) {
            JigsawStudioRequest request = registry.getRequest(requestId);
            JigsawStudioSession session = registry.getSession(requestId);
            if (request == null || session == null) {
                return CloseStart.NOT_ACTIVE;
            }
            if (request.ownerId() != null && !request.ownerId().equals(ownerId)) {
                return CloseStart.NOT_OWNER;
            }
            if (savesInProgress.contains(requestId)) {
                return CloseStart.SAVE_IN_PROGRESS;
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (exportsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (materializationsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (session.operationInProgress()) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (hasJigsawTileWatch(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (session.isDirty() && !discard) {
                return CloseStart.DIRTY;
            }
            closingRequests.add(requestId);
            if (discard) {
                discardingRequests.add(requestId);
            } else {
                discardingRequests.remove(requestId);
            }
            return CloseStart.STARTED;
        }
    }

    public String closeProtectionFailure(UUID requestId) {
        if (requestId == null) {
            return null;
        }
        synchronized (saveLifecycleLock) {
            if (closingRequests.contains(requestId) && !savesInProgress.contains(requestId)) {
                return null;
            }
            if (savesInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is saving and cannot be closed or replaced yet.";
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is updating its graph and cannot be closed or replaced yet.";
            }
            if (exportsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is exporting and cannot be closed or replaced yet.";
            }
            if (materializationsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is loading or restoring a variant and cannot be closed yet.";
            }
            JigsawStudioSession session = registry.getSession(requestId);
            if (session != null && session.operationInProgress()) {
                return "The active Jigsaw Studio is loading a variant and cannot be closed or replaced yet.";
            }
            if (hasJigsawTileWatch(requestId)) {
                return "The active Jigsaw Studio is finalizing an open vanilla jigsaw-block editor.";
            }
            if (session != null && session.isDirty()) {
                return "The active Jigsaw Studio is waiting for autosave. Let it finish before closing.";
            }
            return "The active Jigsaw Studio is owner-controlled. Close it with /iris jigsaw close.";
        }
    }

    public CompletableFuture<Void> awaitCloseForReplacement(UUID requestId, UUID ownerId) {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        awaitCloseForReplacement(requestId, ownerId, readiness, 0);
        return readiness;
    }

    private void awaitCloseForReplacement(
            UUID requestId,
            UUID ownerId,
            CompletableFuture<Void> readiness,
            int waitedTicks
    ) {
        if (readiness.isDone()) {
            return;
        }
        CloseStart closeStart = tryBeginClose(requestId, ownerId, false);
        switch (closeStart) {
            case STARTED, NOT_ACTIVE -> readiness.complete(null);
            case NOT_OWNER -> readiness.completeExceptionally(new IllegalStateException(
                    "The active Jigsaw Studio is owned by another player session."));
            case DIRTY, SAVE_IN_PROGRESS, OPERATION_IN_PROGRESS -> {
                if (waitedTicks == 0) {
                    expediteAutosaves(requestId);
                }
                if (waitedTicks >= REPLACEMENT_CLOSE_WAIT_TICKS) {
                    String failure = closeProtectionFailure(requestId);
                    readiness.completeExceptionally(new IllegalStateException(
                            failure == null
                                    ? "The active Jigsaw Studio did not become ready for replacement."
                                    : failure));
                    return;
                }
                scheduler.later(AUTOSAVE_RETRY_TICKS, () -> awaitCloseForReplacement(
                        requestId,
                        ownerId,
                        readiness,
                        waitedTicks + AUTOSAVE_RETRY_TICKS));
            }
        }
    }

    public void cancelClose(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (saveLifecycleLock) {
            closingRequests.remove(requestId);
            discardingRequests.remove(requestId);
        }
    }

    SaveStart tryBeginSave(UUID requestId) {
        Objects.requireNonNull(requestId, "Jigsaw Studio save request ID");
        synchronized (saveLifecycleLock) {
            if (closingRequests.contains(requestId)) {
                return SaveStart.CLOSING;
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return SaveStart.GRAPH_OPERATION;
            }
            if (exportsInProgress.contains(requestId)) {
                return SaveStart.EXPORT_OPERATION;
            }
            if (materializationsInProgress.contains(requestId)) {
                return SaveStart.VARIANT_OPERATION;
            }
            if (!savesInProgress.add(requestId)) {
                return SaveStart.IN_PROGRESS;
            }
            return SaveStart.STARTED;
        }
    }

    void finishSave(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (saveLifecycleLock) {
            savesInProgress.remove(requestId);
        }
    }

    public ExportStart tryBeginExport(UUID requestId, UUID ownerId) {
        Objects.requireNonNull(requestId, "Jigsaw Studio export request ID");
        Objects.requireNonNull(ownerId, "Jigsaw Studio export owner ID");
        finalizeJigsawTileWatches(requestId);
        synchronized (saveLifecycleLock) {
            JigsawStudioRequest request = registry.getRequest(requestId);
            JigsawStudioSession session = registry.getSession(requestId);
            if (request == null || session == null) {
                return ExportStart.NOT_ACTIVE;
            }
            if (request.ownerId() != null && !request.ownerId().equals(ownerId)) {
                return ExportStart.NOT_OWNER;
            }
            if (closingRequests.contains(requestId)) {
                return ExportStart.CLOSING;
            }
            if (savesInProgress.contains(requestId)) {
                return ExportStart.SAVE_IN_PROGRESS;
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (materializationsInProgress.contains(requestId) || session.operationInProgress()) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (hasJigsawTileWatch(requestId)) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (session.isDirty()) {
                return ExportStart.DIRTY;
            }
            if (!exportsInProgress.add(requestId)) {
                return ExportStart.IN_PROGRESS;
            }
            return ExportStart.STARTED;
        }
    }

    public void finishExport(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (saveLifecycleLock) {
            exportsInProgress.remove(requestId);
        }
    }

    public boolean saveSelected(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> saveSelected(player));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            message(player, "Close and reopen Jigsaw Studio before loading variants in the resized layout.");
            return false;
        }
        if (graphMutationInProgress(studio.generator().getRequest().requestId())) {
            message(player, "Wait for the current Jigsaw Studio graph update to finish.");
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        finalizeJigsawTileWatches(requestId);
        if (hasJigsawTileWatch(requestId)) {
            message(player, "Wait for the open vanilla jigsaw-block editor to finish its final snapshot.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String selected = session.selectedBayId().orElse(null);
        if (selected == null) {
            BlockPosition at = player.position();
            JigsawStudioBay underPlayer = session.layout().findAt(at.x(), at.y(), at.z());
            if (underPlayer != null) {
                selected = underPlayer.stableId();
                session.selectBay(selected);
            }
        }
        if (selected == null) {
            message(player, "Stand inside or select a Jigsaw Studio workcell before saving.");
            return false;
        }
        return saveBay(player, selected);
    }

    public boolean saveBay(ToolPlayer player, String bayId) {
        if (player == null || bayId == null || bayId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> saveBay(player, bayId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioBay bay = findBay(studio.generator().getSession().layout(), bayId);
        if (bay == null) {
            message(player, "No Jigsaw Studio bay matches '" + bayId + "'.");
            return false;
        }
        return startSave(studio, studio.world(), bay, player, true) == SaveAttempt.STARTED;
    }

    private SaveAttempt startSave(
            ActiveStudio studio,
            ToolWorld world,
            JigsawStudioBay bay,
            ToolPlayer player,
            boolean report
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        if (reopenRequiredRequests.contains(requestId)) {
            report(player, report, "Close and reopen Jigsaw Studio before saving the resized layout.");
            return SaveAttempt.DEFERRED;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null) {
            report(player, report, "Workcell '" + bay.stableId() + "' has no active variant to save.");
            return SaveAttempt.DEFERRED;
        }
        if (!activeVariant.owned()) {
            report(player, report, "Variant '" + activeVariant.pieceKey()
                    + "' is read-only. Adopt or clone its graph before editing it.");
            return SaveAttempt.DEFERRED;
        }
        BayReadiness readiness = studio.population(bay).readiness();
        if (!readiness.ready()) {
            report(player, report, readinessMessage(bay, readiness));
            return readiness.failure().isEmpty() ? SaveAttempt.RETRY : SaveAttempt.DEFERRED;
        }
        CaptureTarget captureTarget;
        try {
            captureTarget = resolveCaptureTarget(studio, bay, activeVariant);
        } catch (IOException exception) {
            String failure = "Jigsaw Studio cannot capture this workcell: " + failureMessage(exception);
            report(player, report, failure);
            return retainCurrentAutosaveFailure(
                    studio,
                    bay.stableId(),
                    new AutosavePersistentFailure(failure, exception))
                    ? SaveAttempt.PERSISTENT_FAILURE
                    : SaveAttempt.DEFERRED;
        }
        JigsawStudioSession.SaveStart sessionSave = session.beginSave(bay.stableId());
        if (sessionSave.status() != JigsawStudioSession.SaveStatus.STARTED) {
            report(player, report, switch (sessionSave.status()) {
                case SWITCH_IN_PROGRESS -> "This workcell is loading another variant.";
                case SAVE_IN_PROGRESS -> "This workcell is already saving.";
                case NO_ACTIVE_VARIANT -> "This workcell has no active variant.";
                case UNKNOWN_WORKCELL -> "This workcell is no longer active.";
                case STARTED -> "The workcell save could not start.";
            });
            return sessionSave.status() == JigsawStudioSession.SaveStatus.SAVE_IN_PROGRESS
                    || sessionSave.status() == JigsawStudioSession.SaveStatus.SWITCH_IN_PROGRESS
                    ? SaveAttempt.RETRY
                    : SaveAttempt.DEFERRED;
        }
        JigsawStudioSession.SaveIdentity saveIdentity = sessionSave.identity().orElseThrow();
        SaveStart saveStart = tryBeginSave(requestId);
        if (saveStart == SaveStart.CLOSING) {
            session.abortSave(saveIdentity);
            report(player, report, "This Jigsaw Studio is closing and cannot start another save.");
            return SaveAttempt.DEFERRED;
        }
        if (saveStart == SaveStart.IN_PROGRESS) {
            session.abortSave(saveIdentity);
            report(player, report, "A save is already running for this Jigsaw Studio.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.GRAPH_OPERATION) {
            session.abortSave(saveIdentity);
            report(player, report, "Wait for the current Jigsaw Studio graph update to finish.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.EXPORT_OPERATION) {
            session.abortSave(saveIdentity);
            report(player, report, "Wait for the current Jigsaw Studio export to finish.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.VARIANT_OPERATION) {
            session.abortSave(saveIdentity);
            report(player, report, "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return SaveAttempt.RETRY;
        }
        if (report) {
            session.selectBay(bay.stableId());
        }
        refreshWorkcellContext(studio.worldId(), bay.stableId());
        report(player, report, "Capturing variant '" + activeVariant.pieceKey() + "' from "
                + bay.stableId() + "...");
        try {
            boolean scheduled = scheduleCapture(
                    studio,
                    world,
                    bay,
                    captureTarget,
                    player,
                    requestId,
                    saveIdentity,
                    autosaveFailureState(studio, saveIdentity));
            return scheduled ? SaveAttempt.STARTED : SaveAttempt.DEFERRED;
        } catch (Throwable exception) {
            session.abortSave(saveIdentity);
            finishSave(requestId);
            refreshWorkcellContext(studio.worldId(), bay.stableId());
            reportError(exception);
            report(player, report, "Jigsaw Studio could not schedule the workcell capture: "
                    + failureMessage(exception));
            return SaveAttempt.DEFERRED;
        }
    }

    public boolean teleportTo(ToolPlayer player, String bayId) {
        if (player == null || bayId == null || bayId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> teleportTo(player, bayId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            message(player, "Close and reopen Jigsaw Studio before loading variants in the resized layout.");
            return false;
        }
        JigsawStudioBay bay = findBay(studio.generator().getSession().layout(), bayId);
        if (bay == null) {
            message(player, "No Jigsaw Studio bay matches '" + bayId + "'.");
            return false;
        }
        JigsawStudioBounds bounds = bay.bounds();
        studio.generator().getSession().selectBay(bay.stableId());
        player.teleport(
                studio.world(),
                bounds.originX() + bounds.dimensions().width() / 2.0D,
                bounds.maxY() + 2.0D,
                bounds.originZ() + bounds.dimensions().depth() / 2.0D,
                player.yaw(),
                TELEPORT_PITCH_DEGREES
        ).thenRun(() -> JigsawStudioThreads.run(player, () -> reconcilePlayerContext(player, player.position())));
        message(player, "Selected Jigsaw Studio workcell '" + bay.stableId() + "'.");
        ensureVisualizationLoop(player);
        return true;
    }

    @Override
    public boolean teleportToWorkcell(ToolPlayer player, String workcellId) {
        return teleportTo(player, workcellId);
    }

    @Override
    public boolean setConnectorBlocksVisible(ToolPlayer player, String workcellId, boolean visible) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> setConnectorBlocksVisible(player, workcellId, visible));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcellId);
        if (snapshot.connectorsVisible() == visible) {
            message(player, "Connector blocks are already " + (visible ? "visible." : "hidden."));
            return false;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantReload(workcellId);
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            message(player, switch (start.status()) {
                case DIRTY -> "Wait for this workcell to finish autosaving before changing connector visibility.";
                case SAVE_IN_PROGRESS -> "Wait for the current workcell save to finish.";
                case SWITCH_IN_PROGRESS -> "This workcell is already loading another view.";
                case UNKNOWN_WORKCELL -> "The selected workcell no longer exists.";
                case UNKNOWN_VARIANT -> "This workcell has no active variant.";
                case ALREADY_ACTIVE, WRONG_WORKCELL, STARTED ->
                        "Connector visibility could not change: " + start.status() + ".";
            });
            return false;
        }
        JigsawStudioSession.VariantSwitchToken token = start.token().orElseThrow();
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderVariant(
                workcell,
                token.targetVariant());
        if (!rendered.valid()) {
            session.abortVariantSwitch(token);
            message(player, "Connector visibility cannot change: " + rendered.failure());
            return false;
        }
        message(player, (visible ? "Showing" : "Hiding") + " connector blocks in " + workcellId + "...");
        return scheduleMaterialization(new MaterializationWork(
                studio,
                studio.world(),
                player,
                workcell,
                token,
                rendered,
                rendered,
                snapshot.connectorsVisible(),
                visible,
                true));
    }

    @Override
    public boolean resetConnectorBlocks(ToolPlayer player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> resetConnectorBlocks(player, workcellId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(workcellId).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            message(player, "Load an owned variant before resetting its connector blocks.");
            return false;
        }
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderVariant(
                workcell,
                activeVariant);
        String validationFailure = validateMaterialization(rendered, registryOf(studio));
        if (!validationFailure.isEmpty()) {
            message(player, "Connector blocks cannot reset: " + validationFailure);
            return false;
        }
        if (rendered.connectors().isEmpty()) {
            message(player, "The active variant has no saved connectors to reset.");
            return false;
        }
        String reservationFailure = beginConnectorRepair(studio);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        boolean connectorsVisible = session.workcellSnapshot(workcellId).connectorsVisible();
        message(player, "Resetting " + rendered.connectors().size()
                + " connector block(s) from the last saved iteration...");
        return scheduleConnectorRepair(
                player,
                studio,
                workcell,
                rendered,
                connectorsVisible);
    }

    @Override
    public boolean undoAutosave(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> undoAutosave(player));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        Map<String, VariantReloadRequest> activeReloads = new HashMap<>();
        for (JigsawStudioBay workcell : session.layout().bays()) {
            session.activeVariant(workcell.stableId()).ifPresent(variant -> activeReloads.put(
                    variant.pieceKey(),
                    new VariantReloadRequest(
                            workcell.stableId(),
                            studio.generator().renderBay(workcell))));
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        message(player, "Restoring the previous Jigsaw Studio autosave iteration...");
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioHistoryStore.UndoResult result = new JigsawStudioHistoryStore(
                            packRoot,
                            request.structureKey()).undoLatest();
                    if (!result.available()) {
                        return new CommandGraphMutationResult(
                                session.layout(),
                                "",
                                "",
                                "No earlier autosave iteration is available.");
                    }
                    if (!result.successful()) {
                        throw new IOException("Jigsaw Studio undo failed: "
                                + writeFailure(result.writeResult()));
                    }
                    request.reloadSource();
                    JigsawStudioLayout restoredLayout = loadMappedLayout(studio);
                    VariantReloadRequest reload = activeReloads.get(result.pieceKey());
                    Optional<VariantReloadRequest> activeReload = reload == null
                            || restoredLayout.get(reload.workcellId()) == null
                            ? Optional.empty()
                            : Optional.of(reload);
                    String warning = result.warning().isEmpty()
                            ? ""
                            : " History cleanup warning: " + result.warning();
                    return new CommandGraphMutationResult(
                            restoredLayout,
                            "",
                            "",
                            Map.of(),
                            activeReload,
                            "Restored the previous autosave iteration. "
                                    + result.remainingIterations() + " earlier iteration(s) remain."
                                    + warning);
                });
    }

    public boolean setParticles(ToolPlayer player, boolean visible) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> setParticles(player, visible));
        }
        if (studioOf(player) == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (visible) {
            particlesDisabled.remove(player.id());
            ensureVisualizationLoop(player);
        } else {
            particlesDisabled.add(player.id());
        }
        message(player, "Jigsaw Studio particles " + (visible ? "enabled." : "disabled."));
        return true;
    }

    public boolean switchVariant(
            ToolPlayer player,
            String workcellId,
            String targetPieceKey,
            boolean discardDirty
    ) {
        if (player == null || workcellId == null || targetPieceKey == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> switchVariant(
                    player, workcellId, targetPieceKey, discardDirty));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (graphMutationInProgress(studio.generator().getRequest().requestId())) {
            message(player, "Wait for the current Jigsaw Studio graph update to finish.");
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        finalizeJigsawTileWatches(requestId);
        if (hasJigsawTileWatch(requestId)) {
            message(player, "Wait for the open vanilla jigsaw-block editor to finish its final snapshot.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantSwitch(
                workcell.stableId(), targetPieceKey, discardDirty);
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            message(player, switch (start.status()) {
                case DIRTY -> "Wait for the current variant to finish autosaving before switching.";
                case SAVE_IN_PROGRESS -> "Wait for the current variant save to finish.";
                case SWITCH_IN_PROGRESS -> "This workcell is already loading another variant.";
                case ALREADY_ACTIVE -> "Variant '" + targetPieceKey + "' is already active.";
                case WRONG_WORKCELL -> "Variant '" + targetPieceKey + "' belongs to another workcell.";
                case UNKNOWN_VARIANT -> "No variant '" + targetPieceKey + "' exists in this Studio catalog.";
                case UNKNOWN_WORKCELL -> "The selected workcell no longer exists.";
                case STARTED -> "The variant switch could not start.";
            });
            return false;
        }
        return materializeVariant(player, studio, workcell, start, null);
    }

    private void reloadActiveVariant(
            ToolPlayer player,
            ActiveStudio studio,
            VariantReloadRequest reload
    ) {
        if (!isCurrentRequest(studio, studio.generator().getRequest().requestId())) {
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(reload.workcellId());
        if (workcell == null) {
            message(player, "The resized variant was saved, but its workcell is no longer active. Reopen Studio.");
            return;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantReload(workcell.stableId());
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            reopenRequiredRequests.add(studio.generator().getRequest().requestId());
            message(player, "The resized variant was saved, but Studio could not reload it: "
                    + start.status() + ". Close and reopen this project before editing that workcell.");
            return;
        }
        materializeVariant(player, studio, workcell, start, reload.previous());
    }

    private boolean materializeVariant(
            ToolPlayer player,
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioSession.SwitchStart start,
            JigsawStudioGenerator.RenderedBay previousOverride
    ) {
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioSession.VariantSwitchToken token = start.token().orElseThrow();
        String targetPieceKey = token.targetVariant().pieceKey();
        JigsawStudioGenerator.RenderedBay target = studio.generator().renderVariant(
                workcell,
                token.targetVariant());
        if (!target.valid()) {
            session.abortVariantSwitch(token);
            refreshWorkcellContext(studio.worldId(), workcell.stableId());
            message(player, "Variant '" + targetPieceKey + "' cannot load: " + target.failure());
            return false;
        }
        JigsawStudioGenerator.RenderedBay previous = previousOverride == null
                ? token.previousVariant()
                .map(variant -> studio.generator().renderVariant(workcell, variant))
                .orElseGet(() -> JigsawStudioGenerator.RenderedBay.empty(workcell.bounds().dimensions()))
                : previousOverride;
        if (!previous.valid()) {
            session.abortVariantSwitch(token);
            refreshWorkcellContext(studio.worldId(), workcell.stableId());
            message(player, "The current variant cannot be retained for rollback: " + previous.failure());
            return false;
        }
        JigsawStudioVariant previousVariant = token.previousVariant().orElse(null);
        if (previousVariant != null) {
            String rollbackFailure = validateMaterialization(previous, registryOf(studio));
            if (!rollbackFailure.isEmpty()) {
                session.abortVariantSwitch(token);
                refreshWorkcellContext(studio.worldId(), workcell.stableId());
                message(player, "The current variant cannot be retained for rollback: " + rollbackFailure);
                return false;
            }
        }
        session.selectBay(workcell.stableId());
        refreshWorkcellContext(studio.worldId(), workcell.stableId());
        message(player, "Loading variant '" + targetPieceKey + "' into " + workcell.stableId() + "...");
        return scheduleMaterialization(new MaterializationWork(
                studio,
                studio.world(),
                player,
                workcell,
                token,
                previous,
                target,
                session.workcellSnapshot(workcell.stableId()).connectorsVisible(),
                session.workcellSnapshot(workcell.stableId()).connectorsVisible(),
                false));
    }

    public boolean createVariant(
            ToolPlayer player,
            String workcellId,
            boolean duplicateActive
    ) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> createVariant(player, workcellId, duplicateActive));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        if (!canCreateVariants(session.layout())) {
            message(player, "This Jigsaw Studio graph is read-only. "
                    + "Adopt or clone it before creating variants.");
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(workcellId).orElse(null);
        String sourceFailure = variantCreationSourceFailure(activeVariant, duplicateActive);
        if (!sourceFailure.isEmpty()) {
            message(player, sourceFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (duplicateActive && deferDuplicationUntilAutosaved(
                player,
                studio,
                DeferredDuplication.single(
                        request.requestId(),
                        session.sessionId(),
                        player,
                        studio.worldId(),
                        workcell.stableId(),
                        activeVariant.pieceKey()))) {
            return true;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        String archetypeKey = workcell.archetype()
                .map(archetype -> archetype.name().toLowerCase(Locale.ROOT))
                .orElse("spatial");
        String sourcePieceKey = activeVariant.pieceKey();
        message(player, duplicateActive ? "Duplicating the active variant..." : "Creating a new variant...");
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    String pieceKey = JigsawStudioGraphEditor.nextVariantKey(
                            packRoot, request.structureKey(), archetypeKey);
                    if (duplicateActive) {
                        JigsawStudioGraphEditor.duplicatePiece(
                                packRoot,
                                studio.generator().getPack().objects(),
                                request.structureKey(),
                                sourcePieceKey,
                                pieceKey);
                    } else {
                        JigsawStudioGraphEditor.createBlankVariant(
                                packRoot,
                                studio.generator().getPack().objects(),
                                request.structureKey(),
                                sourcePieceKey,
                                pieceKey);
                    }
                    JigsawStudioLayout updatedLayout = loadMappedLayout(studio);
                    String targetWorkcellId = updatedLayout.workcellForVariant(pieceKey)
                            .map(JigsawStudioBay::stableId)
                            .orElseThrow(() -> new IOException(
                                    "Created variant '" + pieceKey + "' has no Studio workcell"));
                    if (updatedLayout.mode() == JigsawStudioMode.SPATIAL_JIGSAW) {
                        return new CommandGraphMutationResult(
                                updatedLayout,
                                "",
                                "",
                                (duplicateActive ? "Duplicated" : "Created") + " variant '" + pieceKey
                                        + "' in " + targetWorkcellId + ".");
                    }
                    return new CommandGraphMutationResult(
                            updatedLayout,
                            targetWorkcellId,
                            pieceKey,
                            (duplicateActive ? "Duplicated" : "Created") + " variant '" + pieceKey + "'.");
                });
    }

    @Override
    public boolean setWorkcellEnabled(ToolPlayer player, String workcellId, boolean enabled) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> setWorkcellEnabled(player, workcellId, enabled));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null || workcell.archetype().isEmpty()) {
            message(player, "Only planar Jigsaw Studio workcells can be enabled or disabled.");
            return false;
        }
        if (workcell.enabled() == enabled) {
            message(player, "Workcell '" + workcellId + "' is already "
                    + (enabled ? "enabled." : "disabled."));
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElseThrow();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioStructureEditor.updateWorkcellEnabled(
                            packRoot,
                            request.structureKey(),
                            archetype,
                            enabled);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Workcell '" + workcellId + "' is now "
                                    + (enabled ? "enabled." : "disabled for assembly and export."));
                });
    }

    @Override
    public boolean updateWorkcellDimensions(
            ToolPlayer player,
            String workcellId,
            JigsawStudioCellDimensions dimensions
    ) {
        if (player == null || workcellId == null || workcellId.isBlank() || dimensions == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateWorkcellDimensions(player, workcellId, dimensions));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        if (workcell.capacity().equals(dimensions)) {
            message(player, "Workcell '" + workcellId + "' already has capacity "
                    + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + ".");
            return false;
        }
        if (workcell.archetype().isPresent()
                && (dimensions.width() < 3 || dimensions.depth() < 3)) {
            message(player, "Planar workcell width and depth must each be at least 3 blocks.");
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElse(null);
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.WorkcellCapacityResult capacityResult = null;
                    if (archetype == null) {
                        JigsawStudioStructureEditor.updateCellSize(
                                packRoot,
                                request.structureKey(),
                                dimensions);
                    } else {
                        capacityResult = JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                                packRoot,
                                request.structureKey(),
                                archetype,
                                dimensions);
                    }
                    request.reloadSource();
                    String resizeSummary = capacityResult == null
                            ? ""
                            : " Verified " + capacityResult.checkedVariants() + " existing variant"
                            + (capacityResult.checkedVariants() == 1 ? "" : "s") + "; no variant object was resized.";
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated '" + workcellId + "' capacity to "
                                    + dimensions.width() + "x" + dimensions.height() + "x"
                                    + dimensions.depth() + "." + resizeSummary
                                    + " The live workcell layout was regenerated.");
                });
    }

    @Override
    public boolean setRequireCaps(ToolPlayer player, boolean requireCaps) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> setRequireCaps(player, requireCaps));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && requireCaps) {
            message(player, "Mandatory caps are Iris-only and cannot be enabled for a vanilla-portable graph.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        if (structure == null) {
            message(player, "The active jigsaw structure could not be loaded.");
            return false;
        }
        if (structure.isRequireCaps() == requireCaps) {
            message(player, "Mandatory caps are already " + (requireCaps ? "enabled." : "disabled."));
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioStructureEditor.updateRequireCaps(
                            packRoot,
                            request.structureKey(),
                            requireCaps);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Mandatory physical caps are now "
                                    + (requireCaps ? "enabled." : "disabled."));
                });
    }

    @Override
    public boolean duplicateActiveFamily(ToolPlayer player, String themeKey) {
        if (player == null || themeKey == null || themeKey.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> duplicateActiveFamily(player, themeKey));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            message(player, "Coherent variant families are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        Map<String, String> sourcePieces = new LinkedHashMap<>();
        for (JigsawStudioBay workcell : session.layout().bays()) {
            if (!workcell.enabled()) {
                continue;
            }
            JigsawStudioVariant active = session.activeVariant(workcell.stableId()).orElse(null);
            if (active == null || !active.owned()) {
                message(player, "Load an owned source variant in every enabled workcell before duplicating a family.");
                return false;
            }
            sourcePieces.put(workcell.stableId(), active.pieceKey());
        }
        if (deferDuplicationUntilAutosaved(
                player,
                studio,
                DeferredDuplication.family(
                        request.requestId(),
                        session.sessionId(),
                        player,
                        studio.worldId(),
                        sourcePieces,
                        themeKey))) {
            return true;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.VariantFamilyCreation creation =
                            JigsawStudioGraphEditor.duplicateActiveFamily(
                                    packRoot,
                                    request.structureKey(),
                                    sourcePieces,
                                    themeKey);
                    if (!creation.writeResult().successful()) {
                        throw new IOException("Variant-family transaction failed with "
                                + creation.writeResult().status());
                    }
                    JigsawStudioLayout updatedLayout = loadMappedLayout(studio);
                    Map<String, String> rebinds = updatedLayout.mode() == JigsawStudioMode.SPATIAL_JIGSAW
                            ? Map.of()
                            : creation.pieceKeysByWorkcell();
                    return new CommandGraphMutationResult(
                            updatedLayout,
                            "",
                            "",
                            rebinds,
                            Optional.empty(),
                            "Duplicated every enabled workcell as coherent family '" + themeKey + "' with "
                                    + creation.pieceKeysByWorkcell().size() + " variant(s).");
                });
    }

    @Override
    public boolean updateThemeSetWeight(ToolPlayer player, String themeKey, int weight) {
        if (player == null || themeKey == null || themeKey.isBlank() || weight < 1) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateThemeSetWeight(player, themeKey, weight));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            message(player, "Coherent theme sets are Iris-only.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        if (structure == null || structure.getThemeSets() == null) {
            message(player, "The active jigsaw structure has no theme sets.");
            return false;
        }
        List<IrisJigsawThemeSet> updated = new ArrayList<>(structure.getThemeSets().size());
        boolean found = false;
        for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
            if (theme != null && themeKey.equals(theme.getKey())) {
                if (theme.getWeight() == weight) {
                    message(player, "Theme '" + themeKey + "' already has weight " + weight + ".");
                    return false;
                }
                updated.add(new IrisJigsawThemeSet(themeKey, weight));
                found = true;
            } else if (theme != null) {
                updated.add(new IrisJigsawThemeSet(theme.getKey(), theme.getWeight()));
            }
        }
        if (!found) {
            message(player, "Theme set '" + themeKey + "' no longer exists.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioStructureEditor.updateThemeSets(
                            packRoot,
                            request.structureKey(),
                            updated);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set theme '" + themeKey + "' weight to " + weight + ".");
                });
    }

    @Override
    public boolean updateVariantThemes(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            List<String> themes
    ) {
        if (player == null || pieceKey == null || themes == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateVariantThemes(
                    player, workcellId, pieceKey, themes));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        List<String> targetThemes = List.copyOf(themes);
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && !targetThemes.isEmpty()) {
            message(player, "Piece themes are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        Set<String> declaredThemes = new HashSet<>();
        if (structure != null && structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
                if (theme != null) {
                    declaredThemes.add(theme.getKey());
                }
            }
        }
        if (!declaredThemes.containsAll(targetThemes)) {
            message(player, "One or more selected theme sets no longer exist.");
            return false;
        }
        if (variant.themes().equals(targetThemes)) {
            message(player, "Variant theme membership is unchanged.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.updatePieceThemes(
                            packRoot,
                            request.structureKey(),
                            variant.pieceKey(),
                            targetThemes);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated theme membership for '" + variant.pieceKey() + "'.");
                });
    }

    @Override
    public boolean updateVariantRules(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            JigsawStudioPieceRules rules
    ) {
        if (player == null || pieceKey == null || rules == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateVariantRules(
                    player, workcellId, pieceKey, rules));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && !DEFAULT_PIECE_RULES.equals(rules)) {
            message(player, "Piece placement rules are Iris-only for a vanilla-portable graph.");
            return false;
        }
        if (variant.rules().equals(rules)) {
            message(player, "Variant piece rules are unchanged.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.updatePieceRules(
                            packRoot,
                            request.structureKey(),
                            variant.pieceKey(),
                            rules);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated placement rules for '" + variant.pieceKey() + "'.");
                });
    }

    public boolean toggleVariantRotatable(ToolPlayer player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> toggleVariantRotatable(player, workcellId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId);
        if (variant == null) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        if (!canToggleVariantRotation(request.compatibilityTarget(), variant)) {
            message(player, "Vanilla-portable variants must remain rotatable.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        boolean rotatable = !variant.rotatable();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.updateRotatable(
                            packRoot,
                            request.structureKey(),
                            variant.pieceKey(),
                            rotatable);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Variant rotation is now " + (rotatable ? "enabled." : "disabled."));
                });
    }

    public boolean expandVariantToCell(ToolPlayer player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> expandVariantToCell(player, workcellId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId);
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (variant == null || workcell == null) {
            return false;
        }
        return resizeVariant(player, workcellId, variant.pieceKey(), workcell.capacity());
    }

    @Override
    public boolean resizeVariant(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank() || dimensions == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> resizeVariant(player, workcellId, pieceKey, dimensions));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' does not belong to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Duplicate it before resizing.");
            return false;
        }
        if (dimensions.width() > workcell.capacity().width()
                || dimensions.height() > workcell.capacity().height()
                || dimensions.depth() > workcell.capacity().depth()) {
            message(player, "Requested variant size " + dimensions.width() + "x" + dimensions.height() + "x"
                    + dimensions.depth() + " exceeds workcell capacity " + workcell.capacity().width() + "x"
                    + workcell.capacity().height() + "x" + workcell.capacity().depth()
                    + ". Increase the workcell capacity first.");
            return false;
        }
        if (variant.mode() == JigsawStudioMode.PLANAR_JIGSAW
                && (dimensions.width() < 3 || dimensions.depth() < 3)) {
            message(player, "Planar variant width and depth must each be at least 3 blocks.");
            return false;
        }
        if (variant.dimensions().filter(dimensions::equals).isPresent()) {
            message(player, "Variant '" + variant.resolvedDisplayName() + "' already uses "
                    + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + ".");
            return false;
        }
        boolean active = session.activeVariant(workcellId)
                .map(activeVariant -> activeVariant.pieceKey().equals(pieceKey))
                .orElse(false);
        JigsawStudioGenerator.RenderedBay previous = active
                ? studio.generator().renderVariant(workcell, variant)
                : null;
        if (previous != null && !previous.valid()) {
            message(player, "The active variant cannot be retained for live resize rollback: " + previous.failure());
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        message(player, "Resizing variant '" + variant.resolvedDisplayName() + "' to "
                + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + "...");
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.VariantResizeResult resize =
                            JigsawStudioGraphEditor.resizePieceObject(
                            packRoot,
                            studio.generator().getPack().objects(),
                            request.structureKey(),
                            variant.pieceKey(),
                            dimensions);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            Map.of(),
                            active ? Optional.of(new VariantReloadRequest(workcellId, previous)) : Optional.empty(),
                            "Resized variant '" + variant.resolvedDisplayName() + "' from "
                                    + resize.previousDimensions().width() + "x"
                                    + resize.previousDimensions().height() + "x"
                                    + resize.previousDimensions().depth() + " to "
                                    + dimensions.width() + "x" + dimensions.height() + "x"
                                    + dimensions.depth() + ". " + resize.relocatedConnectors()
                                    + " connector(s) moved with the new bounds.");
                });
    }

    @Override
    public boolean updateVariantDisplayName(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            String displayName
    ) {
        if (player == null || workcellId == null || pieceKey == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateVariantDisplayName(
                    player, workcellId, pieceKey, displayName));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' does not belong to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Duplicate it before renaming.");
            return false;
        }
        String normalizedName;
        try {
            normalizedName = JigsawStudioGraphEditor.normalizeDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            message(player, exception.getMessage());
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.updatePieceDisplayName(
                            packRoot,
                            request.structureKey(),
                            pieceKey,
                            normalizedName);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            normalizedName.isEmpty()
                                    ? "Reset the variant label to its resource-key fallback."
                                    : "Renamed the variant to '" + normalizedName + "'.");
                });
    }

    @Override
    public boolean updateWorkcellDisplayName(
            ToolPlayer player,
            String workcellId,
            String displayName
    ) {
        if (player == null || workcellId == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> updateWorkcellDisplayName(player, workcellId, displayName));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        String normalizedName;
        try {
            normalizedName = JigsawStudioGraphEditor.normalizeDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            message(player, exception.getMessage());
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElse(null);
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    if (archetype == null) {
                        JigsawStudioStructureEditor.updateSpatialWorkcellDisplayName(
                                packRoot,
                                request.structureKey(),
                                normalizedName);
                    } else {
                        JigsawStudioStructureEditor.updateWorkcellDisplayName(
                                packRoot,
                                request.structureKey(),
                                archetype,
                                normalizedName);
                    }
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            normalizedName.isEmpty()
                                    ? "Reset the workcell label to '" + workcell.canonicalDisplayName() + "'."
                                    : "Renamed the workcell to '" + normalizedName + "'.");
                });
    }

    @Override
    public boolean adjustVariantWeight(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int delta
    ) {
        if (delta == 0) {
            return false;
        }
        if (player == null || workcellId == null || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> adjustVariantWeight(
                    player, workcellId, pieceKey, poolKey, entryIndex, delta));
        }
        ActiveStudio studio = studioOf(player);
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioPoolMembership membership = findMembership(
                variant, poolKey, entryIndex);
        if (membership == null) {
            message(player, "That exact pool membership is no longer present.");
            return false;
        }
        int weight;
        try {
            weight = Math.addExact(membership.weight(), delta);
        } catch (ArithmeticException exception) {
            message(player, "The requested variant weight is outside the supported integer range.");
            return false;
        }
        if (weight < 1) {
            message(player, "Variant weights cannot be lower than 1; unlink the membership instead.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioPoolEditor.updateWeightAtIndex(
                            packRoot,
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey(),
                            weight);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set '" + variant.pieceKey() + "' weight to " + weight
                                    + " in pool '" + membership.poolKey() + "'.");
                });
    }

    @Override
    public boolean adjustVariantChance(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int deltaPercentagePoints
    ) {
        if (deltaPercentagePoints == 0 || player == null || workcellId == null
                || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> adjustVariantChance(
                    player,
                    workcellId,
                    pieceKey,
                    poolKey,
                    entryIndex,
                    deltaPercentagePoints));
        }
        ActiveStudio studio = studioOf(player);
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioPoolMembership membership = findMembership(variant, poolKey, entryIndex);
        if (membership == null) {
            message(player, "That exact pool membership is no longer present.");
            return false;
        }
        double chance = membership.chance() + deltaPercentagePoints / 100.0D;
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            message(player, "Variant chance must stay between 0% and 100%.");
            return false;
        }
        chance = Math.round(chance * 1_000_000.0D) / 1_000_000.0D;
        JigsawStudioRequest request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && chance != 1.0D) {
            message(player, "Chance gates are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        double targetChance = chance;
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioPoolEditor.updateChanceAtIndex(
                            packRoot,
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey(),
                            targetChance);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set '" + variant.pieceKey() + "' chance to "
                                    + Math.round(targetChance * 100.0D) + "% in pool '"
                                    + membership.poolKey() + "'.");
                });
    }

    @Override
    public boolean deleteVariant(ToolPlayer player, String workcellId, String pieceKey) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> deleteVariant(player, workcellId, pieceKey));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' no longer belongs to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Adopt or clone it before deletion.");
            return false;
        }
        String activePieceKey = session.activeVariant(workcellId)
                .map(JigsawStudioVariant::pieceKey)
                .orElse("");
        if (session.layout().mode() == JigsawStudioMode.PLANAR_JIGSAW
                && activePieceKey.equals(pieceKey)) {
            message(player, "Load another variant in this workcell before deleting '" + pieceKey + "'.");
            return false;
        }
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioGraphEditor.PieceDeletionResult deletion =
                            JigsawStudioGraphEditor.deletePieceVariant(
                                    packRoot,
                                    request.structureKey(),
                                    pieceKey);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Deleted variant '" + pieceKey + "', removed "
                                    + deletion.removedPoolMemberships() + " pool membership(s), and removed "
                                    + deletion.removedObjectResources() + " unshared object(s).");
                });
    }

    public boolean deleteProject(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> deleteProject(player));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        CloseStart closeStart = tryBeginClose(request.requestId(), player.id(), false);
        if (closeStart != CloseStart.STARTED) {
            message(player, switch (closeStart) {
                case DIRTY -> "Wait for autosave to finish before deleting this project.";
                case SAVE_IN_PROGRESS -> "Wait for the active autosave to finish before deleting this project.";
                case OPERATION_IN_PROGRESS -> "Wait for the active Jigsaw Studio operation to finish.";
                case NOT_OWNER -> "This Jigsaw Studio is owned by another player session.";
                case NOT_ACTIVE -> "This Jigsaw Studio session is no longer active.";
                case STARTED -> "Project deletion could not start.";
            });
            return false;
        }
        message(player, "Inspecting owned resources and reverse references before project deletion...");
        scheduler.async(() -> inspectProjectDeletion(player, studio, request));
        return true;
    }

    private void inspectProjectDeletion(
            ToolPlayer player,
            ActiveStudio studio,
            JigsawStudioRequest request
    ) {
        JigsawStudioProjectDeletionService.DeletionPlan plan;
        try {
            plan = studio.generator().getPack().edit(packRoot ->
                    JigsawStudioProjectDeletionService.inspect(packRoot, request.structureKey()));
        } catch (Throwable exception) {
            cancelClose(request.requestId());
            reportError(exception);
            message(player, "Project deletion inspection failed: " + failureMessage(exception));
            return;
        }
        if (!plan.deletable()) {
            cancelClose(request.requestId());
            message(player, "Project deletion is blocked by " + plan.blockers().size()
                    + " external reference(s). The first is "
                    + plan.blockers().getFirst().ownerPath() + " at "
                    + plan.blockers().getFirst().location() + ".");
            return;
        }
        scheduler.onPlayerLater(
                player,
                () -> closeAndDeleteProject(player, studio, request, plan),
                0,
                () -> {
                    cancelClose(request.requestId());
                    message(player, "Project deletion stopped because the owner session ended.");
                });
    }

    private void closeAndDeleteProject(
            ToolPlayer player,
            ActiveStudio studio,
            JigsawStudioRequest request,
            JigsawStudioProjectDeletionService.DeletionPlan plan
    ) {
        if (!isCurrentRequest(studio, request.requestId())) {
            cancelClose(request.requestId());
            message(player, "Project deletion stopped because the Jigsaw Studio session changed.");
            return;
        }
        studio.world().close().whenComplete((ignored, failure) -> {
            if (failure != null) {
                cancelClose(request.requestId());
                reportError(failure);
                message(player, "Project deletion stopped because Studio could not close: "
                        + failureMessage(failure));
                return;
            }
            scheduler.async(() -> applyProjectDeletion(player, studio.generator().getPack(), request, plan));
        });
    }

    private void applyProjectDeletion(
            ToolPlayer player,
            ToolPack pack,
            JigsawStudioRequest request,
            JigsawStudioProjectDeletionService.DeletionPlan plan
    ) {
        try {
            JigsawStudioProjectDeletionService.ProjectDeletionResult result = pack.edit(packRoot -> {
                JigsawStudioProjectDeletionService.ProjectDeletionResult deleted =
                        JigsawStudioProjectDeletionService.delete(plan);
                try {
                    clearAutosaveHistory(packRoot, request.structureKey());
                } catch (IOException historyFailure) {
                    reportError(historyFailure);
                    message(player, "The project graph was deleted, but its autosave history could not be removed: "
                            + failureMessage(historyFailure));
                }
                return deleted;
            });
            message(player, "Deleted Jigsaw project '" + request.structureKey() + "' and "
                    + result.removedResourceCount() + " owned resource(s).");
            debug("Jigsaw Studio project deleted: structure=%s resources=%d",
                    request.structureKey(), result.removedResourceCount());
        } catch (Throwable exception) {
            reportError(exception);
            message(player, "Studio closed, but the project was not deleted: "
                    + failureMessage(exception) + ". Its files remain recoverable on disk.");
        }
    }

    @Override
    public boolean unlinkVariantMembership(
            ToolPlayer player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex
    ) {
        if (player == null || workcellId == null || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> unlinkVariantMembership(
                    player, workcellId, pieceKey, poolKey, entryIndex));
        }
        ActiveStudio studio = studioOf(player);
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioPoolMembership membership = findMembership(variant, poolKey, entryIndex);
        if (membership == null) {
            message(player, "That exact pool membership is no longer present.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        return scheduleGraphMutation(
                player,
                studio,
                packRoot -> {
                    JigsawStudioPoolEditor.removeEntry(
                            packRoot,
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey());
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Unlinked '" + variant.pieceKey() + "' from pool '"
                                    + membership.poolKey() + "'.");
                });
    }

    public boolean previewStudio(ToolPlayer player, long seed) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> previewStudio(player, seed));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        BlockPosition playerAt = player.position();
        BlockPosition origin = new BlockPosition(playerAt.x(), playerAt.y(), playerAt.z());
        JigsawStudioRequest request = studio.generator().getRequest();
        ToolPack pack = studio.generator().getPack();
        scheduler.async(() -> {
            try {
                IrisStructure structure = StructureResources.structure(
                        pack.resources(), request.structureKey());
                if (structure == null) {
                    throw new IOException("The active structure resource no longer exists");
                }
                StructureAssembler assembler = StructureAssembler.forPack(
                        request.source().resources(), request.structureKey(), structure, origin, pack.log());
                StructureAssemblyResult assembly = assembler.assemble(new RNG(seed));
                if (!assembly.hasOutput()) {
                    message(player, "The active graph assembled zero preview pieces.");
                    return;
                }
                List<PlacedStructurePiece> pieces = assembly.pieces();
                JigsawStudioThreads.run(player, () -> {
                    if (isCurrentRequest(studio, request.requestId())) {
                        showAssemblyPreview(player, pieces);
                        message(player, "Showing a " + pieces.size()
                                + "-piece particle preview for 10 seconds.");
                    }
                });
            } catch (Throwable exception) {
                reportError(exception);
                message(player, "Preview assembly failed: " + failureMessage(exception));
            }
        });
        return true;
    }

    public Optional<JigsawStudioGraphEvaluation> evaluation(ToolPlayer player) {
        if (player == null || !JigsawStudioThreads.owns(player)) {
            return Optional.empty();
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !ownerMatches(player, studio)) {
            return Optional.empty();
        }
        return Optional.ofNullable(evaluations.get(studio.generator().getRequest().requestId()));
    }

    public boolean goToPreview(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> goToPreview(player));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        JigsawStudioPreviewRenderer.PreviewBounds bounds = previewRenderer.bounds(requestId);
        if (bounds == null || bounds.isEmpty()) {
            JigsawStudioGraphEvaluation evaluation = evaluations.get(requestId);
            message(player, evaluation == null
                    ? "The seed-1337 preview is still being evaluated."
                    : "The seed-1337 preview has no generated blocks: " + evaluation.detail());
            return false;
        }
        player.teleport(
                studio.world(),
                bounds.centerX() + 0.5D,
                bounds.maximumY() + 2.0D,
                bounds.centerZ() + 0.5D,
                player.yaw(),
                player.pitch()
        ).thenRun(() -> message(player, "Teleported to the live seed-1337 preview."));
        return true;
    }

    private void scheduleInitialEvaluation(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        UUID requestId = request.requestId();
        if (!claimInitialEvaluation(
                studio.evaluationGeneration(),
                isCurrentRequest(studio, requestId))) {
            return;
        }
        publishEvaluation(studio, requestId, 1L);
    }

    static boolean claimInitialEvaluation(AtomicLong generation, boolean currentRequest) {
        return currentRequest
                && Objects.requireNonNull(generation, "Jigsaw Studio evaluation generation")
                .compareAndSet(0L, 1L);
    }

    private void scheduleEvaluation(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        UUID requestId = request.requestId();
        if (!isCurrentRequest(studio, requestId)) {
            return;
        }
        publishEvaluation(studio, requestId, studio.evaluationGeneration().incrementAndGet());
    }

    private void publishEvaluation(ActiveStudio studio, UUID requestId, long generation) {
        JigsawStudioGraphEvaluation previous = evaluations.get(requestId);
        JigsawStudioPreviewRenderer.PreviewBounds previousBounds = previous == null
                ? JigsawStudioPreviewRenderer.PreviewBounds.empty()
                : previous.previewBounds();
        evaluations.put(requestId, new JigsawStudioGraphEvaluation(
                requestId,
                generation,
                PREVIEW_SEED,
                JigsawStudioEvaluationState.PENDING,
                previous == null ? "" : previous.selectedTheme(),
                previous == null ? 0 : previous.pieceCount(),
                "Compiling the committed graph and assembling seed 1337",
                previousBounds));
        refreshAllWorkcellContexts(studio);
        scheduler.async(() -> evaluateGraph(studio, requestId, generation));
    }

    private void evaluateGraph(ActiveStudio studio, UUID requestId, long generation) {
        EvaluationComputation computation;
        try {
            JigsawStudioRequest request = studio.generator().getRequest();
            IrisStructure structure = StructureResources.structure(
                    studio.generator().getPack().resources(),
                    request.structureKey());
            if (structure == null) {
                throw new IOException("The active structure resource no longer exists");
            }
            StructureGraphCompilation compilation = StructureGraphCompiler.compile(
                    request.structureKey(),
                    structure,
                    StructureGraphResolver.forPack(request.source().resources()));
            StructureGraphDiagnostic firstError = firstDiagnostic(
                    compilation,
                    StructureGraphDiagnostic.Severity.ERROR);
            if (firstError != null) {
                computation = invalidEvaluation(
                        requestId,
                        generation,
                        firstError.code() + ": " + firstError.message());
            } else {
                BlockPosition origin = previewOrigin(studio.generator().getLayout(), structure);
                StructureAssembler assembler = StructureAssembler.forCompilation(compilation, origin);
                StructureAssemblyResult assembly = assembler.assemble(new RNG(PREVIEW_SEED));
                computation = evaluationForAssembly(
                        requestId,
                        generation,
                        compilation,
                        assembly,
                        studio.generator().getLayout().mode(),
                        studio.generator().getPack().resources());
            }
        } catch (Throwable exception) {
            reportError(exception);
            computation = invalidEvaluation(requestId, generation, failureMessage(exception));
        }
        completeEvaluation(studio, computation);
    }

    private EvaluationComputation evaluationForAssembly(
            UUID requestId,
            long generation,
            StructureGraphCompilation compilation,
            StructureAssemblyResult assembly,
            JigsawStudioMode mode,
            PackResources pack
    ) throws IOException {
        if (assembly.status().isFailure()) {
            return invalidEvaluation(
                    requestId,
                    generation,
                    assembly.status().name().toLowerCase(Locale.ROOT) + ": " + assembly.detail());
        }
        if (!assembly.hasOutput()) {
            return new EvaluationComputation(
                    new JigsawStudioGraphEvaluation(
                            requestId,
                            generation,
                            PREVIEW_SEED,
                            JigsawStudioEvaluationState.WARNING,
                            assembly.selectedTheme(),
                            0,
                            assembly.detail().isEmpty()
                                    ? "Seed 1337 intentionally generated no structure"
                                    : assembly.detail(),
                            JigsawStudioPreviewRenderer.PreviewBounds.empty()),
                    JigsawStudioPreviewRenderer.PreviewPlan.empty());
        }
        List<PlacedStructurePiece> aligned = alignPreviewPieces(assembly.pieces(), mode);
        JigsawStudioPreviewRenderer.PreviewPlan plan = JigsawStudioPreviewRenderer.plan(aligned, pack);
        StructureGraphDiagnostic firstWarning = firstDiagnostic(
                compilation,
                StructureGraphDiagnostic.Severity.WARNING);
        JigsawStudioEvaluationState state = firstWarning == null
                ? JigsawStudioEvaluationState.VALID
                : JigsawStudioEvaluationState.WARNING;
        String detail = firstWarning == null
                ? "Seed 1337 assembled " + aligned.size() + " piece(s)"
                : firstWarning.code() + ": " + firstWarning.message();
        return new EvaluationComputation(
                new JigsawStudioGraphEvaluation(
                        requestId,
                        generation,
                        PREVIEW_SEED,
                        state,
                        assembly.selectedTheme(),
                        aligned.size(),
                        detail,
                        plan.bounds()),
                plan);
    }

    private void completeEvaluation(ActiveStudio studio, EvaluationComputation computation) {
        JigsawStudioGraphEvaluation evaluated = computation.evaluation();
        UUID requestId = evaluated.requestId();
        if (!isCurrentEvaluation(studio, requestId, evaluated.generation())) {
            return;
        }
        previewRenderer.render(
                studio.world(),
                requestId,
                evaluated.generation(),
                computation.plan(),
                result -> {
                    if (!isCurrentEvaluation(studio, requestId, evaluated.generation())) {
                        return;
                    }
                    JigsawStudioGraphEvaluation completed = result.successful()
                            ? evaluated
                            : new JigsawStudioGraphEvaluation(
                            requestId,
                            evaluated.generation(),
                            PREVIEW_SEED,
                            JigsawStudioEvaluationState.WARNING,
                            evaluated.selectedTheme(),
                            evaluated.pieceCount(),
                            evaluated.detail() + "; " + result.failure(),
                            evaluated.previewBounds());
                    evaluations.put(requestId, completed);
                    refreshAllWorkcellContexts(studio);
                });
    }

    private boolean isCurrentEvaluation(ActiveStudio studio, UUID requestId, long generation) {
        if (!isCurrentRequest(studio, requestId)
                || studio.evaluationGeneration().get() != generation) {
            return false;
        }
        JigsawStudioGraphEvaluation current = evaluations.get(requestId);
        return current != null && current.generation() == generation;
    }

    private static EvaluationComputation invalidEvaluation(
            UUID requestId,
            long generation,
            String detail
    ) {
        return new EvaluationComputation(
                new JigsawStudioGraphEvaluation(
                        requestId,
                        generation,
                        PREVIEW_SEED,
                        JigsawStudioEvaluationState.INVALID,
                        "",
                        0,
                        detail,
                        JigsawStudioPreviewRenderer.PreviewBounds.empty()),
                JigsawStudioPreviewRenderer.PreviewPlan.empty());
    }

    private static StructureGraphDiagnostic firstDiagnostic(
            StructureGraphCompilation compilation,
            StructureGraphDiagnostic.Severity severity
    ) {
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            if (diagnostic.severity() == severity) {
                return diagnostic;
            }
        }
        return null;
    }

    private static BlockPosition previewOrigin(JigsawStudioLayout layout, IrisStructure structure) {
        int radius = Math.max(1, structure.getMaxSizeChunks()) * 16;
        return new BlockPosition(
                -radius - 32,
                0,
                Math.max(16, layout.extentZ() / 2));
    }

    static List<PlacedStructurePiece> alignPreviewPieces(
            List<PlacedStructurePiece> pieces,
            JigsawStudioMode mode
    ) {
        int minimumY = Integer.MAX_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minimumY = Math.min(minimumY, piece.getMinY());
        }
        int baseY = mode == JigsawStudioMode.SPATIAL_JIGSAW
                ? SPATIAL_PREVIEW_BASE_Y
                : JigsawStudioLayout.FLOOR_Y + 1;
        int shiftY = baseY - minimumY;
        List<PlacedStructurePiece> aligned = new ArrayList<>(pieces.size());
        for (PlacedStructurePiece piece : pieces) {
            aligned.add(new PlacedStructurePiece(
                    piece.getPiece(),
                    piece.getObjectKey(),
                    piece.getObject(),
                    piece.getX(),
                    piece.getY() + shiftY,
                    piece.getZ(),
                    piece.getRotation(),
                    piece.getMinX(),
                    piece.getMinY() + shiftY,
                    piece.getMinZ(),
                    piece.getMaxX(),
                    piece.getMaxY() + shiftY,
                    piece.getMaxZ()));
        }
        return List.copyOf(aligned);
    }

    private void refreshAllWorkcellContexts(ActiveStudio studio) {
        scheduleOnlinePlayers(studio.worldId());
    }

    private static IrisStructure loadStudioStructure(ActiveStudio studio) {
        JigsawStudioRequest request = studio.generator().getRequest();
        return StructureResources.structure(studio.generator().getPack().resources(), request.structureKey());
    }

    private static boolean canResizeVariantToCapacity(
            JigsawStudioBay workcell,
            JigsawStudioVariant variant,
            boolean active
    ) {
        if (!active || !variant.owned()) {
            return false;
        }
        JigsawStudioCellDimensions canonical = variant.dimensions().orElse(null);
        if (canonical == null) {
            return false;
        }
        JigsawStudioCellDimensions target = workcell.capacity();
        return canonical.width() <= target.width()
                && canonical.height() <= target.height()
                && canonical.depth() <= target.depth()
                && (canonical.width() < target.width()
                || canonical.height() < target.height()
                || canonical.depth() < target.depth());
    }

    public Optional<JigsawStudioMenuState> menuState(ToolPlayer player) {
        if (player == null || !JigsawStudioThreads.owns(player)) {
            return Optional.empty();
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            return Optional.empty();
        }
        if (!ownerMatches(player, studio)) {
            return Optional.empty();
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioLayout layout = session.layout();
        IrisStructure structure = loadStudioStructure(studio);
        List<JigsawStudioMenuState.ThemeSet> themeSets = new ArrayList<>();
        if (structure != null && structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet themeSet : structure.getThemeSets()) {
                if (themeSet != null) {
                    themeSets.add(new JigsawStudioMenuState.ThemeSet(
                            themeSet.getKey(),
                            themeSet.getWeight()));
                }
            }
        }
        List<JigsawStudioMenuState.Workcell> workcells = new ArrayList<>(layout.bays().size());
        for (JigsawStudioBay workcell : layout.bays()) {
            JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcell.stableId());
            List<JigsawStudioMenuState.Variant> variants = new ArrayList<>();
            for (JigsawStudioVariant variant : layout.variants(workcell)) {
                boolean active = variant.pieceKey().equals(snapshot.activeVariantKey());
                List<JigsawStudioMenuState.Membership> memberships = new ArrayList<>(
                        variant.memberships().size());
                for (JigsawStudioPoolMembership membership : variant.memberships()) {
                    memberships.add(new JigsawStudioMenuState.Membership(
                            membership.poolKey(),
                            membership.entryIndex(),
                            membership.weight(),
                            membership.chance()));
                }
                variants.add(new JigsawStudioMenuState.Variant(
                        variant.pieceKey(),
                        variant.resolvedDisplayName(),
                        variant.dimensions(),
                        active,
                        variant.owned(),
                        variant.rotatable(),
                        canToggleVariantRotation(request.compatibilityTarget(), variant),
                        canResizeVariantToCapacity(workcell, variant, active),
                        variant.themes(),
                        variant.rules(),
                        memberships));
            }
            workcells.add(new JigsawStudioMenuState.Workcell(
                    workcell.stableId(),
                    workcell.canonicalDisplayName(),
                    workcell.displayName(),
                    workcell.capacity(),
                    workcell.enabled(),
                    snapshot.activeVariantKey(),
                    snapshot.dirty(),
                    snapshot.saveInProgress(),
                    snapshot.switchInProgress(),
                    snapshot.connectorsVisible(),
                    variants));
        }

        return Optional.of(new JigsawStudioMenuState(
                studio.worldId(),
                request.requestId(),
                request.structureKey(),
                layout.mode(),
                request.compatibilityTarget(),
                structure != null && structure.isRequireCaps(),
                themeSets,
                session.selectedBayId().orElse(""),
                Optional.ofNullable(evaluations.get(request.requestId()))
                        .map(JigsawStudioMenuState.Evaluation::from)
                        .orElseGet(JigsawStudioMenuState.Evaluation::pending),
                workcells));
    }

    public boolean selectWorkcell(ToolPlayer player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> selectWorkcell(player, workcellId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        if (!session.selectBay(workcellId)) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        ensureVisualizationLoop(player);
        return true;
    }

    public boolean openControlMenu(ToolPlayer player) {
        if (player != null && JigsawStudioThreads.owns(player)) {
            finalizeJigsawTileWatchesForPlayer(player.id());
            ActiveStudio studio = studioOf(player);
            if (studio != null
                    && reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
                message(player, "Close and reopen Jigsaw Studio to apply the resized workcell layout.");
                return false;
            }
        }
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null && activeMenuController.open(player);
    }

    public boolean giveTool(ToolPlayer player, JigsawStudioToolPayload payload) {
        if (player == null || payload == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> giveTool(player, payload));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null
                || !studio.generator().getRequest().requestId().equals(payload.requestId())
                || !authorizeOwner(player, studio)) {
            message(player, "This tool belongs to a different or closed Jigsaw Studio session.");
            return false;
        }
        if (!player.give(toolCodec.create(payload))) {
            message(player, "Your inventory is full; no Jigsaw Studio tool was added.");
            return false;
        }
        message(player, "Added " + payload.action().displayName() + " tool.");
        return true;
    }

    public boolean flushAutosave(ToolPlayer player, String workcellId) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> flushAutosave(player, workcellId));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String targetId = workcellId == null || workcellId.isBlank()
                ? session.selectedBayId().orElse("")
                : workcellId;
        JigsawStudioBay bay = session.layout().get(targetId);
        if (bay == null) {
            message(player, "Select a Jigsaw Studio workcell before saving now.");
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        finalizeJigsawTileWatches(requestId);
        if (hasJigsawTileWatch(requestId)) {
            message(player, "Wait for the open vanilla jigsaw-block editor to finish its final snapshot.");
            return false;
        }
        SaveAttempt attempt = startSave(studio, studio.world(), bay, player, true);
        if (attempt == SaveAttempt.STARTED) {
            return true;
        }
        if (attempt == SaveAttempt.PERSISTENT_FAILURE) {
            return false;
        }
        AutosaveTicket ticket = autosaves.get(new AutosaveKey(requestId, bay.stableId()));
        if (ticket != null) {
            expediteAutosave(
                    ticket,
                    attempt == SaveAttempt.RETRY
                            ? AUTOSAVE_RETRY_TICKS
                            : AUTOSAVE_DEBOUNCE_TICKS);
        }
        return false;
    }

    public void closeMenu(ToolPlayer player) {
        JigsawStudioMenuController activeMenuController = menuController;
        if (activeMenuController != null) {
            activeMenuController.close(player);
        }
    }

    private boolean useTool(
            ToolPlayer player,
            JigsawStudioToolPayload payload,
            ToolItem tool,
            boolean resetLabel
    ) {
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> useTool(player, payload, tool, resetLabel));
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null
                || !studio.generator().getRequest().requestId().equals(payload.requestId())
                || !authorizeOwner(player, studio)) {
            message(player, "This Jigsaw Studio tool is stale or belongs to another session.");
            return false;
        }
        if (payload.action().destructive() && !confirmTool(player, payload)) {
            message(player, "Right-click the same tool again within 10 seconds to confirm "
                    + payload.action().displayName() + ".");
            return true;
        }
        return switch (payload.action()) {
            case OPEN_MENU -> openControlMenu(player);
            case SELECT_WORKCELL -> selectWorkcell(player, payload.workcellId());
            case LOAD_VARIANT -> switchVariant(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    false);
            case CREATE_VARIANT -> createVariant(player, payload.workcellId(), false);
            case DUPLICATE_VARIANT -> createVariant(player, payload.workcellId(), true);
            case DUPLICATE_FAMILY -> duplicateFamilyFromTool(player);
            case PREVIEW_GRAPH -> goToPreview(player);
            case FLUSH_AUTOSAVE -> flushAutosave(player, payload.workcellId());
            case TOGGLE_ROTATION -> toggleVariantRotatable(player, payload.workcellId());
            case EXPAND_TO_CELL -> expandVariantToCell(player, payload.workcellId());
            case RESIZE_VARIANT -> openVariantSizeSettingsFromTool(player, payload);
            case RENAME_VARIANT -> renameVariantFromTool(player, payload, tool, resetLabel);
            case ADJUST_VARIANT_WEIGHT -> adjustVariantWeight(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex(),
                    payload.amount());
            case ADJUST_VARIANT_CHANCE -> adjustVariantChance(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex(),
                    payload.amount());
            case UNLINK_MEMBERSHIP -> unlinkVariantMembership(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex());
            case TOGGLE_WORKCELL -> toggleWorkcellFromTool(player, payload.workcellId());
            case RESIZE_WORKCELL -> openWorkcellSettingsFromTool(player, payload.workcellId());
            case RENAME_WORKCELL -> renameWorkcellFromTool(player, payload, tool, resetLabel);
            case SET_THEME -> useThemeTool(player, payload);
            case SET_PIECE_RULES -> openVariantSettingsFromTool(player, payload);
            case DELETE_VARIANT -> deleteVariant(player, payload.workcellId(), payload.pieceKey());
            case DELETE_PROJECT -> deleteProject(player);
            case TOGGLE_REQUIRE_CAPS -> toggleRequireCapsFromTool(player);
        };
    }

    private boolean toggleWorkcellFromTool(ToolPlayer player, String workcellId) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        JigsawStudioMenuState.Workcell workcell = state
                .map(menu -> menu.workcell(workcellId))
                .orElse(null);
        if (workcell == null) {
            message(player, "This bound workcell no longer exists.");
            return false;
        }
        return setWorkcellEnabled(player, workcellId, !workcell.enabled());
    }

    private boolean openWorkcellSettingsFromTool(ToolPlayer player, String workcellId) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openWorkcellSettings(player, workcellId);
    }

    private boolean openVariantSettingsFromTool(ToolPlayer player, JigsawStudioToolPayload payload) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openVariantSettings(
                player,
                payload.workcellId(),
                payload.pieceKey());
    }

    private boolean openVariantSizeSettingsFromTool(ToolPlayer player, JigsawStudioToolPayload payload) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openVariantSizeSettings(
                player,
                payload.workcellId(),
                payload.pieceKey());
    }

    private boolean renameVariantFromTool(
            ToolPlayer player,
            JigsawStudioToolPayload payload,
            ToolItem tool,
            boolean resetLabel
    ) {
        Optional<String> displayName = toolDisplayName(player, payload, tool, resetLabel);
        return displayName.isPresent() && updateVariantDisplayName(
                player,
                payload.workcellId(),
                payload.pieceKey(),
                displayName.get());
    }

    private boolean renameWorkcellFromTool(
            ToolPlayer player,
            JigsawStudioToolPayload payload,
            ToolItem tool,
            boolean resetLabel
    ) {
        Optional<String> displayName = toolDisplayName(player, payload, tool, resetLabel);
        return displayName.isPresent() && updateWorkcellDisplayName(
                player,
                payload.workcellId(),
                displayName.get());
    }

    private static Optional<String> toolDisplayName(
            ToolPlayer player,
            JigsawStudioToolPayload payload,
            ToolItem tool,
            boolean resetLabel
    ) {
        if (resetLabel) {
            return Optional.of("");
        }
        String normalized = tool == null ? "" : tool.name().plainText().trim();
        String defaultName = JigsawStudioToolCodec.NAME_PREFIX + payload.action().displayName();
        if (normalized.isEmpty() || normalized.equals(defaultName)) {
            message(player, "Rename this bound stick in an anvil to the desired label, then right-click it. "
                    + "Sneak-right-click resets the label.");
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private boolean useThemeTool(ToolPlayer player, JigsawStudioToolPayload payload) {
        if (!payload.pieceKey().isBlank()) {
            return openVariantSettingsFromTool(player, payload);
        }
        Optional<JigsawStudioMenuState> state = menuState(player);
        if (state.isEmpty()) {
            return false;
        }
        return duplicateActiveFamily(
                player,
                JigsawStudioMenuController.nextThemeSetKey(state.get().themeSets()));
    }

    private boolean duplicateFamilyFromTool(ToolPlayer player) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        return state.isPresent() && duplicateActiveFamily(
                player,
                JigsawStudioMenuController.nextThemeSetKey(state.get().themeSets()));
    }

    private boolean toggleRequireCapsFromTool(ToolPlayer player) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        return state.isPresent() && setRequireCaps(player, !state.get().requireCaps());
    }

    private boolean confirmTool(ToolPlayer player, JigsawStudioToolPayload payload) {
        long now = System.nanoTime();
        ToolConfirmation previous = toolConfirmations.get(player.id());
        if (previous != null
                && previous.expiresAtNanos() >= now
                && previous.payload().equals(payload)) {
            toolConfirmations.remove(player.id(), previous);
            return true;
        }
        toolConfirmations.put(
                player.id(),
                new ToolConfirmation(payload, now + TOOL_CONFIRM_NANOS));
        return false;
    }

    private JigsawStudioVariant activeOwnedVariant(
            ToolPlayer player,
            ActiveStudio studio,
            String workcellId
    ) {
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return null;
        }
        JigsawStudioSession session = studio.generator().getSession();
        if (session.layout().get(workcellId) == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return null;
        }
        JigsawStudioVariant variant = session.activeVariant(workcellId).orElse(null);
        if (variant == null) {
            message(player, "This workcell has no active variant.");
            return null;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + variant.pieceKey()
                    + "' is read-only. Adopt or clone its graph before editing it.");
            return null;
        }
        return variant;
    }

    private JigsawStudioVariant activeOwnedVariant(
            ToolPlayer player,
            ActiveStudio studio,
            String workcellId,
            String expectedPieceKey
    ) {
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId);
        if (variant == null) {
            return null;
        }
        if (!variant.pieceKey().equals(expectedPieceKey)) {
            message(player, "The bound variant changed. Open the control menu or request a fresh tool.");
            return null;
        }
        return variant;
    }

    private static JigsawStudioPoolMembership findMembership(
            JigsawStudioVariant variant,
            String poolKey,
            int entryIndex
    ) {
        for (JigsawStudioPoolMembership membership : variant.memberships()) {
            if (membership.poolKey().equals(poolKey) && membership.entryIndex() == entryIndex) {
                return membership;
            }
        }
        return null;
    }

    private boolean deferDuplicationUntilAutosaved(
            ToolPlayer player,
            ActiveStudio studio,
            DeferredDuplication requested
    ) {
        JigsawStudioSession session = studio.generator().getSession();
        UUID requestId = studio.generator().getRequest().requestId();
        boolean autosavePending;
        synchronized (saveLifecycleLock) {
            autosavePending = session.isDirty() || savesInProgress.contains(requestId);
        }
        if (!autosavePending) {
            return false;
        }
        DeferredDuplication existing = deferredDuplications.putIfAbsent(requestId, requested);
        if (existing == null) {
            message(player, requested.kind() == DeferredDuplicationKind.SINGLE
                    ? "Autosave is finishing first; this cell's variant will duplicate automatically."
                    : "Autosave is finishing every edited cell first; the coherent family will duplicate automatically.");
            expediteAutosaves(requestId);
            scheduleDeferredDuplication(requested);
            return true;
        }
        message(player, existing.sameIntent(requested)
                ? "That duplicate action is already queued behind autosave."
                : "Another duplicate action is already queued behind autosave for this Studio.");
        return true;
    }

    private void scheduleDeferredDuplication(DeferredDuplication deferred) {
        if (deferredDuplications.get(deferred.requestId()) != deferred
                || !deferred.scheduled().compareAndSet(false, true)) {
            return;
        }
        scheduler.later(AUTOSAVE_RETRY_TICKS, () -> {
            deferred.scheduled().set(false);
            if (deferredDuplications.get(deferred.requestId()) != deferred) {
                return;
            }
            ToolPlayer player = onlinePlayer(studios.get(deferred.worldId()), deferred.playerId());
            if (player == null) {
                deferredDuplications.remove(deferred.requestId(), deferred);
                return;
            }
            scheduler.onPlayerLater(
                    player,
                    () -> resumeDeferredDuplication(player, deferred),
                    0,
                    () -> deferredDuplications.remove(deferred.requestId(), deferred));
        });
    }

    private static ToolPlayer onlinePlayer(ActiveStudio studio, UUID playerId) {
        if (studio == null) {
            return null;
        }
        for (ToolPlayer candidate : studio.world().players()) {
            if (candidate.id().equals(playerId)) {
                return candidate;
            }
        }
        return null;
    }

    private void resumeDeferredDuplication(ToolPlayer player, DeferredDuplication deferred) {
        if (deferredDuplications.get(deferred.requestId()) != deferred) {
            return;
        }
        ActiveStudio studio = studios.get(deferred.worldId());
        JigsawStudioSession session = studio == null ? null : studio.generator().getSession();
        boolean currentRequest = studio != null
                && studio.generator().getRequest().requestId().equals(deferred.requestId())
                && session.sessionId().equals(deferred.sessionId())
                && studioOf(player) == studio;
        boolean sourcesCurrent = currentRequest && sourceBindingsCurrent(session, deferred.sourcePieces());
        boolean autosavePending;
        boolean operationPending;
        boolean terminal;
        synchronized (saveLifecycleLock) {
            autosavePending = currentRequest
                    && (session.isDirty() || savesInProgress.contains(deferred.requestId()));
            operationPending = currentRequest
                    && (graphMutationsInProgress.contains(deferred.requestId())
                    || materializationsInProgress.contains(deferred.requestId())
                    || exportsInProgress.contains(deferred.requestId())
                    || session.operationInProgress()
                    || hasJigsawTileWatch(deferred.requestId()));
            terminal = !currentRequest
                    || closingRequests.contains(deferred.requestId())
                    || reopenRequiredRequests.contains(deferred.requestId());
        }
        DeferredDuplicationReadiness readiness = deferredDuplicationReadiness(
                currentRequest,
                sourcesCurrent,
                autosavePending,
                operationPending,
                terminal);
        if (readiness == DeferredDuplicationReadiness.STALE) {
            deferredDuplications.remove(deferred.requestId(), deferred);
            message(player, "The queued duplicate was cancelled because its Studio or source variant changed.");
            return;
        }
        if (readiness == DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE) {
            expediteAutosaves(deferred.requestId());
            scheduleDeferredDuplication(deferred);
            return;
        }
        if (readiness == DeferredDuplicationReadiness.WAITING_FOR_OPERATION) {
            scheduleDeferredDuplication(deferred);
            return;
        }
        boolean started = deferred.kind() == DeferredDuplicationKind.SINGLE
                ? createVariant(player, deferred.workcellId(), true)
                : duplicateActiveFamily(player, deferred.themeKey());
        if (started) {
            deferredDuplications.remove(deferred.requestId(), deferred);
            return;
        }
        if (isCurrentRequest(studio, deferred.requestId())
                && sourceBindingsCurrent(session, deferred.sourcePieces())) {
            scheduleDeferredDuplication(deferred);
        } else {
            deferredDuplications.remove(deferred.requestId(), deferred);
        }
    }

    static DeferredDuplicationReadiness deferredDuplicationReadiness(
            boolean currentRequest,
            boolean sourcesCurrent,
            boolean autosavePending,
            boolean operationPending,
            boolean terminal
    ) {
        if (!currentRequest || !sourcesCurrent || terminal) {
            return DeferredDuplicationReadiness.STALE;
        }
        if (autosavePending) {
            return DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE;
        }
        if (operationPending) {
            return DeferredDuplicationReadiness.WAITING_FOR_OPERATION;
        }
        return DeferredDuplicationReadiness.READY;
    }

    private static boolean sourceBindingsCurrent(
            JigsawStudioSession session,
            Map<String, String> expectedSources
    ) {
        if (session == null) {
            return false;
        }
        for (Map.Entry<String, String> source : expectedSources.entrySet()) {
            if (session.activeVariant(source.getKey())
                    .map(JigsawStudioVariant::pieceKey)
                    .filter(source.getValue()::equals)
                    .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String beginGraphMutation(
            ActiveStudio studio,
            JigsawStudioSession session
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        finalizeJigsawTileWatches(requestId);
        synchronized (saveLifecycleLock) {
            if (closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot update its graph.";
            }
            if (savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return "A Jigsaw Studio graph update is already running.";
            }
            if (exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (materializationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio variant load or rollback to finish.";
            }
            if (session.operationInProgress()) {
                return "Wait for the current workcell operation to finish.";
            }
            if (reopenRequiredRequests.contains(requestId)) {
                return "Close and reopen Jigsaw Studio before making another graph change.";
            }
            if (hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before changing graph metadata.";
            }
            if (session.isDirty()) {
                return "Wait for every dirty workcell to finish autosaving before changing graph metadata.";
            }
            graphMutationsInProgress.add(requestId);
            return "";
        }
    }

    public boolean runCommandGraphMutation(
            ToolPlayer player,
            UUID expectedRequestId,
            CommandGraphMutation task
    ) {
        Objects.requireNonNull(expectedRequestId, "Jigsaw Studio command graph request ID");
        Objects.requireNonNull(task, "Jigsaw Studio command graph mutation");
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> runCommandGraphMutation(player, expectedRequestId, task));
        }
        ActiveStudio studio = studioOf(player);
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
        return scheduleGraphMutation(player, studio, packRoot -> task.run());
    }

    private boolean scheduleGraphMutation(
            ToolPlayer player,
            ActiveStudio studio,
            RootedGraphMutation task
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        scheduler.async(() -> {
            CommandGraphMutationResult result = null;
            Throwable failure = null;
            try {
                result = studio.generator().getPack().edit(task::run);
            } catch (Throwable exception) {
                failure = exception;
            }
            CommandGraphMutationResult completedResult = result;
            Throwable completedFailure = failure;
            scheduler.onPlayerLater(
                    player,
                    () -> completeGraphMutation(
                            player,
                            studio,
                            requestId,
                            completedResult,
                            completedFailure),
                    0,
                    () -> {
                        finishGraphMutation(requestId);
                        if (completedFailure != null) {
                            reportError(completedFailure);
                        }
                    });
        });
        return true;
    }

    /** A change to the pack's files the studio makes for a graph edit, given the pack folder it may change. */
    @FunctionalInterface
    private interface RootedGraphMutation {
        CommandGraphMutationResult run(Path packRoot) throws IOException;
    }

    private void completeGraphMutation(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            Throwable failure
    ) {
        try {
            if (failure != null) {
                reportError(failure);
                message(player, "Graph update failed: " + failureMessage(failure));
                finishGraphMutation(requestId);
                return;
            }
            if (result == null) {
                message(player, "Graph update completed without a result; no Studio state changed.");
                finishGraphMutation(requestId);
                return;
            }
            if (!isCurrentRequest(studio, requestId)) {
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
            reportError(exception);
            message(player, "The graph updated on disk, but Studio could not refresh: "
                    + failureMessage(exception) + ". Reopen this project before editing further.");
            reopenRequiredRequests.add(requestId);
            finishGraphMutation(requestId);
        }
    }

    private void finishGraphMutationSuccess(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result
    ) {
        for (JigsawStudioBay workcell : result.layout().bays()) {
            refreshWorkcellContext(studio.worldId(), workcell.stableId());
        }
        scheduleEvaluation(studio);
        message(player, result.message());
        finishGraphMutation(requestId);
        if (!result.activatePieceKey().isEmpty()) {
            switchVariant(
                    player,
                    result.activateWorkcellId(),
                    result.activatePieceKey(),
                    false);
        } else if (result.reload().isPresent()) {
            reloadActiveVariant(player, studio, result.reload().orElseThrow());
        }
    }

    private void scheduleLiveRelayout(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            JigsawStudioLayout previousLayout,
            CommandGraphMutationResult result
    ) {
        Set<Long> chunks = relayoutChunks(previousLayout, result.layout());
        if (chunks.isEmpty()) {
            completeLiveRelayout(player, studio, requestId, result, "", null);
            return;
        }
        List<ChunkPos> positions = chunks.stream()
                .map(chunkKey -> new ChunkPos((int) (chunkKey >> 32), (int) chunkKey.longValue()))
                .toList();
        studio.world().repaint(positions).whenComplete((ignored, repaintFailure) -> {
            if (repaintFailure != null) {
                scheduleLiveRelayoutCompletion(
                        player,
                        studio,
                        requestId,
                        result,
                        "the chunks could not regenerate: " + failureMessage(repaintFailure),
                        repaintFailure);
                return;
            }
            hydrateRepaintedChunks(player, studio, requestId, result, positions);
        });
    }

    private void hydrateRepaintedChunks(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            List<ChunkPos> positions
    ) {
        AtomicInteger remaining = new AtomicInteger(positions.size());
        AtomicReference<String> failure = new AtomicReference<>("");
        AtomicReference<Throwable> cause = new AtomicReference<>();
        Runnable chunkDone = () -> {
            if (remaining.decrementAndGet() == 0) {
                scheduleLiveRelayoutCompletion(player, studio, requestId, result, failure.get(), cause.get());
            }
        };
        for (ChunkPos position : positions) {
            JigsawStudioThreads.onRegion(log, studio.world(), position.x(), position.z(), () -> {
                try {
                    hydrateRepaintedChunk(studio, position.x(), position.z());
                } catch (Throwable exception) {
                    failure.compareAndSet("", "chunk " + position.x() + "," + position.z()
                            + " could not regenerate: " + failureMessage(exception));
                    cause.compareAndSet(null, exception);
                }
                chunkDone.run();
            }, () -> {
                failure.compareAndSet("", "chunk " + position.x() + "," + position.z()
                        + " could not be scheduled on its owning region");
                chunkDone.run();
            });
        }
    }

    private void scheduleLiveRelayoutCompletion(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            String failure,
            Throwable cause
    ) {
        scheduler.onPlayerLater(
                player,
                () -> completeLiveRelayout(player, studio, requestId, result, failure, cause),
                0,
                () -> {
                    reopenRequiredRequests.add(requestId);
                    finishGraphMutation(requestId);
                    if (cause != null) {
                        reportError(cause);
                    }
                });
    }

    private void completeLiveRelayout(
            ToolPlayer player,
            ActiveStudio studio,
            UUID requestId,
            CommandGraphMutationResult result,
            String failure,
            Throwable cause
    ) {
        if (!failure.isEmpty() || !isCurrentRequest(studio, requestId)) {
            reopenRequiredRequests.add(requestId);
            studio.populations().clear();
            if (cause != null) {
                reportError(cause);
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
        reopenRequiredRequests.remove(requestId);
        finishGraphMutationSuccess(player, studio, requestId, result);
        studio.generator().getSession().selectedBayId().ifPresent(workcellId -> teleportTo(player, workcellId));
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
                    chunks.add(chunkKey(chunkX, chunkZ));
                }
            }
        }
    }

    private void hydrateRepaintedChunk(ActiveStudio studio, int chunkX, int chunkZ) throws IOException {
        ToolWorld world = studio.world();
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
    }

    private boolean graphMutationInProgress(UUID requestId) {
        synchronized (saveLifecycleLock) {
            return graphMutationsInProgress.contains(requestId);
        }
    }

    private void finishGraphMutation(UUID requestId) {
        synchronized (saveLifecycleLock) {
            graphMutationsInProgress.remove(requestId);
        }
    }

    private static JigsawStudioLayout loadMappedLayout(ActiveStudio studio) throws IOException {
        JigsawStudioRequest request = studio.generator().getRequest();
        request.reloadSource();
        IrisStructure structure = StructureResources.structure(request.source().resources(), request.structureKey());
        if (structure == null) {
            throw new IOException("The structure could not be reloaded after the graph transaction");
        }
        return JigsawStudioGraphMapper.map(request.source(), request.packRoot(), request.structureKey(), structure);
    }

    public boolean showAssemblyPreview(ToolPlayer player, List<PlacedStructurePiece> pieces) {
        if (player == null || pieces == null || pieces.isEmpty()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> showAssemblyPreview(player, pieces));
        }
        if (studioOf(player) == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        List<JigsawStudioBounds> bounds = new ArrayList<>(pieces.size());
        for (PlacedStructurePiece piece : pieces) {
            int width = Math.addExact(Math.subtractExact(piece.getMaxX(), piece.getMinX()), 1);
            int height = Math.addExact(Math.subtractExact(piece.getMaxY(), piece.getMinY()), 1);
            int depth = Math.addExact(Math.subtractExact(piece.getMaxZ(), piece.getMinZ()), 1);
            bounds.add(new JigsawStudioBounds(
                    piece.getMinX(),
                    piece.getMinY(),
                    piece.getMinZ(),
                    new JigsawStudioCellDimensions(width, height, depth)));
        }
        assemblyPreviews.put(player.id(), new AssemblyPreview(
                studioOf(player).worldId(),
                System.currentTimeMillis() + ASSEMBLY_PREVIEW_MILLIS,
                List.copyOf(bounds)));
        particlesDisabled.remove(player.id());
        ensureVisualizationLoop(player);
        return true;
    }

    /** The world was unloaded: nothing can be saved from it any more, so the studio goes without a last drain. */
    void worldClosed(UUID worldId) {
        earlyChunks.remove(worldId);
        ActiveStudio studio = studios.get(worldId);
        if (studio != null) {
            UUID requestId = studio.generator().getRequest().requestId();
            finalizeJigsawTileWatches(requestId);
            synchronized (saveLifecycleLock) {
                discardingRequests.add(requestId);
            }
        }
        unregister(worldId);
    }

    void chunkLoaded(UUID worldId, int chunkX, int chunkZ) {
        ActiveStudio studio = studios.get(worldId);
        if (studio == null) {
            earlyChunks.computeIfAbsent(worldId, id -> ConcurrentHashMap.newKeySet()).add(chunkKey(chunkX, chunkZ));
            return;
        }
        markChunkAvailable(studio, chunkX, chunkZ);
    }

    void playerEntered(ToolPlayer player) {
        scheduler.playerEntered(player.id());
        closeMenu(player);
        finalizeJigsawTileWatchesForPlayer(player.id());
        tripleSneakTracker.clearPlayer(player.id());
        visualizationLoops.remove(player.id());
        assemblyPreviews.remove(player.id());
        playerWorkcells.remove(player.id());
        ensureVisualizationLoop(player);
    }

    /** A player arrived by other means than walking; the host tells it at the time of the teleport, after the arrival it belongs to. */
    void playerTeleported(ToolPlayer player, BlockPosition to) {
        finalizeJigsawTileWatchesForPlayer(player.id());
        reconcilePlayerContext(player, to);
    }

    /** The contents of a container or machine changed without a block change: the container's workcell has unsaved work. */
    void containerChanged(UUID worldId, BlockPosition at) {
        markDirty(studios.get(worldId), at);
    }

    void playerLeft(ToolPlayer player) {
        UUID playerId = player.id();
        scheduler.playerLeft(playerId);
        closeMenu(player);
        finalizeJigsawTileWatchesForPlayer(playerId);
        tripleSneakTracker.clearPlayer(playerId);
        toolConfirmations.remove(playerId);
        visualizationLoops.remove(playerId);
        assemblyPreviews.remove(playerId);
        playerWorkcells.remove(playerId);
        deferredDuplications.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        player.hideSidebar();
    }

    void playerMoved(ToolPlayer player, BlockPosition to) {
        reconcilePlayerContext(player, to);
    }

    void sneakToggled(ToolPlayer player, boolean sneaking) {
        if (!sneaking) {
            return;
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null || !ownerMatches(player, studio)) {
            tripleSneakTracker.clearPlayer(player.id());
            return;
        }
        JigsawStudioTripleSneakTracker.Progress progress = tripleSneakTracker.recordSneak(
                player.id(),
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                System.nanoTime());
        if (progress == JigsawStudioTripleSneakTracker.Progress.TRIGGERED) {
            openControlMenu(player);
        }
    }

    /** A player used their hand; false cancels it. */
    boolean playerUsed(UUID worldId, ToolPlayer player, ToolUse use) {
        ActiveStudio studio = studios.get(worldId);
        if (studio == null) {
            return true;
        }
        if (usesStudioTool(player, use)) {
            return false;
        }
        boolean rightClickBlock = use.action() == ToolUse.Action.RIGHT_CLICK_BLOCK;
        if (rightClickBlock && use.block().isPresent()) {
            BlockPosition clicked = use.block().get();
            if (isControlChest(studio, clicked)) {
                return !opensControlMenu(player, studio, use);
            }
            if (isUnauthorizedStudioEdit(player, studio, clicked)) {
                return false;
            }
        }
        observeInteraction(player, studio, use);
        return true;
    }

    private boolean usesStudioTool(ToolPlayer player, ToolUse use) {
        boolean rightClick = use.action() == ToolUse.Action.RIGHT_CLICK_AIR
                || use.action() == ToolUse.Action.RIGHT_CLICK_BLOCK;
        if (use.hand() != ToolUse.Hand.MAIN || !rightClick || use.item().isEmpty()) {
            return false;
        }
        Optional<JigsawStudioToolPayload> decoded = toolCodec.decode(use.item().get());
        if (decoded.isEmpty()) {
            return false;
        }
        finalizeJigsawTileWatchesForPlayer(player.id());
        useTool(player, decoded.get(), use.item().get(), player.sneaking());
        return true;
    }

    private boolean opensControlMenu(ToolPlayer player, ActiveStudio studio, ToolUse use) {
        if (use.hand() != ToolUse.Hand.MAIN) {
            return false;
        }
        if (authorizeOwner(player, studio)) {
            openControlMenu(player);
        }
        return true;
    }

    private void observeInteraction(ToolPlayer player, ActiveStudio studio, ToolUse use) {
        boolean interacted = use.action() == ToolUse.Action.RIGHT_CLICK_BLOCK
                || use.action() == ToolUse.Action.PHYSICAL;
        if (!interacted || use.block().isEmpty()) {
            return;
        }
        BlockPosition clicked = use.block().get();
        if (use.action() == ToolUse.Action.RIGHT_CLICK_BLOCK) {
            finalizeJigsawTileWatchesForPlayer(player.id());
            if (blockIfOwned(studio, clicked).filter(JigsawStudioBlocks::isJigsaw).isPresent()) {
                startJigsawTileWatch(player, studio, clicked);
            }
        }
        markDirty(studio, clicked);
    }

    private static Optional<BlockState> blockIfOwned(ActiveStudio studio, BlockPosition at) {
        return studio.world().owns(at) ? Optional.of(studio.world().block(at)) : Optional.empty();
    }

    /** Something is about to change blocks; false cancels it. */
    boolean blockChanging(UUID worldId, BlockChange change) {
        ActiveStudio studio = studios.get(worldId);
        if (studio == null) {
            return false;
        }
        if (!allowsBlockChange(studio, change)) {
            return false;
        }
        markChangedBlocksDirty(studio, change);
        return true;
    }

    private boolean allowsBlockChange(ActiveStudio studio, BlockChange change) {
        ToolPlayer player = change.player().orElse(null);
        return switch (change.cause()) {
            case PLACE, BUCKET -> change.positions().stream()
                    .noneMatch(position -> isUnauthorizedStudioEdit(player, studio, position));
            case BREAK -> allowsBreak(player, studio, change.positions().getFirst());
            case CONTAINER -> !blocksContainerMutation(studio, change);
            case MACHINE -> change.positions().stream().noneMatch(position -> blocksMachineMutation(studio, position));
            case PHYSICS, FLUID, GROWTH, FIRE, REDSTONE, PISTON, EXPLOSION, ENTITY ->
                    !materializationInProgress(studio)
                            && change.positions().stream().noneMatch(position -> isImmutableStudioBlock(studio, position));
        };
    }

    private boolean allowsBreak(ToolPlayer player, ActiveStudio studio, BlockPosition position) {
        if (isControlChest(studio, position)) {
            message(player, "The Jigsaw Studio control chest cannot be removed.");
            return false;
        }
        return !isUnauthorizedStudioEdit(player, studio, position);
    }

    private boolean blocksContainerMutation(ActiveStudio studio, BlockChange change) {
        UUID actorId = change.player().map(ToolPlayer::id).orElse(null);
        for (BlockPosition position : change.positions()) {
            StudioBlockMutationContext context = studioBlockMutationContext(studio, position);
            if (context != null && blocksStudioInventoryMutation(
                    true,
                    context.immutable(),
                    materializationInProgress(context.studio()),
                    reopenRequiredRequests.contains(context.requestId()),
                    context.ownerId(),
                    actorId)) {
                change.player().ifPresent(player -> message(
                        player,
                        "This Jigsaw Studio container is read-only during the current operation or owner session."));
                return true;
            }
        }
        return false;
    }

    private void markChangedBlocksDirty(ActiveStudio studio, BlockChange change) {
        List<BlockPosition> positions = change.positions();
        switch (change.cause()) {
            case FLUID -> markDirty(studio, positions.getLast());
            case PHYSICS -> markDirty(studio, positions.getFirst());
            case PISTON -> change.changed().forEach(position -> markDirty(studio, position));
            default -> positions.forEach(position -> markDirty(studio, position));
        }
    }

    /** A command is about to run; false cancels it. */
    boolean command(UUID worldId, Optional<ToolPlayer> sender, String line) {
        ActiveStudio studio = studios.get(worldId);
        if (sender.isPresent()) {
            return playerCommand(studio, sender.get(), line);
        }
        return consoleCommand(studio, line);
    }

    private boolean playerCommand(ActiveStudio studio, ToolPlayer player, String line) {
        finalizeJigsawTileWatchesForPlayer(player.id());
        if (studio != null
                && materializationInProgress(studio)
                && !isSafeNonOwnerCommand(line)) {
            message(player, "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return false;
        }
        if (studio != null && blocksNonEditableWorkcellMutation(hasNonEditableWorkcell(studio), line)) {
            message(player, "Mutating commands are disabled while this Studio has an empty or read-only workcell.");
            return false;
        }
        UUID ownerId = studio == null ? null : studio.generator().getRequest().ownerId();
        if (blocksMutatingCommand(ownerId, player.id(), line)) {
            message(player, "This Jigsaw Studio is owned by another player session.");
            return false;
        }
        if (studio != null && isMutatingCommand(line)) {
            markAllDirty(studio);
        }
        return true;
    }

    private boolean consoleCommand(ActiveStudio studio, String line) {
        if (!isSafeNonOwnerCommand(line) && anyMaterializationInProgress()) {
            return false;
        }
        if (isMutatingCommand(line) && anyNonEditableWorkcell()) {
            return false;
        }
        if (studio != null && isMutatingCommand(line)) {
            markAllDirty(studio);
        }
        return true;
    }

    /** Whether the host may close the world now, as the message that says why not. */
    Optional<String> closeRefusal(UUID worldId) {
        return Optional.ofNullable(closeProtectionFailure(worldId));
    }

    /**
     * Gets the studio ready to close, for a replacement or for the host stopping: saves what the calling thread may save
     * at once, then waits for the autosaves that must run elsewhere.
     */
    CompletableFuture<Void> closing(UUID worldId, Optional<ToolPlayer> by) {
        drainAutosavesBeforeRemoval(studios.get(worldId));
        return awaitCloseForReplacement(worldId, by.map(ToolPlayer::id).orElse(null));
    }

    private boolean markDirty(ActiveStudio studio, BlockPosition at) {
        if (studio == null) {
            return false;
        }
        int worldX = at.x();
        int worldY = at.y();
        int worldZ = at.z();
        if (reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(worldX, worldY, worldZ);
        if (bay == null) {
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            return false;
        }
        JigsawStudioSession.DirtyMark dirtyMark = session.markWorkcellDirty(bay.stableId());
        if (dirtyMark.status() != JigsawStudioSession.DirtyStatus.MARKED) {
            return false;
        }
        scheduleAutosave(studio, bay, dirtyMark.identity().orElseThrow(), AUTOSAVE_DEBOUNCE_TICKS);
        markEvaluationStale(studio);
        if (dirtyMark.newlyDirty()) {
            refreshWorkcellContext(studio.worldId(), bay.stableId());
        }
        return true;
    }

    private boolean scheduleCapture(
            ActiveStudio studio,
            ToolWorld world,
            JigsawStudioBay bay,
            CaptureTarget captureTarget,
            ToolPlayer player,
            UUID requestId,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState autosaveFailureState
    ) {
        List<ChunkCaptureArea> areas = chunkIntersections(captureTarget.bounds());
        CaptureWork work = new CaptureWork(
                studio,
                world,
                bay,
                captureTarget,
                player,
                requestId,
                saveIdentity,
                autosaveFailureState);
        CaptureCoordinator coordinator = new CaptureCoordinator(
                work,
                areas);
        for (ChunkCaptureArea area : areas) {
            if (coordinator.stopped()) {
                return false;
            }
            JigsawStudioThreads.onRegion(
                    log,
                    world,
                    area.chunkX(),
                    area.chunkZ(),
                    () -> captureChunk(coordinator, area),
                    () -> coordinator.fail("Iris could not schedule bay chunk "
                            + area.chunkX() + "," + area.chunkZ() + " on its owning region.", null));
        }
        return !coordinator.stopped();
    }

    private boolean scheduleMaterialization(MaterializationWork work) {
        String validationFailure = validateMaterialization(work.target(), registryOf(work.studio()));
        if (!validationFailure.isEmpty()) {
            work.studio().generator().getSession().abortVariantSwitch(work.token());
            refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
            message(work.player(), "Variant '" + work.token().targetVariant().pieceKey()
                    + "' cannot load: " + validationFailure);
            return false;
        }
        String reservationFailure = beginMaterialization(work);
        if (!reservationFailure.isEmpty()) {
            work.studio().generator().getSession().abortVariantSwitch(work.token());
            refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
            message(work.player(), reservationFailure);
            return false;
        }
        List<ChunkCaptureArea> areas = chunkIntersections(work.workcell().bounds());
        MaterializationCoordinator coordinator = new MaterializationCoordinator(work, areas);
        for (ChunkCaptureArea area : areas) {
            coordinator.markScheduled();
            JigsawStudioThreads.onRegion(
                    log,
                    work.world(),
                    area.chunkX(),
                    area.chunkZ(),
                    () -> materializeCandidateChunk(coordinator, area),
                    () -> coordinator.candidateComplete(
                            area,
                            "Iris could not schedule workcell chunk "
                                    + area.chunkX() + "," + area.chunkZ() + " on its owning region.",
                            null));
        }
        return coordinator.scheduledAny();
    }

    private String beginMaterialization(MaterializationWork work) {
        UUID requestId = work.studio().generator().getRequest().requestId();
        synchronized (saveLifecycleLock) {
            if (!isCurrentVariantSwitch(work)) {
                return "The Jigsaw Studio changed before the variant load could start.";
            }
            if (closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot load another variant.";
            }
            if (savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio graph update to finish.";
            }
            if (exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before loading another variant.";
            }
            if (!materializationsInProgress.add(requestId)) {
                return "Another Jigsaw Studio variant load or rollback is already running.";
            }
            return "";
        }
    }

    private String beginConnectorRepair(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        finalizeJigsawTileWatches(requestId);
        synchronized (saveLifecycleLock) {
            if (!isCurrentRequest(studio, requestId)) {
                return "This Jigsaw Studio session is no longer active.";
            }
            if (closingRequests.contains(requestId)) {
                return "This Jigsaw Studio is closing and cannot reset connector blocks.";
            }
            if (savesInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio save to finish.";
            }
            if (graphMutationsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio graph update to finish.";
            }
            if (exportsInProgress.contains(requestId)) {
                return "Wait for the current Jigsaw Studio export to finish.";
            }
            if (studio.generator().getSession().operationInProgress()) {
                return "Wait for the current workcell operation to finish.";
            }
            if (hasJigsawTileWatch(requestId)) {
                return "Finish or close the open vanilla jigsaw-block editor before resetting connectors.";
            }
            if (!materializationsInProgress.add(requestId)) {
                return "Another Jigsaw Studio variant load or repair is already running.";
            }
            return "";
        }
    }

    private boolean scheduleConnectorRepair(
            ToolPlayer player,
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
            scheduledAny.set(true);
            JigsawStudioThreads.onRegion(log, studio.world(), chunkX, chunkZ, () -> {
                try {
                    if (!studio.isChunkLoaded(chunkX, chunkZ)) {
                        throw new IOException("connector chunk " + chunkX + "," + chunkZ
                                + " is not loaded");
                    }
                    restoreConnectorChunk(
                            studio.world(),
                            studio.generator().getPack().resources().blocks(),
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
            }, () -> {
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
            });
        }
        if (!scheduledAny.get()) {
            finishMaterialization(studio.generator().getRequest().requestId());
        }
        return scheduledAny.get();
    }

    private void completeConnectorRepair(
            ToolPlayer player,
            ActiveStudio studio,
            int connectorCount,
            String failure,
            Throwable cause
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        scheduler.onPlayerLater(player, () -> {
            finishMaterialization(requestId);
            if (cause != null) {
                reportError(cause);
            }
            if (!failure.isEmpty()) {
                message(player, "Connector reset was incomplete: " + failure + ". Retry after visiting the workcell.");
                return;
            }
            message(player, "Restored " + connectorCount
                    + " connector block(s) from the last saved iteration.");
        }, 0, () -> {
            finishMaterialization(requestId);
            if (cause != null) {
                reportError(cause);
            }
        });
    }

    static void restoreConnectorChunk(
            ToolWorld world,
            BlockRegistry registry,
            JigsawStudioBay workcell,
            List<JigsawStudioGenerator.RenderedConnector> connectors,
            Map<LocalPosition, JigsawStudioGenerator.RenderedBlock> renderedBlocks,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        for (JigsawStudioGenerator.RenderedConnector connector : connectors) {
            LocalPosition position = new LocalPosition(connector.x(), connector.y(), connector.z());
            BlockPosition target = new BlockPosition(
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z());
            if (connectorsVisible) {
                BlockState marker = jigsawMarker(registry, connector.orientation())
                        .orElseThrow(() -> new IOException("Invalid saved connector orientation '"
                                + connector.orientation() + "'"));
                world.setBlock(target, marker, Optional.of(markerBlockEntity(connector.connector())));
                continue;
            }
            JigsawStudioGenerator.RenderedBlock renderedBlock = renderedBlocks.get(position);
            BlockState restored = renderedBlock != null
                    ? renderedBlock.state()
                    : registry.find(connector.connector().getFinalState())
                    .orElseThrow(() -> new IOException("Invalid saved connector final state '"
                            + connector.connector().getFinalState() + "'"));
            Optional<BlockEntityData> blockEntity = renderedBlock == null
                    ? Optional.empty()
                    : Optional.ofNullable(renderedBlock.blockEntity());
            try {
                world.setBlock(target, restored, blockEntity);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Saved connector tile data could not be restored at "
                        + target.x() + "," + target.y() + "," + target.z(), exception);
            }
        }
    }

    /** The jigsaw block of the platform facing {@code orientation}; empty when the platform has no such block. */
    private static Optional<BlockState> jigsawMarker(BlockRegistry registry, String orientation) {
        return registry.find(JigsawStudioBlocks.jigsawMarker(orientation).key())
                .filter(JigsawStudioBlocks::isJigsaw);
    }

    private static BlockEntityData markerBlockEntity(IrisJigsawConnector connector) {
        return JigsawStudioTileCodec.encode(JigsawStudioBlocks.JIGSAW_BLOCK, markerNbt(connector));
    }

    private void finishMaterialization(UUID requestId) {
        synchronized (saveLifecycleLock) {
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
        if (!work.studio().isChunkLoaded(area.chunkX(), area.chunkZ())) {
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
                    work.studio().generator().getPack().resources().blocks(),
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
        if (!work.studio().isChunkLoaded(area.chunkX(), area.chunkZ())) {
            coordinator.rollbackComplete(
                    area,
                    "Rollback chunk " + area.chunkX() + "," + area.chunkZ() + " is not loaded.",
                    null);
            return;
        }
        try {
            writeMaterializedChunk(
                    work.world(),
                    work.studio().generator().getPack().resources().blocks(),
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

    static String validateMaterialization(JigsawStudioGenerator.RenderedBay rendered, BlockRegistry registry) {
        if (!rendered.valid()) {
            return rendered.failure();
        }
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            BlockState state = block.state();
            if (registry.find(state.key()).isEmpty()) {
                return "block '" + state.key() + "' has no block representation on this platform";
            }
        }
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            if (jigsawMarker(registry, connector.orientation()).isEmpty()) {
                return "connector orientation '" + connector.orientation() + "' is invalid";
            }
        }
        return "";
    }

    private void writeMaterializedChunk(
            ToolWorld world,
            BlockRegistry registry,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            ChunkCaptureArea area,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        Map<LocalPosition, BlockState> expected = materializedBlocks(registry, rendered, connectorsVisible);
        for (int x = area.minimumX(); x < area.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = area.minimumZ(); z < area.maximumZ(); z++) {
                    BlockPosition target = new BlockPosition(
                            bounds.originX() + x,
                            bounds.originY() + y,
                            bounds.originZ() + z);
                    BlockState state = expected.getOrDefault(new LocalPosition(x, y, z), JigsawStudioBlocks.AIR);
                    world.setBlock(target, state, Optional.empty());
                }
            }
        }
        applyRenderedBayChunk(world, workcell, rendered, area.chunkX(), area.chunkZ(), connectorsVisible);
        verifyMaterializedChunk(world, workcell, rendered, area, expected, connectorsVisible);
    }

    private static Map<LocalPosition, BlockState> materializedBlocks(
            BlockRegistry registry,
            JigsawStudioGenerator.RenderedBay rendered,
            boolean connectorsVisible
    ) throws IOException {
        Map<LocalPosition, BlockState> expected = new HashMap<>();
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            expected.put(new LocalPosition(block.x(), block.y(), block.z()), block.state());
        }
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            if (!connectorsVisible && expected.containsKey(
                    new LocalPosition(connector.x(), connector.y(), connector.z()))) {
                continue;
            }
            Optional<BlockState> marker = connectorsVisible
                    ? jigsawMarker(registry, connector.orientation())
                    : registry.find(connector.connector().getFinalState());
            expected.put(
                    new LocalPosition(connector.x(), connector.y(), connector.z()),
                    marker.orElseThrow(() -> new IOException("Invalid rendered connector orientation '"
                            + connector.orientation() + "'")));
        }
        return expected;
    }

    private void verifyMaterializedChunk(
            ToolWorld world,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            ChunkCaptureArea area,
            Map<LocalPosition, BlockState> expected,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        for (int x = area.minimumX(); x < area.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = area.minimumZ(); z < area.maximumZ(); z++) {
                    BlockPosition target = new BlockPosition(
                            bounds.originX() + x,
                            bounds.originY() + y,
                            bounds.originZ() + z);
                    BlockState expectedState = expected.getOrDefault(new LocalPosition(x, y, z), JigsawStudioBlocks.AIR);
                    if (!sameBlock(world.block(target), expectedState)) {
                        throw new IOException("Workcell block did not materialize at "
                                + target.x() + "," + target.y() + "," + target.z());
                    }
                }
            }
        }
        verifyRenderedBayChunk(world, workcell, rendered, area.chunkX(), area.chunkZ(), connectorsVisible);
    }

    /**
     * Whether the block in the world is the one that was written: the same block with every property equal. The states
     * written are the platform's own complete states, so a block the platform changed on its own does not pass.
     */
    static boolean sameBlock(BlockState actual, BlockState written) {
        return actual.equals(written);
    }

    private static BlockRegistry registryOf(ActiveStudio studio) {
        return studio.generator().getPack().resources().blocks();
    }

    private boolean isCurrentVariantSwitch(MaterializationWork work) {
        return isCurrentRequest(
                work.studio(),
                work.studio().generator().getRequest().requestId())
                && work.studio().generator().getSession().isVariantSwitchCurrent(work.token());
    }

    private boolean isRollbackVariantSwitch(MaterializationWork work) {
        return studios.get(work.studio().worldId()) == work.studio()
                && materializationInProgress(work.studio());
    }

    private boolean blocksMachineMutation(ActiveStudio studio, BlockPosition at) {
        StudioBlockMutationContext context = studioBlockMutationContext(studio, at);
        return context != null && blocksStudioInventoryMutation(
                true,
                context.immutable(),
                materializationInProgress(context.studio()),
                reopenRequiredRequests.contains(context.requestId()),
                context.ownerId(),
                null);
    }

    private StudioBlockMutationContext studioBlockMutationContext(ActiveStudio studio, BlockPosition block) {
        if (studio == null || block == null) {
            return null;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        boolean preview = previewRenderer.contains(
                request.requestId(), block.x(), block.y(), block.z());
        boolean controlChest = isControlChest(studio, block);
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(block.x(), block.y(), block.z());
        boolean workcell = bay != null;
        if (!preview && !controlChest && !workcell) {
            return null;
        }
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        boolean nonEditableWorkcell = bay != null && (variant == null || !variant.owned());
        return new StudioBlockMutationContext(
                studio,
                request.requestId(),
                request.ownerId(),
                preview || controlChest || nonEditableWorkcell);
    }

    private void startJigsawTileWatch(ToolPlayer player, ActiveStudio studio, BlockPosition block) {
        if (player == null || studio == null || block == null) {
            return;
        }
        ToolWorld world = studio.world();
        UUID playerId = player.id();
        if (JigsawStudioThreads.ownsRegion(world, block.x() >> CHUNK_SHIFT, block.z() >> CHUNK_SHIFT)) {
            initializeJigsawTileWatch(playerId, studio, block.x(), block.y(), block.z());
            return;
        }
        JigsawStudioThreads.onRegion(
                log,
                world,
                block.x() >> CHUNK_SHIFT,
                block.z() >> CHUNK_SHIFT,
                () -> initializeJigsawTileWatch(playerId, studio, block.x(), block.y(), block.z()),
                () -> warn("Jigsaw Studio could not watch jigsaw marker NBT at %d,%d,%d in %s",
                        block.x(), block.y(), block.z(), world.name()));
    }

    private void initializeJigsawTileWatch(
            UUID playerId,
            ActiveStudio studio,
            int worldX,
            int worldY,
            int worldZ
    ) {
        ToolWorld world = studio.world();
        if (studios.get(studio.worldId()) != studio) {
            return;
        }
        BlockPosition at = new BlockPosition(worldX, worldY, worldZ);
        if (!JigsawStudioBlocks.isJigsaw(world.block(at))) {
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(worldX, worldY, worldZ);
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        if (bay == null || variant == null || !variant.owned()) {
            return;
        }
        KMap<String, Object> snapshot;
        try {
            snapshot = world.blockEntity(at).flatMap(JigsawStudioTileCodec::nbt).orElse(null);
        } catch (Throwable exception) {
            reportError(exception);
            return;
        }
        if (snapshot == null) {
            warn("Jigsaw Studio could not read jigsaw marker NBT at %d,%d,%d in %s",
                    worldX, worldY, worldZ, world.name());
            return;
        }
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawTileWatchKey key = new JigsawTileWatchKey(
                request.requestId(), studio.worldId(), worldX, worldY, worldZ);
        JigsawTileWatch watch = new JigsawTileWatch(
                key,
                studio,
                playerId,
                bay.stableId(),
                snapshot,
                new AtomicBoolean(false),
                new AtomicBoolean(false));
        jigsawTileWatches.put(key, watch);
        scheduleJigsawTileWatch(watch);
    }

    private void scheduleJigsawTileWatch(JigsawTileWatch watch) {
        scheduler.onRegionLater(
                watch.studio().world(),
                watch.key().worldX() >> CHUNK_SHIFT,
                watch.key().worldZ() >> CHUNK_SHIFT,
                () -> runJigsawTileWatch(watch, false),
                JIGSAW_TILE_WATCH_INTERVAL_TICKS,
                () -> {
                    if (jigsawTileWatches.get(watch.key()) == watch) {
                        warnJigsawTileWatchScheduleFailure(watch);
                        scheduleJigsawTileWatchReconciliation(watch, false);
                    }
                });
    }

    private void runJigsawTileWatch(JigsawTileWatch watch, boolean release) {
        if (jigsawTileWatches.get(watch.key()) != watch) {
            return;
        }
        ActiveStudio studio = studios.get(watch.key().worldId());
        if (studio != watch.studio()
                || !isCurrentRequest(studio, watch.key().requestId())) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        BlockPosition watched = new BlockPosition(
                watch.key().worldX(), watch.key().worldY(), watch.key().worldZ());
        JigsawStudioBay bay = studio.generator().getSession().layout().findAt(
                watch.key().worldX(), watch.key().worldY(), watch.key().worldZ());
        if (!JigsawStudioBlocks.isJigsaw(studio.world().block(watched))
                || bay == null
                || !bay.stableId().equals(watch.workcellId())) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        KMap<String, Object> snapshot;
        try {
            snapshot = studio.world().blockEntity(watched).flatMap(JigsawStudioTileCodec::nbt).orElse(null);
        } catch (Throwable exception) {
            jigsawTileWatches.remove(watch.key(), watch);
            reportError(exception);
            return;
        }
        if (snapshot == null) {
            jigsawTileWatches.remove(watch.key(), watch);
            warn("Jigsaw Studio stopped watching unreadable jigsaw marker NBT at %d,%d,%d in %s",
                    watch.key().worldX(),
                    watch.key().worldY(),
                    watch.key().worldZ(),
                    studio.world().name());
            return;
        }
        JigsawTilePollDecision decision = jigsawTilePollDecision(
                watch.snapshot(), snapshot, release);
        if (decision.changed()) {
            markDirty(studio, watched);
        }
        if (!decision.continueWatching()) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        JigsawTileWatch next = new JigsawTileWatch(
                watch.key(),
                watch.studio(),
                watch.playerId(),
                watch.workcellId(),
                snapshot,
                watch.scheduleFailureLogged(),
                new AtomicBoolean(false));
        if (jigsawTileWatches.replace(watch.key(), watch, next)) {
            scheduleJigsawTileWatch(next);
        }
    }

    private boolean hasJigsawTileWatch(UUID requestId) {
        if (requestId == null) {
            return false;
        }
        for (JigsawTileWatchKey key : jigsawTileWatches.keySet()) {
            if (requestId.equals(key.requestId())) {
                return true;
            }
        }
        return false;
    }

    private void clearJigsawTileWatches(UUID requestId) {
        if (requestId != null) {
            jigsawTileWatches.keySet().removeIf(key -> requestId.equals(key.requestId()));
        }
    }

    private void finalizeJigsawTileWatchesForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (JigsawTileWatch watch : List.copyOf(jigsawTileWatches.values())) {
            if (playerId.equals(watch.playerId())) {
                finalizeJigsawTileWatch(watch);
            }
        }
    }

    private void finalizeJigsawTileWatches(UUID requestId) {
        if (requestId == null) {
            return;
        }
        for (JigsawTileWatch watch : List.copyOf(jigsawTileWatches.values())) {
            if (requestId.equals(watch.key().requestId())) {
                finalizeJigsawTileWatch(watch);
            }
        }
    }

    private void finalizeJigsawTileWatch(JigsawTileWatch watch) {
        if (jigsawTileWatches.get(watch.key()) != watch) {
            return;
        }
        ToolWorld world = watch.studio().world();
        int chunkX = watch.key().worldX() >> CHUNK_SHIFT;
        int chunkZ = watch.key().worldZ() >> CHUNK_SHIFT;
        if (JigsawStudioThreads.ownsRegion(world, chunkX, chunkZ)) {
            runJigsawTileWatch(watch, true);
            return;
        }
        JigsawStudioThreads.onRegion(
                log,
                world,
                chunkX,
                chunkZ,
                () -> runJigsawTileWatch(watch, true),
                () -> {
                    warnJigsawTileWatchScheduleFailure(watch);
                    scheduleJigsawTileWatchReconciliation(watch, true);
                });
    }

    private void warnJigsawTileWatchScheduleFailure(JigsawTileWatch watch) {
        if (watch.scheduleFailureLogged().compareAndSet(false, true)) {
            warn("Jigsaw Studio could not schedule jigsaw marker NBT at %d,%d,%d in %s; Iris will keep retrying",
                    watch.key().worldX(),
                    watch.key().worldY(),
                    watch.key().worldZ(),
                    watch.studio().world().name());
        }
    }

    private void scheduleJigsawTileWatchReconciliation(JigsawTileWatch watch, boolean release) {
        if (!watch.reconciliationScheduled().compareAndSet(false, true)) {
            return;
        }
        scheduler.later(AUTOSAVE_RETRY_TICKS, () -> {
            watch.reconciliationScheduled().set(false);
            if (jigsawTileWatches.get(watch.key()) != watch) {
                return;
            }
            if (release) {
                finalizeJigsawTileWatch(watch);
            } else {
                scheduleJigsawTileWatch(watch);
            }
        });
    }

    static boolean tileSnapshotChanged(
            KMap<String, Object> previous,
            KMap<String, Object> current
    ) {
        return !Objects.equals(previous, current);
    }

    static JigsawTilePollDecision jigsawTilePollDecision(
            KMap<String, Object> previous,
            KMap<String, Object> current,
            boolean release
    ) {
        return new JigsawTilePollDecision(tileSnapshotChanged(previous, current), !release);
    }

    private int markAllDirty(ActiveStudio studio) {
        int changed = 0;
        JigsawStudioSession session = studio.generator().getSession();
        for (JigsawStudioBay bay : session.layout().bays()) {
            JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
            if (activeVariant == null || !activeVariant.owned()) {
                continue;
            }
            JigsawStudioSession.DirtyMark dirtyMark = session.markWorkcellDirty(bay.stableId());
            if (dirtyMark.status() == JigsawStudioSession.DirtyStatus.MARKED) {
                scheduleAutosave(
                        studio,
                        bay,
                        dirtyMark.identity().orElseThrow(),
                        AUTOSAVE_DEBOUNCE_TICKS);
                changed++;
            }
        }
        if (changed > 0) {
            markEvaluationStale(studio);
            for (JigsawStudioBay bay : session.layout().bays()) {
                refreshWorkcellContext(studio.worldId(), bay.stableId());
            }
        }
        return changed;
    }

    private void scheduleAutosave(
            ActiveStudio studio,
            JigsawStudioBay bay,
            JigsawStudioSession.DirtyIdentity identity,
            int delayTicks
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        AutosaveKey key = new AutosaveKey(requestId, bay.stableId());
        AutosaveTicket ticket = new AutosaveTicket(
                key,
                studio,
                identity,
                new AtomicBoolean(false),
                new AtomicBoolean(false));
        autosaves.put(key, ticket);
        scheduleAutosave(ticket, delayTicks);
    }

    private void scheduleAutosave(AutosaveTicket ticket, int delayTicks) {
        if (autosaves.get(ticket.key()) != ticket
                || !ticket.scheduled().compareAndSet(false, true)) {
            return;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            ticket.scheduled().set(false);
            return;
        }
        JigsawStudioBounds bounds = bay.bounds();
        scheduler.onRegionLater(
                ticket.studio().world(),
                bounds.originX() >> CHUNK_SHIFT,
                bounds.originZ() >> CHUNK_SHIFT,
                () -> runAutosave(ticket),
                delayTicks,
                () -> {
                    ticket.scheduled().set(false);
                    if (ticket.scheduleFailureLogged().compareAndSet(false, true)) {
                        warn("Jigsaw Studio autosave could not schedule workcell %s for request %s; Iris will keep retrying",
                                ticket.key().workcellId(), ticket.key().requestId());
                    }
                    scheduleAutosaveReconciliation(ticket);
                });
    }

    private JigsawStudioBay resolveAutosaveBay(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return null;
        }
        JigsawStudioBay bay = resolveCurrentAutosaveBay(
                ticket.studio().generator().getSession(),
                ticket.key().workcellId());
        if (bay != null) {
            return bay;
        }
        if (autosaves.remove(ticket.key(), ticket)) {
            reportAutosaveFailure(
                    ticket,
                    new AutosavePersistentFailure(
                            "workcell is absent from the current Studio layout; pending ticket retired",
                            null));
        }
        return null;
    }

    static JigsawStudioBay resolveCurrentAutosaveBay(
            JigsawStudioSession session,
            String workcellId
    ) {
        JigsawStudioSession activeSession = Objects.requireNonNull(
                session,
                "Jigsaw Studio autosave session");
        return activeSession.layout().get(Objects.requireNonNull(
                workcellId,
                "Jigsaw Studio autosave workcell ID"));
    }

    private boolean retainCurrentAutosaveFailure(
            ActiveStudio studio,
            String workcellId,
            AutosavePersistentFailure failure
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                workcellId);
        AutosaveTicket ticket = autosaves.get(key);
        return ticket != null && retainPersistentAutosaveFailure(ticket, failure);
    }

    private AutosaveFailureState autosaveFailureState(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                saveIdentity.workcellId());
        AutosaveTicket ticket = autosaves.get(key);
        return ticket != null && matchesSaveIdentity(ticket.identity(), saveIdentity)
                ? ticket.failureState()
                : new AutosaveFailureState();
    }

    private boolean retainPersistentAutosaveFailure(
            AutosaveTicket ticket,
            AutosavePersistentFailure failure
    ) {
        if (autosaves.get(ticket.key()) != ticket
                || !isCurrentRequest(ticket.studio(), ticket.key().requestId())
                || !ticket.studio().generator().getSession().isDirtyCurrent(ticket.identity())) {
            return false;
        }
        AutosaveFailureDecision decision = reportAutosaveFailure(ticket, failure);
        expediteAutosave(ticket, decision.retryTicks());
        return true;
    }

    private AutosaveFailureDecision reportAutosaveFailure(
            AutosaveTicket ticket,
            AutosavePersistentFailure failure
    ) {
        JigsawStudioRequest request = ticket.studio().generator().getRequest();
        return recordPersistentAutosaveFailure(
                log,
                ticket.failureState(),
                ticket.key().requestId(),
                request.structureKey(),
                ticket.key().workcellId(),
                ticket.identity().variantKey(),
                failure.detail(),
                failure.cause());
    }

    private void reportPersistentSaveFailure(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState failureState,
            AutosavePersistentFailure failure
    ) {
        JigsawStudioRequest request = studio.generator().getRequest();
        recordPersistentAutosaveFailure(
                log,
                failureState,
                request.requestId(),
                request.structureKey(),
                saveIdentity.workcellId(),
                saveIdentity.variantKey(),
                failure.detail(),
                failure.cause());
    }

    static AutosaveFailureDecision recordPersistentAutosaveFailure(
            ModuleLog log,
            AutosaveFailureState state,
            UUID requestId,
            String structureKey,
            String workcellId,
            String pieceKey,
            String detail,
            Throwable cause
    ) {
        AutosaveFailureState activeState = Objects.requireNonNull(
                state,
                "Jigsaw Studio autosave failure state");
        AutosaveFailureDecision decision = activeState.recordPersistentFailure();
        if (!decision.logFailure()) {
            return decision;
        }
        String context = autosaveFailureContext(
                requestId,
                structureKey,
                workcellId,
                pieceKey,
                detail);
        if (cause == null) {
            log.warn(context);
        } else {
            log.error(context, cause);
        }
        return decision;
    }

    static String autosaveFailureContext(
            UUID requestId,
            String structureKey,
            String workcellId,
            String pieceKey,
            String detail
    ) {
        return "Jigsaw Studio save failure: request="
                + Objects.requireNonNull(requestId, "Jigsaw Studio autosave request ID")
                + " structure="
                + Objects.requireNonNull(structureKey, "Jigsaw Studio autosave structure key")
                + " workcell="
                + Objects.requireNonNull(workcellId, "Jigsaw Studio autosave workcell ID")
                + " piece="
                + Objects.requireNonNull(pieceKey, "Jigsaw Studio autosave piece key")
                + " failure="
                + Objects.requireNonNull(detail, "Jigsaw Studio autosave failure detail");
    }

    static int persistentAutosaveRetryTicks(int failureCount) {
        int index = Math.max(0, Math.min(
                failureCount - 1,
                AUTOSAVE_PERSISTENT_RETRY_DELAYS.size() - 1));
        return AUTOSAVE_PERSISTENT_RETRY_DELAYS.get(index);
    }

    private void scheduleAutosaveReconciliation(AutosaveTicket ticket) {
        scheduler.later(AUTOSAVE_RETRY_TICKS, () -> {
            if (autosaves.get(ticket.key()) == ticket
                    && !ticket.scheduled().get()) {
                scheduleAutosave(ticket, AUTOSAVE_RETRY_TICKS);
            }
        });
    }

    private void runAutosave(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return;
        }
        ticket.scheduled().set(false);
        ActiveStudio studio = ticket.studio();
        JigsawStudioSession session = studio.generator().getSession();
        if (!isCurrentRequest(studio, ticket.key().requestId())) {
            autosaves.remove(ticket.key(), ticket);
            return;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            return;
        }
        if (!session.isDirtyCurrent(ticket.identity())) {
            autosaves.remove(ticket.key(), ticket);
            return;
        }
        SaveAttempt attempt = startSave(studio, studio.world(), bay, null, false);
        if (attempt == SaveAttempt.STARTED || attempt == SaveAttempt.PERSISTENT_FAILURE) {
            return;
        }
        scheduleAutosave(
                ticket,
                attempt == SaveAttempt.RETRY
                        ? AUTOSAVE_RETRY_TICKS
                        : AUTOSAVE_DEBOUNCE_TICKS);
    }

    private void completeAutosaveAttempt(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState failureState,
            AutosavePersistentFailure persistentFailure
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                saveIdentity.workcellId());
        AutosaveTicket ticket = autosaves.get(key);
        if (ticket == null || !matchesSaveIdentity(ticket.identity(), saveIdentity)) {
            if (persistentFailure != null) {
                reportPersistentSaveFailure(studio, saveIdentity, failureState, persistentFailure);
            }
            return;
        }
        if (resolveAutosaveBay(ticket) == null) {
            return;
        }
        if (!studio.generator().getSession().isDirtyCurrent(ticket.identity())) {
            autosaves.remove(key, ticket);
            return;
        }
        if (persistentFailure != null) {
            retainPersistentAutosaveFailure(ticket, persistentFailure);
            return;
        }
        scheduleAutosave(ticket, AUTOSAVE_RETRY_TICKS);
    }

    private static boolean matchesSaveIdentity(
            JigsawStudioSession.DirtyIdentity dirtyIdentity,
            JigsawStudioSession.SaveIdentity saveIdentity
    ) {
        return dirtyIdentity.sessionId().equals(saveIdentity.sessionId())
                && dirtyIdentity.workcellId().equals(saveIdentity.workcellId())
                && dirtyIdentity.variantKey().equals(saveIdentity.variantKey())
                && dirtyIdentity.loadGeneration() == saveIdentity.loadGeneration()
                && dirtyIdentity.mutationGeneration() == saveIdentity.mutationGeneration();
    }

    private void drainAutosavesBeforeRemoval(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        if (discardingRequest(requestId)) {
            return;
        }
        if (!ownsEveryStudioChunk(studio)) {
            expediteAutosaves(requestId);
            return;
        }
        for (AutosaveTicket ticket : List.copyOf(autosaves.values())) {
            if (requestId.equals(ticket.key().requestId())) {
                drainAutosaveSynchronously(ticket);
            }
        }
    }

    /** Whether the calling thread may read every workcell chunk, which the final synchronous autosave needs. */
    private static boolean ownsEveryStudioChunk(ActiveStudio studio) {
        for (JigsawStudioBay bay : studio.generator().getLayout().bays()) {
            for (ChunkCaptureArea area : chunkIntersections(bay.bounds())) {
                if (!JigsawStudioThreads.ownsRegion(studio.world(), area.chunkX(), area.chunkZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean drainAutosaveSynchronously(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return true;
        }
        ActiveStudio studio = ticket.studio();
        JigsawStudioSession session = studio.generator().getSession();
        if (!isCurrentRequest(studio, ticket.key().requestId())) {
            autosaves.remove(ticket.key(), ticket);
            return true;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            return false;
        }
        if (!session.isDirtyCurrent(ticket.identity())) {
            autosaves.remove(ticket.key(), ticket);
            return true;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            return false;
        }
        BayReadiness readiness = studio.population(bay).readiness();
        if (!readiness.ready()) {
            return false;
        }
        CaptureTarget captureTarget;
        try {
            captureTarget = resolveCaptureTarget(studio, bay, activeVariant);
        } catch (IOException exception) {
            retainPersistentAutosaveFailure(
                    ticket,
                    new AutosavePersistentFailure(
                            "Jigsaw Studio cannot capture this workcell: " + failureMessage(exception),
                            exception));
            return false;
        }
        JigsawStudioSession.SaveStart sessionSave = session.beginSave(bay.stableId());
        if (sessionSave.status() != JigsawStudioSession.SaveStatus.STARTED) {
            return false;
        }
        JigsawStudioSession.SaveIdentity saveIdentity = sessionSave.identity().orElseThrow();
        if (tryBeginSave(ticket.key().requestId()) != SaveStart.STARTED) {
            session.abortSave(saveIdentity);
            return false;
        }
        List<ChunkCaptureArea> areas = chunkIntersections(captureTarget.bounds());
        CaptureWork work = new CaptureWork(
                studio,
                studio.world(),
                bay,
                captureTarget,
                null,
                ticket.key().requestId(),
                saveIdentity,
                ticket.failureState());
        CaptureCoordinator coordinator = new CaptureCoordinator(work, areas);
        List<ChunkSnapshot> snapshots = new ArrayList<>(areas.size());
        try {
            for (ChunkCaptureArea area : areas) {
                if (!studio.isChunkLoaded(area.chunkX(), area.chunkZ())) {
                    throw new IOException("Workcell chunk " + area.chunkX() + "," + area.chunkZ()
                            + " is not loaded during the final autosave drain");
                }
                snapshots.add(captureChunkIntersection(
                        studio.world(),
                        studio.generator().getPack().resources().blocks(),
                        captureTarget.bounds(),
                        captureTarget.piece(),
                        captureTarget.object(),
                        area,
                        captureTarget.displayRotationQuarterTurns(),
                        captureTarget.connectorsVisible()));
            }
            assembleAndPersist(coordinator, snapshots);
        } catch (Throwable exception) {
            coordinator.fail("Jigsaw Studio final autosave drain failed: "
                    + failureMessage(exception), exception);
        }
        return !session.isDirtyCurrent(ticket.identity());
    }

    private void expediteAutosaves(UUID requestId) {
        if (requestId == null) {
            return;
        }
        for (AutosaveTicket current : List.copyOf(autosaves.values())) {
            if (!requestId.equals(current.key().requestId())) {
                continue;
            }
            expediteAutosave(current, 0);
        }
    }

    private void expediteAutosave(AutosaveTicket current, int delayTicks) {
        AutosaveTicket expedited = new AutosaveTicket(
                current.key(),
                current.studio(),
                current.identity(),
                new AtomicBoolean(false),
                current.scheduleFailureLogged(),
                current.failureState());
        if (autosaves.replace(current.key(), current, expedited)) {
            scheduleAutosave(expedited, delayTicks);
        }
    }

    private boolean requiresLifecycleDrain(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        if (hasJigsawTileWatch(requestId)
                || studio.generator().getSession().isDirty()
                || studio.generator().getSession().operationInProgress()) {
            return true;
        }
        for (AutosaveKey key : autosaves.keySet()) {
            if (requestId.equals(key.requestId())) {
                return true;
            }
        }
        synchronized (saveLifecycleLock) {
            return savesInProgress.contains(requestId)
                    || graphMutationsInProgress.contains(requestId)
                    || materializationsInProgress.contains(requestId)
                    || exportsInProgress.contains(requestId);
        }
    }

    private boolean discardingRequest(UUID requestId) {
        synchronized (saveLifecycleLock) {
            return discardingRequests.contains(requestId);
        }
    }

    private void clearAutosaves(UUID requestId) {
        if (requestId != null) {
            autosaves.keySet().removeIf(key -> key.requestId().equals(requestId));
        }
    }

    private void markEvaluationStale(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        evaluations.computeIfPresent(
                requestId,
                (ignored, current) -> current.state() == JigsawStudioEvaluationState.STALE
                        ? current
                        : current.stale("Autosave is pending for one or more edited workcells"));
    }

    static boolean isMutatingCommand(String commandLine) {
        return MUTATING_COMMANDS.contains(commandLabel(commandLine));
    }

    static boolean blocksMutatingCommand(UUID ownerId, UUID actorId, String commandLine) {
        return !ownerMatches(ownerId, actorId)
                && !isSafeNonOwnerCommand(commandLine);
    }

    static boolean blocksNonEditableWorkcellMutation(
            boolean hasNonEditableWorkcell,
            String commandLine
    ) {
        return hasNonEditableWorkcell && isMutatingCommand(commandLine);
    }

    static boolean canToggleVariantRotation(
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioVariant variant
    ) {
        JigsawStudioCompatibilityTarget target = Objects.requireNonNull(
                compatibilityTarget,
                "Jigsaw Studio compatibility target");
        JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio variant");
        return activeVariant.owned()
                && (target != JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                || !activeVariant.rotatable());
    }

    static boolean isSafeNonOwnerCommand(String commandLine) {
        String normalized = normalizeCommandLine(commandLine);
        String label = commandLabel(normalized);
        if (SAFE_NON_OWNER_COMMANDS.contains(label)) {
            return true;
        }
        int separator = normalized.indexOf(' ');
        if (!label.equals("iris") || separator < 0) {
            return false;
        }
        String arguments = normalized.substring(separator + 1).trim();
        return arguments.equals("jigsaw status") || arguments.startsWith("jigsaw status ");
    }

    private static String commandLabel(String commandLine) {
        String normalized = normalizeCommandLine(commandLine);
        int separator = normalized.indexOf(' ');
        String label = separator < 0 ? normalized : normalized.substring(0, separator);
        int namespace = label.lastIndexOf(':');
        return namespace < 0 ? label : label.substring(namespace + 1);
    }

    private static String normalizeCommandLine(String commandLine) {
        if (commandLine == null) {
            return "";
        }
        String normalized = commandLine.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private void captureChunk(CaptureCoordinator coordinator, ChunkCaptureArea area) {
        if (coordinator.stopped()) {
            return;
        }
        ActiveStudio studio = coordinator.studio();
        ToolWorld world = coordinator.world();
        if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
            coordinator.fail("Jigsaw Studio changed before every bay chunk could be captured; save cancelled.", null);
            return;
        }
        if (!studio.isChunkLoaded(area.chunkX(), area.chunkZ())) {
            coordinator.fail("Bay chunk " + area.chunkX() + "," + area.chunkZ()
                    + " is not loaded. Visit the whole bay and try again.", null);
            return;
        }
        try {
            ChunkSnapshot snapshot = captureChunkIntersection(
                    world,
                    studio.generator().getPack().resources().blocks(),
                    coordinator.captureTarget().bounds(),
                    coordinator.captureTarget().piece(),
                    coordinator.captureTarget().object(),
                    area,
                    coordinator.captureTarget().displayRotationQuarterTurns(),
                    coordinator.captureTarget().connectorsVisible());
            if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                coordinator.fail("Jigsaw Studio changed while bay chunks were being captured; save cancelled.", null);
                return;
            }
            coordinator.accept(snapshot);
        } catch (Throwable exception) {
            coordinator.fail("Jigsaw Studio capture failed in chunk "
                    + area.chunkX() + "," + area.chunkZ() + ": " + failureMessage(exception), exception);
        }
    }

    private void assembleAndPersist(CaptureCoordinator coordinator, List<ChunkSnapshot> snapshots) {
        if (coordinator.failed()) {
            return;
        }
        ActiveStudio studio = coordinator.studio();
        if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
            coordinator.fail("Jigsaw Studio changed before captured chunks could be assembled; save cancelled.", null);
            return;
        }
        try {
            ToolPack pack = studio.generator().getPack();
            Capture capture = aggregateSnapshots(
                    pack.resources().blocks(),
                    pack.objects(),
                    coordinator.captureTarget().bounds(),
                    coordinator.areas(),
                    snapshots,
                    coordinator.captureTarget().displayRotationQuarterTurns());
            List<IrisJigsawConnector> connectors = preserveCapturedConnectorOrder(
                    coordinator.captureTarget().piece(),
                    capture.connectors());
            requireWorkcellTopology(
                    coordinator.bay(),
                    connectors,
                    coordinator.captureTarget().displayRotationQuarterTurns());
            if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                coordinator.fail("Jigsaw Studio changed before the captured bay could be written; save cancelled.", null);
                return;
            }
            message(coordinator.player(), "Captured " + connectors.size()
                    + " connector(s) from " + snapshots.size()
                    + " chunk(s); validating and writing the owned structure graph...");
            persistCapture(coordinator, capture, connectors);
        } catch (WorkcellTopologyException exception) {
            coordinator.failPersistent(
                    "Jigsaw Studio cannot autosave this planar workcell: "
                            + failureMessage(exception),
                    null);
        } catch (Throwable exception) {
            coordinator.failPersistent(
                    "Jigsaw Studio capture assembly failed: " + failureMessage(exception),
                    exception);
        }
    }

    private void persistCapture(
            CaptureCoordinator coordinator,
            Capture capture,
            List<IrisJigsawConnector> connectors
    ) {
        ActiveStudio studio = coordinator.studio();
        JigsawStudioBay bay = coordinator.bay();
        ToolPlayer player = coordinator.player();
        try {
            if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                message(player, "Jigsaw Studio changed before the captured bay could be written; save cancelled.");
                return;
            }
            JigsawStudioRequest request = studio.generator().getRequest();
            PersistedGraph persisted = studio.generator().getPack().edit(
                    packRoot -> writeCapturedGraph(coordinator, capture, connectors, packRoot));
            if (persisted == null) {
                return;
            }
            JigsawStudioResourceBundleAssembler.Assembly assembly = persisted.assembly();
            StructureWriteResult result = persisted.result();
            if (!result.successful()) {
                String failure = writeFailure(result);
                coordinator.recordPersistentFailure(failure, result.failure().orElse(null));
                message(player, failure);
                return;
            }
            studio.generator().invalidateRender(bay.stableId(), coordinator.saveIdentity().variantKey());
            JigsawStudioSession session = studio.generator().getSession();
            boolean unchanged = session.markWorkcellSaved(coordinator.saveIdentity());
            try {
                reloadSessionLayout(studio);
            } catch (IOException | RuntimeException refreshFailure) {
                reportError(refreshFailure);
                message(player, "The graph saved, but Studio could not refresh its variant catalog. "
                        + "Close and reopen this project before continuing: "
                        + failureMessage(refreshFailure));
            }
            String cleanup = result.status() == StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED
                    ? " The graph committed, but transaction cleanup requires operator attention in the console."
                    : "";
            String mutationNotice = unchanged ? "" : " Newer edits remain unsaved.";
            message(player, "Saved piece '" + coordinator.saveIdentity().variantKey() + "' and object '"
                    + assembly.objectKey() + "' atomically." + mutationNotice + cleanup);
            playSaveSound(player);
            if (unchanged) {
                scheduleEvaluation(studio);
            }
            debug("Jigsaw Studio saved: structure=%s piece=%s object=%s connectors=%d status=%s",
                    request.structureKey(), coordinator.saveIdentity().variantKey(), assembly.objectKey(),
                    connectors.size(), result.status());
        } catch (Throwable exception) {
            String failure = "Jigsaw Studio save failed: " + failureMessage(exception);
            coordinator.recordPersistentFailure(failure, exception);
            message(player, failure);
        } finally {
            coordinator.complete();
        }
    }

    /**
     * Validates the captured workcell against the graph and writes it under the pack folder; null when the studio changed
     * on the way and the save was dropped, in which case the player has been told.
     */
    private PersistedGraph writeCapturedGraph(
            CaptureCoordinator coordinator,
            Capture capture,
            List<IrisJigsawConnector> connectors,
            Path packRoot
    ) throws IOException {
        ActiveStudio studio = coordinator.studio();
        ToolPlayer player = coordinator.player();
        JigsawStudioRequest request = studio.generator().getRequest();
        JigsawStudioResourceBundleAssembler.Assembly assembly =
                JigsawStudioResourceBundleAssembler.assemble(
                        packRoot,
                        request.structureKey(),
                        coordinator.saveIdentity().variantKey(),
                        capture.objectContent(),
                        connectors,
                        capture.hasBlockEntities());
        StructureResourceBundleGraphCompiler.requireViable(assembly.bundle());
        if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
            message(player, "Jigsaw Studio changed during validation; save cancelled before writing.");
            return null;
        }
        JigsawStudioHistoryStore historyStore = new JigsawStudioHistoryStore(
                packRoot,
                request.structureKey());
        JigsawStudioHistoryStore.Snapshot previous = historyStore.snapshotCurrent(
                coordinator.saveIdentity().variantKey());
        if (!previous.matches(assembly.bundle())) {
            historyStore.append(previous);
        }
        synchronized (saveLifecycleLock) {
            if (!isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                message(player, "Jigsaw Studio changed before the validated save entered the writer; save cancelled.");
                return null;
            }
            StructureWriteResult result = new StructureTransactionWriter(packRoot)
                    .write(assembly.bundle(), StructureWriteOptions.overwriteExpected(
                            assembly.expectedManifestHash()));
            return new PersistedGraph(assembly, result);
        }
    }

    private record PersistedGraph(
            JigsawStudioResourceBundleAssembler.Assembly assembly,
            StructureWriteResult result
    ) {
    }

    static List<ChunkCaptureArea> chunkIntersections(JigsawStudioBounds bounds) {
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        List<ChunkCaptureArea> areas = new ArrayList<>();
        int minimumChunkX = captureBounds.originX() >> 4;
        int maximumChunkX = captureBounds.maxX() >> 4;
        int minimumChunkZ = captureBounds.originZ() >> 4;
        int maximumChunkZ = captureBounds.maxZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            int chunkOriginX = chunkX << 4;
            int minimumX = Math.max(0, chunkOriginX - captureBounds.originX());
            int maximumX = Math.min(
                    captureBounds.dimensions().width(),
                    chunkOriginX + 16 - captureBounds.originX());
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                int chunkOriginZ = chunkZ << 4;
                int minimumZ = Math.max(0, chunkOriginZ - captureBounds.originZ());
                int maximumZ = Math.min(
                        captureBounds.dimensions().depth(),
                        chunkOriginZ + 16 - captureBounds.originZ());
                areas.add(new ChunkCaptureArea(
                        chunkX,
                        chunkZ,
                        minimumX,
                        maximumX,
                        minimumZ,
                        maximumZ));
            }
        }
        return List.copyOf(areas);
    }

    static ChunkSnapshot captureChunkIntersection(
            ToolWorld world,
            BlockRegistry registry,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            SourceObject sourceObject,
            ChunkCaptureArea area
    ) throws IOException {
        return captureChunkIntersection(world, registry, bounds, sourcePiece, sourceObject, area, 0);
    }

    static ChunkSnapshot captureChunkIntersection(
            ToolWorld world,
            BlockRegistry registry,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            SourceObject sourceObject,
            ChunkCaptureArea area,
            int displayRotationQuarterTurns
    ) throws IOException {
        return captureChunkIntersection(
                world,
                registry,
                bounds,
                sourcePiece,
                sourceObject,
                area,
                displayRotationQuarterTurns,
                true);
    }

    static ChunkSnapshot captureChunkIntersection(
            ToolWorld world,
            BlockRegistry registry,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            SourceObject sourceObject,
            ChunkCaptureArea area,
            int displayRotationQuarterTurns,
            boolean connectorsVisible
    ) throws IOException {
        ToolWorld captureWorld = Objects.requireNonNull(world, "Jigsaw Studio capture world");
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        SourceObject captureSource = Objects.requireNonNull(sourceObject, "Jigsaw Studio source object");
        ChunkCaptureArea captureArea = Objects.requireNonNull(area, "Jigsaw Studio chunk capture area");
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().width()
                : captureBounds.dimensions().depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().depth()
                : captureBounds.dimensions().width();
        List<CapturedBlock> blocks = new ArrayList<>();
        List<CapturedConnector> connectors = new ArrayList<>();
        Map<LocalPosition, IrisJigsawConnector> hiddenConnectors = connectorsVisible
                ? Map.of()
                : displayedSourceConnectors(sourcePiece, captureBounds.dimensions(), quarterTurns);
        for (int x = captureArea.minimumX(); x < captureArea.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = captureArea.minimumZ(); z < captureArea.maximumZ(); z++) {
                    BlockPosition at = new BlockPosition(
                            captureBounds.originX() + x,
                            captureBounds.originY() + y,
                            captureBounds.originZ() + z);
                    BlockState state = captureWorld.block(at);
                    IrisJigsawConnector hiddenConnector = hiddenConnectors.get(new LocalPosition(x, y, z));
                    if (hiddenConnector != null) {
                        hiddenConnector.setFinalState(state.key());
                        connectors.add(CapturedConnector.from(hiddenConnector));
                    }
                    if (hiddenConnector == null && JigsawStudioBlocks.isJigsaw(state)) {
                        KMap<String, Object> nbt = captureWorld.blockEntity(at)
                                .flatMap(JigsawStudioTileCodec::nbt)
                                .orElse(null);
                        if (nbt == null) {
                            throw new IOException("Cannot read jigsaw marker NBT at "
                                    + at.x() + "," + at.y() + "," + at.z()
                                    + ". The active NMS binding must support tile serialization.");
                        }
                        IrisJigsawConnector connector;
                        try {
                            connector = JigsawStudioMarkerParser.parse(nbt, orientationOf(state), x, y, z);
                        } catch (IllegalArgumentException exception) {
                            throw new IOException("Invalid jigsaw marker at "
                                    + at.x() + "," + at.y() + "," + at.z()
                                    + ": " + failureMessage(exception), exception);
                        }
                        restoreCapturedMetadataForDisplay(
                                connector,
                                sourcePiece,
                                captureBounds.dimensions(),
                                quarterTurns);
                        BlockState finalState = registry.find(connector.getFinalState())
                                .orElseThrow(() -> new IOException("Invalid final_state '" + connector.getFinalState()
                                        + "' at " + at.x() + "," + at.y() + "," + at.z()));
                        connector.setFinalState(finalState.key());
                        connectors.add(CapturedConnector.from(connector));
                        if (!JigsawStudioBlocks.isStructureVoid(finalState)) {
                            blocks.add(new CapturedBlock(x, y, z, finalState, null));
                        }
                        continue;
                    }
                    if (JigsawStudioBlocks.isAir(state)) {
                        LocalPosition sourcePosition = inversePosition(
                                x,
                                y,
                                z,
                                sourceWidth,
                                sourceDepth,
                                quarterTurns);
                        BlockState retainedAir = retainedSourceAir(
                                captureSource,
                                sourcePosition.x(),
                                sourcePosition.y(),
                                sourcePosition.z(),
                                state);
                        if (retainedAir != null) {
                            blocks.add(new CapturedBlock(x, y, z, retainedAir, null));
                        }
                        continue;
                    }
                    if (JigsawStudioBlocks.isStructureVoid(state)) {
                        continue;
                    }
                    blocks.add(new CapturedBlock(x, y, z, state, captureWorld.blockEntity(at).orElse(null)));
                }
            }
        }
        return new ChunkSnapshot(captureArea, blocks, connectors);
    }

    private static Map<LocalPosition, IrisJigsawConnector> displayedSourceConnectors(
            IrisJigsawPiece sourcePiece,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns
    ) throws IOException {
        IrisJigsawPiece source = Objects.requireNonNull(sourcePiece, "Jigsaw Studio source piece");
        if (source.getConnectors() == null) {
            throw new IOException("Jigsaw Studio source piece has no connector list");
        }
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? displayDimensions.width()
                : displayDimensions.depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? displayDimensions.depth()
                : displayDimensions.width();
        PieceTurn rotation = PieceTurn.aroundY(-90 * quarterTurns);
        Map<LocalPosition, IrisJigsawConnector> displayed = new HashMap<>(source.getConnectors().size());
        for (IrisJigsawConnector sourceConnector : source.getConnectors()) {
            LocalPosition sourcePosition = connectorPosition(sourceConnector, "source piece");
            LocalPosition position = forwardPosition(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    sourceWidth,
                    sourceDepth,
                    quarterTurns);
            IrisJigsawConnector connector = CapturedConnector.from(sourceConnector).toConnector()
                    .setPosition(new BlockPosition(position.x(), position.y(), position.z()))
                    .setDirection(sourceConnector.getDirection().turnedBy(rotation.quarterTurns()))
                    .setTop(sourceConnector.getTop().turnedBy(rotation.quarterTurns()));
            if (displayed.put(position, connector) != null) {
                throw new IOException("Jigsaw Studio source piece has duplicate displayed connector position "
                        + connectorLocation(position));
            }
        }
        return Map.copyOf(displayed);
    }

    static BlockState retainedSourceAir(
            SourceObject sourceObject,
            int x,
            int y,
            int z,
            BlockState current
    ) {
        SourceObject source = Objects.requireNonNull(sourceObject, "Jigsaw Studio source object");
        BlockState currentState = Objects.requireNonNull(current, "Jigsaw Studio current block state");
        if (!JigsawStudioBlocks.isAir(currentState)
                || x < 0
                || y < 0
                || z < 0
                || x >= source.width()
                || y >= source.height()
                || z >= source.depth()) {
            return null;
        }
        return source.airBlocks().get(new LocalPosition(x, y, z));
    }

    /**
     * The object a piece was saved as: its declared size, and the air it keeps deliberately. A workcell block that is air
     * now keeps the air it had in the object, so the saved object still clears the same blocks when it is placed.
     */
    record SourceObject(int width, int height, int depth, Map<LocalPosition, BlockState> airBlocks) {
        SourceObject {
            airBlocks = Map.copyOf(airBlocks);
        }

        static SourceObject of(PlaceableObject object) {
            BlockPosition center = object.center();
            Map<LocalPosition, BlockState> air = new HashMap<>();
            for (ObjectBlock block : object.geometry().blocks()) {
                if (JigsawStudioBlocks.isAir(block.state())) {
                    air.put(new LocalPosition(
                            block.position().x() + center.x(),
                            block.position().y() + center.y(),
                            block.position().z() + center.z()), block.state());
                }
            }
            return new SourceObject(object.width(), object.height(), object.depth(), air);
        }
    }

    static Capture aggregateSnapshots(
            BlockRegistry registry,
            ObjectCodec codec,
            JigsawStudioBounds bounds,
            List<ChunkCaptureArea> expectedAreas,
            List<ChunkSnapshot> snapshots
    ) throws IOException {
        return aggregateSnapshots(registry, codec, bounds, expectedAreas, snapshots, 0);
    }

    static Capture aggregateSnapshots(
            BlockRegistry registry,
            ObjectCodec codec,
            JigsawStudioBounds bounds,
            List<ChunkCaptureArea> expectedAreas,
            List<ChunkSnapshot> snapshots,
            int displayRotationQuarterTurns
    ) throws IOException {
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        List<ChunkCaptureArea> requiredAreas = List.copyOf(expectedAreas);
        List<ChunkSnapshot> capturedSnapshots = List.copyOf(snapshots);
        Map<Long, ChunkCaptureArea> requiredByChunk = new HashMap<>();
        for (ChunkCaptureArea area : requiredAreas) {
            if (requiredByChunk.put(chunkKey(area.chunkX(), area.chunkZ()), area) != null) {
                throw new IOException("Duplicate required bay chunk " + area.chunkX() + "," + area.chunkZ());
            }
        }
        if (capturedSnapshots.size() != requiredByChunk.size()) {
            throw new IOException("Expected " + requiredByChunk.size() + " bay chunk snapshot(s), received "
                    + capturedSnapshots.size());
        }
        Map<Long, ChunkSnapshot> capturedByChunk = new HashMap<>();
        for (ChunkSnapshot snapshot : capturedSnapshots) {
            ChunkCaptureArea area = snapshot.area();
            long key = chunkKey(area.chunkX(), area.chunkZ());
            ChunkCaptureArea required = requiredByChunk.get(key);
            if (!area.equals(required)) {
                throw new IOException("Unexpected bay chunk intersection for "
                        + area.chunkX() + "," + area.chunkZ());
            }
            if (capturedByChunk.put(key, snapshot) != null) {
                throw new IOException("Duplicate bay chunk snapshot " + area.chunkX() + "," + area.chunkZ());
            }
        }
        if (!capturedByChunk.keySet().containsAll(requiredByChunk.keySet())) {
            throw new IOException("One or more required bay chunk snapshots are missing");
        }

        List<ChunkSnapshot> ordered = new ArrayList<>(capturedSnapshots);
        ordered.sort(Comparator
                .comparingInt((ChunkSnapshot snapshot) -> snapshot.area().chunkX())
                .thenComparingInt(snapshot -> snapshot.area().chunkZ()));
        int sourceWidth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().width()
                : captureBounds.dimensions().depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().depth()
                : captureBounds.dimensions().width();
        int sourceHeight = captureBounds.dimensions().height();
        List<ObjectBlock> objectBlocks = new ArrayList<>();
        PieceTurn inverseRotation = PieceTurn.aroundY(90 * quarterTurns);
        List<IrisJigsawConnector> connectors = new ArrayList<>();
        Set<LocalPosition> capturedBlocks = new HashSet<>();
        Set<LocalPosition> capturedConnectors = new HashSet<>();
        boolean hasBlockEntities = false;
        for (ChunkSnapshot snapshot : ordered) {
            ChunkCaptureArea area = snapshot.area();
            for (CapturedBlock block : snapshot.blocks()) {
                if (!area.contains(block.x(), block.z())
                        || block.y() < 0
                        || block.y() >= captureBounds.dimensions().height()) {
                    throw new IOException("Captured block falls outside bay chunk "
                            + area.chunkX() + "," + area.chunkZ());
                }
                LocalPosition position = inversePosition(
                        block.x(), block.y(), block.z(), sourceWidth, sourceDepth, quarterTurns);
                if (!capturedBlocks.add(position)) {
                    throw new IOException("Duplicate captured block at "
                            + block.x() + "," + block.y() + "," + block.z());
                }
                BlockState sourceState = quarterTurns == 0
                        ? block.state()
                        : JigsawStudioBlocks.rotated(registry, block.state(), inverseRotation);
                if (sourceState == null) {
                    throw new IOException("Captured block state cannot be inverse-rotated at "
                            + block.x() + "," + block.y() + "," + block.z());
                }
                objectBlocks.add(new ObjectBlock(
                        fromCorner(position, sourceWidth, sourceHeight, sourceDepth),
                        sourceState,
                        Optional.ofNullable(block.blockEntity())));
                if (block.blockEntity() != null) {
                    hasBlockEntities = true;
                }
            }
            for (CapturedConnector capturedConnector : snapshot.connectors()) {
                if (!area.contains(capturedConnector.x(), capturedConnector.z())
                        || capturedConnector.y() < 0
                        || capturedConnector.y() >= captureBounds.dimensions().height()) {
                    throw new IOException("Captured connector falls outside bay chunk "
                            + area.chunkX() + "," + area.chunkZ());
                }
                LocalPosition position = inversePosition(
                        capturedConnector.x(),
                        capturedConnector.y(),
                        capturedConnector.z(),
                        sourceWidth,
                        sourceDepth,
                        quarterTurns);
                if (!capturedConnectors.add(position)) {
                    throw new IOException("Duplicate captured connector at "
                            + capturedConnector.x() + "," + capturedConnector.y() + ","
                            + capturedConnector.z());
                }
                IrisJigsawConnector connector = capturedConnector.toConnector();
                if (quarterTurns != 0) {
                    connector.setPosition(new BlockPosition(position.x(), position.y(), position.z()));
                    connector.setDirection(connector.getDirection().turnedBy(inverseRotation.quarterTurns()));
                    connector.setTop(connector.getTop().turnedBy(inverseRotation.quarterTurns()));
                    BlockState finalState = registry.find(connector.getFinalState()).orElse(null);
                    if (finalState == null) {
                        throw new IOException("Captured connector final state cannot be parsed at "
                                + capturedConnector.x() + "," + capturedConnector.y() + ","
                                + capturedConnector.z());
                    }
                    BlockState sourceFinalState = JigsawStudioBlocks.rotated(registry, finalState, inverseRotation);
                    if (sourceFinalState == null) {
                        throw new IOException("Captured connector final state cannot be inverse-rotated at "
                                + capturedConnector.x() + "," + capturedConnector.y() + ","
                                + capturedConnector.z());
                    }
                    connector.setFinalState(sourceFinalState.key());
                }
                connectors.add(connector);
            }
        }
        GeneratedObject object = new GeneratedObject(
                objectBounds(sourceWidth, sourceHeight, sourceDepth),
                new BlockPosition(0, 0, 0),
                objectBlocks,
                List.of());
        return new Capture(codec.encode(object), connectors, hasBlockEntities);
    }

    /** A position counted from the object's corner, as the offset from its center the object format keeps. */
    private static BlockPosition fromCorner(LocalPosition corner, int width, int height, int depth) {
        return new BlockPosition(
                corner.x() - width / 2,
                corner.y() - height / 2,
                corner.z() - depth / 2);
    }

    /** The box of an object of the given size, in the offsets from its center that its blocks are kept in. */
    private static BlockBox objectBounds(int width, int height, int depth) {
        return new BlockBox(
                -(width / 2),
                -(height / 2),
                -(depth / 2),
                width - width / 2,
                height - height / 2,
                depth - depth / 2);
    }

    static List<IrisJigsawConnector> preserveCapturedConnectorOrder(
            IrisJigsawPiece sourcePiece,
            List<IrisJigsawConnector> capturedConnectors
    ) throws IOException {
        IrisJigsawPiece source = Objects.requireNonNull(sourcePiece, "Jigsaw Studio source piece");
        List<IrisJigsawConnector> sourceConnectors = source.getConnectors();
        if (sourceConnectors == null) {
            throw new IOException("Jigsaw Studio source piece has no connector list");
        }
        List<IrisJigsawConnector> captured = new ArrayList<>(Objects.requireNonNull(
                capturedConnectors,
                "Jigsaw Studio captured connectors"));
        Map<LocalPosition, IrisJigsawConnector> capturedByPosition = new HashMap<>(captured.size());
        for (IrisJigsawConnector connector : captured) {
            LocalPosition position = connectorPosition(connector, "captured");
            if (capturedByPosition.put(position, connector) != null) {
                throw new IOException("Jigsaw Studio capture has duplicate connector position "
                        + connectorLocation(position));
            }
        }

        List<IrisJigsawConnector> ordered = new ArrayList<>(captured.size());
        Set<LocalPosition> sourcePositions = new HashSet<>(sourceConnectors.size());
        for (IrisJigsawConnector connector : sourceConnectors) {
            LocalPosition position = connectorPosition(connector, "source piece");
            if (!sourcePositions.add(position)) {
                throw new IOException("Jigsaw Studio source piece has duplicate connector position "
                        + connectorLocation(position));
            }
            IrisJigsawConnector matched = capturedByPosition.remove(position);
            if (matched != null) {
                ordered.add(matched);
            }
        }

        List<IrisJigsawConnector> appended = new ArrayList<>(capturedByPosition.values());
        appended.sort(NEW_CONNECTOR_SOURCE_POSITION_ORDER);
        ordered.addAll(appended);
        return List.copyOf(ordered);
    }

    private static LocalPosition connectorPosition(IrisJigsawConnector connector, String owner) throws IOException {
        if (connector == null || connector.getPosition() == null) {
            throw new IOException("Jigsaw Studio " + owner + " contains a connector without a position");
        }
        BlockPosition position = connector.getPosition();
        return new LocalPosition(position.x(), position.y(), position.z());
    }

    private static String connectorLocation(LocalPosition position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    private static LocalPosition inversePosition(
            int x,
            int y,
            int z,
            int sourceWidth,
            int sourceDepth,
            int displayRotationQuarterTurns
    ) {
        return switch (Math.floorMod(displayRotationQuarterTurns, 4)) {
            case 0 -> new LocalPosition(x, y, z);
            case 1 -> new LocalPosition(z, y, sourceDepth - 1 - x);
            case 2 -> new LocalPosition(sourceWidth - 1 - x, y, sourceDepth - 1 - z);
            case 3 -> new LocalPosition(sourceWidth - 1 - z, y, x);
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio inverse rotation");
        };
    }

    private static LocalPosition forwardPosition(
            int x,
            int y,
            int z,
            int sourceWidth,
            int sourceDepth,
            int displayRotationQuarterTurns
    ) {
        return switch (Math.floorMod(displayRotationQuarterTurns, 4)) {
            case 0 -> new LocalPosition(x, y, z);
            case 1 -> new LocalPosition(sourceDepth - 1 - z, y, x);
            case 2 -> new LocalPosition(sourceWidth - 1 - x, y, sourceDepth - 1 - z);
            case 3 -> new LocalPosition(z, y, sourceWidth - 1 - x);
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio display rotation");
        };
    }

    static void requireWorkcellTopology(
            JigsawStudioBay workcell,
            List<IrisJigsawConnector> connectors,
            int displayRotationQuarterTurns
    ) throws IOException {
        JigsawPlanarArchetype expected = workcell.archetype().orElse(null);
        if (expected == null) {
            return;
        }
        int mask = 0;
        for (IrisJigsawConnector connector : connectors) {
            mask |= switch (connector.getDirection()) {
                case NORTH_NEGATIVE_Z -> JigsawPlanarDirection.NORTH.bit();
                case EAST_POSITIVE_X -> JigsawPlanarDirection.EAST.bit();
                case SOUTH_POSITIVE_Z -> JigsawPlanarDirection.SOUTH.bit();
                case WEST_NEGATIVE_X -> JigsawPlanarDirection.WEST.bit();
                case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new WorkcellTopologyException(
                        "Planar workcell '" + workcell.stableId()
                                + "' cannot save a vertical connector. Remove it or use Reset Connector Blocks.");
            };
        }
        JigsawPlanarTopology sourceTopology = JigsawPlanarTopology.fromMask(mask);
        JigsawPlanarTopology displayedTopology = sourceTopology.rotateClockwise(displayRotationQuarterTurns);
        if (displayedTopology != expected.canonicalTopology()) {
            throw new WorkcellTopologyException("Workcell '" + workcell.stableId() + "' requires "
                    + topologyDescription(expected.canonicalTopology())
                    + ", but the edited markers form "
                    + topologyDescription(displayedTopology)
                    + ". Use Reset Connector Blocks to restore the saved topology, or edit the markers to match the floor glyph.");
        }
    }

    private static String topologyDescription(JigsawPlanarTopology topology) {
        int connectorCount = topology.directions().size();
        return topology.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " (" + connectorCount + " horizontal connector"
                + (connectorCount == 1 ? "" : "s") + ")";
    }


    private void scheduleHydration(
            ActiveStudio studio,
            ToolWorld world,
            int chunkX,
            int chunkZ,
            int attempt
    ) {
        if (studios.get(studio.worldId()) != studio) {
            return;
        }
        long key = chunkKey(chunkX, chunkZ);
        if (!studio.hydrationsInProgress().add(key)) {
            return;
        }
        scheduler.onRegionLater(
                world,
                chunkX,
                chunkZ,
                () -> runHydrationAttempt(studio, world, chunkX, chunkZ, attempt, key),
                HYDRATION_RETRY_TICKS,
                () -> studio.hydrationsInProgress().remove(key));
    }

    private void runHydrationAttempt(
            ActiveStudio studio,
            ToolWorld world,
            int chunkX,
            int chunkZ,
            int attempt,
            long key
    ) {
        studio.hydrationsInProgress().remove(key);
        if (studios.get(studio.worldId()) != studio) {
            return;
        }
        if (!studio.isChunkLoaded(chunkX, chunkZ)) {
            if (attempt < MAX_HYDRATION_ATTEMPTS) {
                scheduleHydration(studio, world, chunkX, chunkZ, attempt + 1);
            }
            return;
        }
        if (hydrateChunk(studio, world, chunkX, chunkZ)) {
            scheduleHydration(studio, world, chunkX, chunkZ, 0);
        }
    }

    private boolean hydrateChunk(
            ActiveStudio studio,
            ToolWorld world,
            int chunkX,
            int chunkZ
    ) {
        long key = chunkKey(chunkX, chunkZ);
        boolean verificationRequired = false;
        for (JigsawStudioBay bay : studio.generator().getLayout().bays()) {
            BayPopulation population = studio.population(bay);
            if (!population.needsApplication(key) && !population.needsVerification(key)) {
                continue;
            }
            JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
            if (!rendered.valid()) {
                population.fail(rendered.failure());
                continue;
            }
            try {
                if (population.needsApplication(key)) {
                    applyRenderedBayChunk(
                            world,
                            bay,
                            rendered,
                            chunkX,
                            chunkZ,
                            studio.generator().getSession().workcellSnapshot(bay.stableId()).connectorsVisible());
                    population.markApplied(key);
                    verificationRequired = true;
                } else {
                    verifyRenderedBayChunk(
                            world,
                            bay,
                            rendered,
                            chunkX,
                            chunkZ,
                            studio.generator().getSession().workcellSnapshot(bay.stableId()).connectorsVisible());
                    population.markHydrated(key);
                }
            } catch (Throwable exception) {
                if (population.fail("chunk " + chunkX + "," + chunkZ
                        + " could not hydrate: " + failureMessage(exception))) {
                    reportError(exception);
                }
            }
        }
        return verificationRequired;
    }

    private void applyRenderedBayChunk(
            ToolWorld world,
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered,
            int chunkX,
            int chunkZ,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = bay.bounds();
        Set<LocalPosition> connectorPositions = connectorPositions(rendered, connectorsVisible);
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            BlockEntityData blockEntity = block.blockEntity();
            if (blockEntity == null
                    || connectorPositions.contains(new LocalPosition(block.x(), block.y(), block.z()))) {
                continue;
            }
            int worldX = bounds.originX() + block.x();
            int worldZ = bounds.originZ() + block.z();
            if ((worldX >> CHUNK_SHIFT) != chunkX || (worldZ >> CHUNK_SHIFT) != chunkZ) {
                continue;
            }
            BlockPosition target = new BlockPosition(worldX, bounds.originY() + block.y(), worldZ);
            if (!sameMaterial(world.block(target), block.state())) {
                throw new IOException("source tile state does not match rendered block at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
            try {
                world.setBlock(target, world.block(target), Optional.of(blockEntity));
            } catch (IllegalArgumentException exception) {
                throw new IOException("source tile state could not hydrate at "
                        + target.x() + "," + target.y() + "," + target.z(), exception);
            }
        }
        if (!connectorsVisible) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            int worldX = bounds.originX() + renderedConnector.x();
            int worldZ = bounds.originZ() + renderedConnector.z();
            if ((worldX >> CHUNK_SHIFT) != chunkX || (worldZ >> CHUNK_SHIFT) != chunkZ) {
                continue;
            }
            BlockPosition target = new BlockPosition(worldX, bounds.originY() + renderedConnector.y(), worldZ);
            BlockState actual = world.block(target);
            if (!JigsawStudioBlocks.isJigsaw(actual)) {
                throw new IOException("expected a jigsaw marker at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
            String actualOrientation = orientationOf(actual);
            if (!actualOrientation.equals(renderedConnector.orientation())) {
                throw new IOException("jigsaw marker orientation at "
                        + target.x() + "," + target.y() + "," + target.z()
                        + " is " + actualOrientation + " instead of " + renderedConnector.orientation());
            }
            world.setBlock(target, actual, Optional.of(markerBlockEntity(renderedConnector.connector())));
        }
    }

    private void verifyRenderedBayChunk(
            ToolWorld world,
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered,
            int chunkX,
            int chunkZ,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = bay.bounds();
        Set<LocalPosition> connectorPositions = connectorPositions(rendered, connectorsVisible);
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            BlockEntityData expectedEntity = block.blockEntity();
            if (expectedEntity == null
                    || connectorPositions.contains(new LocalPosition(block.x(), block.y(), block.z()))) {
                continue;
            }
            int worldX = bounds.originX() + block.x();
            int worldZ = bounds.originZ() + block.z();
            if ((worldX >> CHUNK_SHIFT) != chunkX || (worldZ >> CHUNK_SHIFT) != chunkZ) {
                continue;
            }
            BlockPosition target = new BlockPosition(worldX, bounds.originY() + block.y(), worldZ);
            if (!sameMaterial(world.block(target), block.state())) {
                throw new IOException("hydrated tile state no longer matches its block at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
            Optional<KMap<String, Object>> hydrated = world.blockEntity(target)
                    .flatMap(JigsawStudioTileCodec::nbt);
            Optional<KMap<String, Object>> expected = JigsawStudioTileCodec.nbt(expectedEntity);
            if (hydrated.isEmpty() || expected.isPresent() && !nbtContains(expected.get(), hydrated.get())) {
                throw new IOException("the active NMS binding did not preserve source tile NBT at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
        }
        if (!connectorsVisible) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            int worldX = bounds.originX() + renderedConnector.x();
            int worldZ = bounds.originZ() + renderedConnector.z();
            if ((worldX >> CHUNK_SHIFT) != chunkX || (worldZ >> CHUNK_SHIFT) != chunkZ) {
                continue;
            }
            BlockPosition target = new BlockPosition(worldX, bounds.originY() + renderedConnector.y(), worldZ);
            BlockState actual = world.block(target);
            if (!JigsawStudioBlocks.isJigsaw(actual)
                    || !orientationOf(actual).equals(renderedConnector.orientation())) {
                throw new IOException("jigsaw marker changed before hydration completed at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
            KMap<String, Object> expected = markerNbt(renderedConnector.connector());
            Optional<KMap<String, Object>> hydrated = world.blockEntity(target).flatMap(JigsawStudioTileCodec::nbt);
            if (hydrated.isEmpty() || !markerNbtMatches(expected, hydrated.get())) {
                throw new IOException("the active NMS binding did not preserve jigsaw marker NBT at "
                        + target.x() + "," + target.y() + "," + target.z());
            }
        }
    }

    private static Set<LocalPosition> connectorPositions(
            JigsawStudioGenerator.RenderedBay rendered,
            boolean connectorsVisible
    ) {
        Set<LocalPosition> positions = new HashSet<>(rendered.connectors().size());
        if (connectorsVisible) {
            for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
                positions.add(new LocalPosition(connector.x(), connector.y(), connector.z()));
            }
        }
        return positions;
    }

    private static boolean sameMaterial(BlockState actual, BlockState expected) {
        return actual.material().equals(expected.material());
    }

    private static String orientationOf(BlockState jigsaw) {
        String orientation = jigsaw.properties().get(JigsawStudioBlocks.ORIENTATION_PROPERTY);
        return orientation == null ? "" : orientation;
    }

    static KMap<String, Object> markerNbt(IrisJigsawConnector connector) {
        IrisJigsawConnector activeConnector = Objects.requireNonNull(connector, "Jigsaw Studio connector");
        KMap<String, Object> nbt = new KMap<>();
        nbt.put("name", studioIdentifier(activeConnector.getName()));
        nbt.put("target", studioIdentifier(activeConnector.getTargetName()));
        nbt.put("pool", JigsawStudioMarkerKeyCodec.encodePool(activeConnector.getPool()));
        nbt.put("final_state", activeConnector.getFinalState());
        nbt.put("joint", activeConnector.getJoint().name().toLowerCase(Locale.ROOT));
        nbt.put("selection_priority", activeConnector.getSelectionPriority());
        nbt.put("placement_priority", activeConnector.getPlacementPriority());
        return nbt;
    }

    private static boolean markerNbtMatches(
            Map<String, Object> expected,
            Map<String, Object> hydrated
    ) {
        if (hydrated == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object actual = hydrated.get(entry.getKey());
            if (entry.getValue() instanceof Number expectedNumber) {
                if (!(actual instanceof Number actualNumber)
                        || expectedNumber.longValue() != actualNumber.longValue()) {
                    return false;
                }
            } else if (!Objects.equals(entry.getValue(), actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nbtContains(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber) {
            if (!(actual instanceof Number actualNumber)) {
                return false;
            }
            if (isIntegral(expectedNumber) && isIntegral(actualNumber)) {
                return expectedNumber.longValue() == actualNumber.longValue();
            }
            return Double.compare(expectedNumber.doubleValue(), actualNumber.doubleValue()) == 0;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())
                        || !nbtContains(entry.getValue(), actualMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList) || expectedList.size() != actualList.size()) {
                return false;
            }
            for (int index = 0; index < expectedList.size(); index++) {
                if (!nbtContains(expectedList.get(index), actualList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(expected, actual);
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long;
    }

    static void restoreCapturedMetadata(
            IrisJigsawConnector captured,
            IrisJigsawPiece sourcePiece
    ) {
        IrisJigsawConnector activeCaptured = Objects.requireNonNull(captured, "Captured jigsaw connector");
        IrisJigsawConnector original = connectorAt(sourcePiece, activeCaptured.getPosition());
        if (original == null) {
            return;
        }
        activeCaptured.setName(restoreIdentifier(activeCaptured.getName(), original.getName()));
        activeCaptured.setTargetName(restoreIdentifier(
                activeCaptured.getTargetName(),
                original.getTargetName()));
        activeCaptured.setChannel(original.getChannel() == null ? "" : original.getChannel());
    }

    private static void restoreCapturedMetadataForDisplay(
            IrisJigsawConnector captured,
            IrisJigsawPiece sourcePiece,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns
    ) {
        if (Math.floorMod(displayRotationQuarterTurns, 4) == 0) {
            restoreCapturedMetadata(captured, sourcePiece);
            return;
        }
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? displayDimensions.width()
                : displayDimensions.depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? displayDimensions.depth()
                : displayDimensions.width();
        LocalPosition position = inversePosition(
                captured.getPosition().x(),
                captured.getPosition().y(),
                captured.getPosition().z(),
                sourceWidth,
                sourceDepth,
                quarterTurns);
        PieceTurn inverseRotation = PieceTurn.aroundY(90 * quarterTurns);
        IrisJigsawConnector sourceOriented = CapturedConnector.from(captured).toConnector()
                .setPosition(new BlockPosition(position.x(), position.y(), position.z()))
                .setDirection(captured.getDirection().turnedBy(inverseRotation.quarterTurns()))
                .setTop(captured.getTop().turnedBy(inverseRotation.quarterTurns()));
        restoreCapturedMetadata(sourceOriented, sourcePiece);
        captured.setName(sourceOriented.getName());
        captured.setTargetName(sourceOriented.getTargetName());
        captured.setChannel(sourceOriented.getChannel());
    }

    private static IrisJigsawConnector connectorAt(IrisJigsawPiece piece, BlockPosition position) {
        if (piece == null || piece.getConnectors() == null || position == null) {
            return null;
        }
        for (IrisJigsawConnector connector : piece.getConnectors()) {
            if (connector != null && position.equals(connector.getPosition())) {
                return connector;
            }
        }
        return null;
    }

    private static String restoreIdentifier(String captured, String original) {
        if (original != null && studioIdentifier(original).equals(captured)) {
            return original;
        }
        return captured;
    }

    private static String studioIdentifier(String value) {
        String normalized = Objects.requireNonNull(value, "Jigsaw Studio marker identifier").trim();
        return normalized.indexOf(':') < 0 ? "iris:" + normalized : normalized;
    }

    private boolean isCurrentRequest(ActiveStudio studio, UUID requestId) {
        if (studio == null
                || requestId == null
                || studios.get(studio.worldId()) != studio
                || !studio.generator().getRequest().requestId().equals(requestId)) {
            return false;
        }
        JigsawStudioRequest active = registry.getRequest(
                studio.generator().getRequest().packKey());
        return active != null && active.requestId().equals(requestId);
    }

    private boolean isCurrentSave(
            ActiveStudio studio,
            UUID requestId,
            JigsawStudioSession.SaveIdentity identity
    ) {
        return isCurrentRequest(studio, requestId)
                && studio.generator().getSession().isSaveCurrent(identity);
    }

    private static String readinessMessage(JigsawStudioBay bay, BayReadiness readiness) {
        if (!readiness.failure().isEmpty()) {
            return "Bay '" + bay.stableId() + "' is invalid and cannot be saved: " + readiness.failure();
        }
        return "Bay '" + bay.stableId() + "' is not ready to save: "
                + readiness.generatedChunks() + "/" + readiness.requiredChunks() + " chunk(s) populated, "
                + readiness.hydratedChunks() + "/" + readiness.requiredChunks()
                + " chunk(s) hydrated. Visit the whole bay and wait for its markers to finish loading.";
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static Set<Long> requiredChunks(
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered
    ) {
        Set<Long> chunks = new HashSet<>();
        if (!rendered.valid()) {
            return chunks;
        }
        JigsawStudioBounds bounds = bay.bounds();
        int minimumChunkX = bounds.originX() >> 4;
        int maximumChunkX = bounds.maxX() >> 4;
        int minimumChunkZ = bounds.originZ() >> 4;
        int maximumChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                chunks.add(chunkKey(chunkX, chunkZ));
            }
        }
        return Set.copyOf(chunks);
    }

    private static CaptureTarget resolveCaptureTarget(
            ActiveStudio studio,
            JigsawStudioBay bay,
            JigsawStudioVariant variant
    )
            throws IOException {
        PackResources resources = studio.generator().getPack().resources();
        JigsawStudioLayout currentLayout = studio.generator().getSession().layout();
        JigsawStudioBay currentBay = currentLayout.get(bay.stableId());
        if (currentBay == null) {
            throw new IOException("workcell '" + bay.stableId() + "' is absent from the current Studio layout");
        }
        JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio capture variant");
        if (!currentLayout.accepts(currentBay, activeVariant)) {
            throw new IOException("variant '" + activeVariant.pieceKey()
                    + "' does not belong to workcell '" + currentBay.stableId() + "'");
        }
        IrisJigsawPiece piece = StructureResources.piece(resources, activeVariant.pieceKey());
        if (piece == null || piece.getObject() == null || piece.getObject().isBlank()) {
            throw new IOException("piece '" + activeVariant.pieceKey() + "' is missing or has no object");
        }
        PlaceableObject object = resources.objects().object(resources.idOf(piece.getObject())).orElse(null);
        if (object == null || object.width() < 1 || object.height() < 1 || object.depth() < 1) {
            throw new IOException("object '" + piece.getObject() + "' is missing or has invalid dimensions");
        }
        JigsawStudioBounds bayBounds = currentBay.bounds();
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                object.width(), object.height(), object.depth());
        JigsawStudioCellDimensions canonicalDimensions = activeVariant.canonicalDimensions(sourceDimensions);
        if (canonicalDimensions.width() > bayBounds.dimensions().width()
                || canonicalDimensions.height() > bayBounds.dimensions().height()
                || canonicalDimensions.depth() > bayBounds.dimensions().depth()) {
            throw new IOException("object '" + piece.getObject() + "' is "
                    + canonicalDimensions.width() + "x" + canonicalDimensions.height() + "x"
                    + canonicalDimensions.depth() + " in its displayed orientation"
                    + " but bay '" + currentBay.stableId() + "' is only "
                    + bayBounds.dimensions().width() + "x" + bayBounds.dimensions().height() + "x"
                    + bayBounds.dimensions().depth());
        }
        return new CaptureTarget(
                new JigsawStudioBounds(
                        bayBounds.originX(),
                        bayBounds.originY(),
                        bayBounds.originZ(),
                        canonicalDimensions),
                piece,
                SourceObject.of(object),
                activeVariant.sourceToCanonicalQuarterTurns(),
                studio.generator().getSession().workcellSnapshot(currentBay.stableId()).connectorsVisible());
    }

    private void reloadSessionLayout(ActiveStudio studio) throws IOException {
        JigsawStudioLayout layout = loadMappedLayout(studio);
        studio.generator().getSession().replaceLayout(layout);
        for (JigsawStudioBay workcell : layout.bays()) {
            studio.generator().invalidateRender(workcell.stableId());
        }
    }

    private void scheduleOnlinePlayers(UUID worldId) {
        ActiveStudio studio = studios.get(worldId);
        if (studio == null) {
            return;
        }
        for (ToolPlayer player : studio.world().players()) {
            JigsawStudioThreads.run(player, () -> {
                if (studioOf(player) == studio) {
                    ensureVisualizationLoop(player);
                    reconcilePlayerContext(player, player.position());
                }
            });
        }
    }

    public void reconcilePlayerContext(ToolPlayer player, BlockPosition target) {
        if (player == null) {
            return;
        }
        if (!JigsawStudioThreads.owns(player)) {
            scheduler.onPlayerLater(
                    player,
                    () -> reconcilePlayerContext(player, player.position()),
                    RECONCILE_RETRY_TICKS,
                    () -> { });
            return;
        }
        BlockPosition at = target == null ? player.position() : target;
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            clearPlayerContext(player);
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().findAt(at.x(), at.y(), at.z());
        boolean owner = ownerMatches(player, studio);
        selectEnteredWorkcell(session, workcell, owner);
        String workcellId = workcell == null ? "" : workcell.stableId();
        playerWorkcells.put(player.id(), new PlayerWorkcellContext(
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                workcellId));

        JigsawStudioVariant variant = workcell == null
                ? null
                : session.activeVariant(workcell.stableId()).orElse(null);
        JigsawStudioBoardState state = boardState(studio, session, workcell, variant);
        String workcellRole = workcell == null ? "" : workcell.canonicalDisplayName();
        String workcellName = workcell == null ? "" : workcell.displayName();
        String variantName = variant == null ? "" : variant.resolvedDisplayName();
        String hint = "Triple-sneak for controls";
        JigsawStudioBoardContext context = new JigsawStudioBoardContext(
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                studio.generator().getRequest().structureKey(),
                session.layout().mode(),
                workcellRole,
                workcellName,
                variantName,
                state,
                hint);
        player.sidebar(context.sidebarTitle(), context.sidebarLines());
    }

    static boolean selectEnteredWorkcell(
            JigsawStudioSession session,
            JigsawStudioBay workcell,
            boolean owner
    ) {
        return owner && workcell != null && session.selectBay(workcell.stableId());
    }

    public void refreshWorkcellContext(UUID worldId, String workcellId) {
        if (worldId == null || workcellId == null) {
            return;
        }
        ActiveStudio studio = studios.get(worldId);
        if (studio == null) {
            return;
        }
        for (ToolPlayer player : studio.world().players()) {
            JigsawStudioThreads.run(player, () -> {
                PlayerWorkcellContext context = playerWorkcells.get(player.id());
                if (context != null && workcellId.equals(context.workcellId())) {
                    reconcilePlayerContext(player, player.position());
                }
            });
        }
    }

    private void clearWorldPlayerContexts(ToolWorld world, UUID worldId) {
        for (ToolPlayer player : world.players()) {
            JigsawStudioThreads.run(player, () -> {
                PlayerWorkcellContext context = playerWorkcells.get(player.id());
                if (context != null && context.worldId().equals(worldId)) {
                    closeMenu(player);
                    clearPlayerContext(player);
                }
            });
        }
    }

    private void clearPlayerContext(ToolPlayer player) {
        if (player == null) {
            return;
        }
        playerWorkcells.remove(player.id());
        player.hideSidebar();
    }

    private static boolean ownerMatches(ToolPlayer player, ActiveStudio studio) {
        if (player == null || studio == null) {
            return false;
        }
        UUID ownerId = studio.generator().getRequest().ownerId();
        return ownerMatches(ownerId, player.id());
    }

    static boolean canCreateVariants(JigsawStudioLayout layout) {
        return Objects.requireNonNull(layout, "Jigsaw Studio layout")
                .variantCatalog()
                .editableGraph();
    }

    static String variantCreationSourceFailure(
            JigsawStudioVariant activeVariant,
            boolean duplicateActive
    ) {
        if (activeVariant == null) {
            return duplicateActive
                    ? "This workcell has no active variant to duplicate."
                    : "This workcell has no active variant whose pool role can be copied. Use "
                    + "/iris jigsaw piece create <poolKey> <pieceKey> to choose an owned pool explicitly.";
        }
        if (!activeVariant.owned()) {
            return "Adopt or clone this graph before creating from its read-only variant.";
        }
        if (activeVariant.memberships().isEmpty()) {
            return "The active variant has no owned pool membership to copy. Use "
                    + "/iris jigsaw piece create <poolKey> <pieceKey> to choose an owned pool explicitly.";
        }
        return "";
    }

    static boolean ownerMatches(UUID ownerId, UUID actorId) {
        return ownerId == null || ownerId.equals(actorId);
    }

    private static boolean authorizeOwner(ToolPlayer player, ActiveStudio studio) {
        if (ownerMatches(player, studio)) {
            return true;
        }
        message(player, "This Jigsaw Studio is owned by another player session.");
        return false;
    }

    private boolean isUnauthorizedStudioEdit(ToolPlayer player, ActiveStudio studio, BlockPosition block) {
        if (player == null || studio == null || block == null) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        if (previewRenderer.contains(requestId, block.x(), block.y(), block.z())) {
            message(player, "The seed-1337 Jigsaw preview is read-only and refreshes automatically.");
            return true;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(block.x(), block.y(), block.z());
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        if (bay != null && (variant == null || !variant.owned())) {
            message(player, variant == null
                    ? "This empty Jigsaw Studio workcell has no loaded editable variant. Load one before editing it."
                    : "This Jigsaw Studio variant is read-only. Adopt or clone its graph before editing it.");
            return true;
        }
        if (reopenRequiredRequests.contains(requestId)) {
            message(player, "Close and reopen Jigsaw Studio before editing the resized layout.");
            return true;
        }
        if (materializationInProgress(studio)) {
            message(player, "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return true;
        }
        if (!blocksStudioEdit(studio.generator().getRequest().ownerId(), player.id())) {
            return false;
        }
        message(player, "This Jigsaw Studio is owned by another player session.");
        return true;
    }

    private boolean materializationInProgress(ActiveStudio studio) {
        if (studio == null) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        synchronized (saveLifecycleLock) {
            return materializationsInProgress.contains(requestId);
        }
    }

    private boolean anyMaterializationInProgress() {
        synchronized (saveLifecycleLock) {
            return !materializationsInProgress.isEmpty();
        }
    }

    private boolean anyNonEditableWorkcell() {
        for (ActiveStudio studio : studios.values()) {
            if (hasNonEditableWorkcell(studio)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonEditableWorkcell(ActiveStudio studio) {
        JigsawStudioSession session = studio.generator().getSession();
        for (JigsawStudioBay bay : session.layout().bays()) {
            JigsawStudioVariant variant = session.activeVariant(bay.stableId()).orElse(null);
            if (variant == null || !variant.owned()) {
                return true;
            }
        }
        return false;
    }

    static boolean blocksStudioEdit(UUID ownerId, UUID actorId) {
        return !ownerMatches(ownerId, actorId);
    }

    static boolean blocksStudioInventoryMutation(
            boolean studioBlockInventory,
            boolean immutable,
            boolean materializing,
            boolean reopenRequired,
            UUID ownerId,
            UUID actorId
    ) {
        return studioBlockInventory
                && (immutable
                || materializing
                || reopenRequired
                || actorId != null && blocksStudioEdit(ownerId, actorId));
    }

    private boolean isImmutableStudioBlock(ActiveStudio studio, BlockPosition block) {
        StudioBlockMutationContext context = studioBlockMutationContext(studio, block);
        return context != null && context.immutable();
    }

    /**
     * Whether the block is the studio's control chest. A block this thread does not own cannot be read, and the control
     * position only ever holds the chest, so the position decides there.
     */
    private boolean isControlChest(ActiveStudio studio, BlockPosition block) {
        if (studio == null || block == null) {
            return false;
        }
        JigsawStudioControlPosition control = studio.generator().getLayout().controlPosition();
        boolean atControlPosition = block.x() == control.worldX()
                && block.y() == control.worldY()
                && block.z() == control.worldZ();
        if (!atControlPosition) {
            return false;
        }
        return blockIfOwned(studio, block).map(JigsawStudioBlocks::isChest).orElse(true);
    }

    private static JigsawStudioBoardState boardState(
            ActiveStudio studio,
            JigsawStudioSession session,
            JigsawStudioBay workcell,
            JigsawStudioVariant variant
    ) {
        if (workcell == null) {
            return JigsawStudioBoardState.SAVED;
        }
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcell.stableId());
        if (snapshot.switchInProgress()) {
            return JigsawStudioBoardState.LOADING;
        }
        if (snapshot.saveInProgress()) {
            return JigsawStudioBoardState.SAVING;
        }
        if (!workcell.enabled()) {
            return JigsawStudioBoardState.DISABLED;
        }
        if (variant != null && !variant.owned()) {
            return JigsawStudioBoardState.READ_ONLY;
        }
        BayReadiness readiness = studio.population(workcell).readiness();
        if (!readiness.failure().isEmpty()) {
            return JigsawStudioBoardState.INVALID;
        }
        return snapshot.dirty() ? JigsawStudioBoardState.UNSAVED : JigsawStudioBoardState.SAVED;
    }

    private static String displayKey(String resourceKey) {
        int separator = resourceKey.lastIndexOf('/');
        return separator < 0 ? resourceKey : resourceKey.substring(separator + 1);
    }

    private void ensureVisualizationLoop(ToolPlayer player) {
        if (player == null) {
            return;
        }
        if (!JigsawStudioThreads.owns(player)) {
            JigsawStudioThreads.run(player, () -> ensureVisualizationLoop(player));
            return;
        }
        UUID playerId = player.id();
        if (particlesDisabled.contains(playerId)
                || studioOf(player) == null
                || !visualizationLoops.add(playerId)) {
            return;
        }
        visualizationTick(player);
    }

    private void visualizationTick(ToolPlayer player) {
        UUID playerId = player.id();
        if (particlesDisabled.contains(playerId)) {
            visualizationLoops.remove(playerId);
            return;
        }
        ActiveStudio studio = studioOf(player);
        if (studio == null) {
            visualizationLoops.remove(playerId);
            return;
        }
        try {
            renderVisualization(player, studio);
        } catch (Throwable exception) {
            visualizationLoops.remove(playerId);
            reportError(exception);
            return;
        }
        scheduler.onPlayerLater(
                player,
                () -> visualizationTick(player),
                VISUAL_INTERVAL_TICKS,
                () -> visualizationLoops.remove(playerId));
    }

    private void renderVisualization(ToolPlayer player, ActiveStudio studio) {
        ParticleBudget budget = new ParticleBudget(PARTICLE_BUDGET);
        drawVisualization(player, studio, budget);
        player.particles(budget.dust());
    }

    private void drawVisualization(ToolPlayer player, ActiveStudio studio, ParticleBudget budget) {
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioLayout layout = session.layout();
        BlockPosition playerBlock = player.position();
        ViewPoint playerLocation = new ViewPoint(
                playerBlock.x() + BLOCK_CENTER, playerBlock.y(), playerBlock.z() + BLOCK_CENTER);
        JigsawStudioBay focused = layout.findAt(playerBlock.x(), playerBlock.y(), playerBlock.z());
        if (focused != null) {
            int focusedColor = bayValid(studio, focused) ? SELECTED_COLOR : INVALID_BAY_COLOR;
            drawConnectors(playerLocation, studio, focused, budget);
            drawBounds(playerLocation, focused.bounds(), focusedColor, 1.15F, 1.0D, budget);
        }
        drawLivePreview(playerLocation, studio, budget);
        drawAssemblyPreview(player, playerLocation, studio, budget);
        for (JigsawStudioBay bay : layout.bays()) {
            if (bay == focused || !isNearby(playerLocation, bay.bounds())) {
                continue;
            }
            int nearbyColor = bayValid(studio, bay) ? NEARBY_COLOR : INVALID_BAY_COLOR;
            drawBounds(playerLocation, bay.bounds(), nearbyColor, 0.55F, 4.0D, budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    /** Where the viewer is, as particle distances count it. */
    private record ViewPoint(double x, double y, double z) {
    }

    private static int rgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    private void drawLivePreview(
            ViewPoint playerLocation,
            ActiveStudio studio,
            ParticleBudget budget
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        JigsawStudioGraphEvaluation evaluation = evaluations.get(requestId);
        if (evaluation == null || evaluation.previewBounds().isEmpty()) {
            return;
        }
        JigsawStudioPreviewRenderer.PreviewBounds preview = evaluation.previewBounds();
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(
                preview.maximumX() - preview.minimumX() + 1,
                preview.maximumY() - preview.minimumY() + 1,
                preview.maximumZ() - preview.minimumZ() + 1);
        int color = switch (evaluation.state()) {
            case VALID -> ASSEMBLY_PREVIEW_COLOR;
            case PENDING, WARNING, STALE -> LIVE_PREVIEW_WARNING_COLOR;
            case INVALID -> INVALID_BAY_COLOR;
        };
        drawBounds(
                playerLocation,
                new JigsawStudioBounds(
                        preview.minimumX(),
                        preview.minimumY(),
                        preview.minimumZ(),
                        dimensions),
                color,
                0.85F,
                2.0D,
                budget);
    }

    private void drawAssemblyPreview(
            ToolPlayer player,
            ViewPoint playerLocation,
            ActiveStudio studio,
            ParticleBudget budget
    ) {
        AssemblyPreview preview = assemblyPreviews.get(player.id());
        if (preview == null) {
            return;
        }
        if (!preview.worldId().equals(studio.worldId())
                || preview.expiresAtMillis() < System.currentTimeMillis()) {
            assemblyPreviews.remove(player.id(), preview);
            return;
        }
        for (JigsawStudioBounds bounds : preview.bounds()) {
            drawBounds(playerLocation, bounds, ASSEMBLY_PREVIEW_COLOR, 0.85F, 2.0D, budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    private static void drawConnectors(
            ViewPoint playerLocation,
            ActiveStudio studio,
            JigsawStudioBay bay,
            ParticleBudget budget
    ) {
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
        if (!rendered.valid()) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            IrisJigsawConnector connector = renderedConnector.connector();
            JigsawDirection direction = connector.getDirection();
            drawConnectorLine(
                    playerLocation,
                    bay.bounds().originX() + renderedConnector.x() + 0.5D,
                    bay.bounds().originY() + renderedConnector.y() + 0.5D,
                    bay.bounds().originZ() + renderedConnector.z() + 0.5D,
                    direction,
                    connectorColor(connector),
                    budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    private static void drawConnectorLine(
            ViewPoint playerLocation,
            double startX,
            double startY,
            double startZ,
            JigsawDirection direction,
            int color,
            ParticleBudget budget
    ) {
        drawLine(
                playerLocation,
                startX,
                startY,
                startZ,
                startX + direction.x() * 1.75D,
                startY + direction.y() * 1.75D,
                startZ + direction.z() * 1.75D,
                color,
                1.05F,
                0.35D,
                budget);
    }

    private static boolean bayValid(ActiveStudio studio, JigsawStudioBay bay) {
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
        return rendered.valid() && studio.population(bay).readiness().failure().isEmpty();
    }

    private static int connectorColor(IrisJigsawConnector connector) {
        if (connector.getPool() == null || connector.getPool().isBlank()
                || connector.getName() == null || connector.getName().isBlank()
                || connector.getTargetName() == null || connector.getTargetName().isBlank()) {
            return INVALID_CONNECTOR_COLOR;
        }
        String channel = connector.getChannel();
        if (channel == null || channel.isBlank()) {
            return VALID_CONNECTOR_COLOR;
        }
        int hash = channel.toLowerCase(Locale.ROOT).hashCode();
        int red = 80 + (hash & 127);
        int green = 80 + ((hash >>> 8) & 127);
        int blue = 80 + ((hash >>> 16) & 127);
        return rgb(red, green, blue);
    }

    private static void drawBounds(
            ViewPoint playerLocation,
            JigsawStudioBounds bounds,
            int color,
            float size,
            double step,
            ParticleBudget budget
    ) {
        double minX = bounds.originX();
        double minY = bounds.originY();
        double minZ = bounds.originZ();
        double maxX = bounds.maxX() + 1.0D;
        double maxY = bounds.maxY() + 1.0D;
        double maxZ = bounds.maxZ() + 1.0D;
        drawLine(playerLocation, minX, minY, minZ, maxX, minY, minZ, color, size, step, budget);
        drawLine(playerLocation, minX, minY, maxZ, maxX, minY, maxZ, color, size, step, budget);
        drawLine(playerLocation, minX, maxY, minZ, maxX, maxY, minZ, color, size, step, budget);
        drawLine(playerLocation, minX, maxY, maxZ, maxX, maxY, maxZ, color, size, step, budget);
        drawLine(playerLocation, minX, minY, minZ, minX, maxY, minZ, color, size, step, budget);
        drawLine(playerLocation, maxX, minY, minZ, maxX, maxY, minZ, color, size, step, budget);
        drawLine(playerLocation, minX, minY, maxZ, minX, maxY, maxZ, color, size, step, budget);
        drawLine(playerLocation, maxX, minY, maxZ, maxX, maxY, maxZ, color, size, step, budget);
        drawLine(playerLocation, minX, minY, minZ, minX, minY, maxZ, color, size, step, budget);
        drawLine(playerLocation, maxX, minY, minZ, maxX, minY, maxZ, color, size, step, budget);
        drawLine(playerLocation, minX, maxY, minZ, minX, maxY, maxZ, color, size, step, budget);
        drawLine(playerLocation, maxX, maxY, minZ, maxX, maxY, maxZ, color, size, step, budget);
    }

    private static void drawLine(
            ViewPoint playerLocation,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            int color,
            float size,
            double step,
            ParticleBudget budget
    ) {
        if (budget.empty()) {
            return;
        }
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        int samples = Math.max(1, (int) Math.ceil(length / step));
        for (int index = 0; index <= samples; index++) {
            double progress = (double) index / samples;
            double x = startX + deltaX * progress;
            double y = startY + deltaY * progress;
            double z = startZ + deltaZ * progress;
            double distanceX = playerLocation.x() - x;
            double distanceY = playerLocation.y() - y;
            double distanceZ = playerLocation.z() - z;
            if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ > VISUAL_RANGE_SQUARED) {
                continue;
            }
            if (!budget.consume()) {
                return;
            }
            budget.add(new Particle(x, y, z, color, size));
        }
    }

    private static boolean isNearby(ViewPoint location, JigsawStudioBounds bounds) {
        double centerX = bounds.originX() + bounds.dimensions().width() / 2.0D;
        double centerY = bounds.originY() + bounds.dimensions().height() / 2.0D;
        double centerZ = bounds.originZ() + bounds.dimensions().depth() / 2.0D;
        double deltaX = location.x() - centerX;
        double deltaY = location.y() - centerY;
        double deltaZ = location.z() - centerZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= VISUAL_RANGE_SQUARED;
    }

    private static JigsawStudioBay findBay(JigsawStudioLayout layout, String requested) {
        String key = requested.trim();
        JigsawStudioBay exact = layout.get(key);
        if (exact != null) {
            return exact;
        }
        if (key.regionMatches(true, 0, "topology/", 0, "topology/".length())) {
            String maskText = key.substring("topology/".length());
            try {
                int mask = Integer.parseInt(maskText, 16);
                JigsawPlanarArchetype archetype = JigsawPlanarArchetype.fromTopology(
                        JigsawPlanarTopology.fromMask(mask));
                return layout.get(archetype.stableId());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        for (JigsawStudioBay bay : layout.bays()) {
            if (bay.stableId().equalsIgnoreCase(key)
                    || bay.archetype().map(Enum::name).filter(name -> name.equalsIgnoreCase(key)).isPresent()) {
                return bay;
            }
        }
        for (JigsawStudioVariant variant : layout.variantCatalog().variants()) {
            if (!variant.pieceKey().equalsIgnoreCase(key)) {
                continue;
            }
            return layout.workcellForVariant(variant.pieceKey()).orElse(null);
        }
        return null;
    }

    static String writeFailure(StructureWriteResult result) {
        if (!result.conflicts().isEmpty()) {
            StructureWriteResult.Conflict conflict = result.conflicts().getFirst();
            return "Jigsaw Studio ownership conflict at '" + conflict.relativePath() + "': "
                    + conflict.reason().name().toLowerCase(Locale.ROOT)
                    + ". No authored files were changed.";
        }
        String detail = result.failure().map(JigsawStudioService::failureMessage)
                .orElse(result.status().name().toLowerCase(Locale.ROOT));
        return "Jigsaw Studio atomic save failed: " + detail;
    }

    private static String failureMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void message(ToolPlayer player, String text) {
        if (player != null) {
            player.message(ToolText.plain(MESSAGE_PREFIX + text));
        }
    }

    static void playSaveSound(ToolPlayer player) {
        if (player != null) {
            player.sound(SAVE_SOUND, SAVE_SOUND_VOLUME, SAVE_SOUND_PITCH);
        }
    }

    private static void report(ToolPlayer player, boolean enabled, String text) {
        if (enabled) {
            message(player, text);
        }
    }

    private record StudioBlockMutationContext(
            ActiveStudio studio,
            UUID requestId,
            UUID ownerId,
            boolean immutable
    ) {
        private StudioBlockMutationContext {
            Objects.requireNonNull(studio, "Jigsaw Studio block mutation studio");
            Objects.requireNonNull(requestId, "Jigsaw Studio block mutation request ID");
        }
    }

    /** An open studio: its world, what paints it, and the bookkeeping of populating the workcells. */
    record ActiveStudio(
            UUID worldId,
            ToolWorld world,
            JigsawStudioGenerator generator,
            ConcurrentHashMap<String, BayPopulation> populations,
            Set<Long> hydrationsInProgress,
            AtomicLong evaluationGeneration
    ) {
        ActiveStudio {
            Objects.requireNonNull(worldId, "Jigsaw Studio world ID");
            Objects.requireNonNull(world, "Jigsaw Studio world");
            Objects.requireNonNull(generator, "Jigsaw Studio generator");
            Objects.requireNonNull(populations, "Jigsaw Studio bay populations");
            Objects.requireNonNull(hydrationsInProgress, "Jigsaw Studio hydrations");
            Objects.requireNonNull(evaluationGeneration, "Jigsaw Studio evaluation generation");
        }

        /** Whether the platform has the chunk loaded now. */
        boolean isChunkLoaded(int chunkX, int chunkZ) {
            return world.loaded(new ChunkPos(chunkX, chunkZ));
        }

        BayPopulation population(JigsawStudioBay bay) {
            return populations.computeIfAbsent(bay.stableId(), key -> {
                JigsawStudioGenerator.RenderedBay rendered = generator.renderBay(bay);
                return new BayPopulation(
                        requiredChunks(bay, rendered),
                        rendered.valid() ? "" : rendered.failure());
            });
        }

        BayPopulation replacePopulation(
                JigsawStudioBay bay,
                JigsawStudioGenerator.RenderedBay rendered,
                String failure,
                boolean ready
        ) {
            BayPopulation replacement = new BayPopulation(
                    requiredChunks(bay, rendered),
                    failure);
            if (ready) {
                replacement.markFullyReady();
            }
            populations.put(bay.stableId(), replacement);
            return replacement;
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
                reportError(exception);
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
                refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
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
                JigsawStudioThreads.onRegion(
                        log,
                        work.world(),
                        area.chunkX(),
                        area.chunkZ(),
                        () -> materializeRollbackChunk(this, area),
                        () -> rollbackComplete(
                                area,
                                "Iris could not schedule rollback for workcell chunk "
                                        + area.chunkX() + "," + area.chunkZ() + ".",
                                null));
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
                reportError(exception);
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
                    refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
                    message(work.player(), candidateFailure + " The previous variant was restored.");
                    return;
                }
                String failure = rollbackFailure.isEmpty()
                        ? "The previous variant could not be restored because the Studio session changed."
                        : rollbackFailure;
                work.studio().replacePopulation(work.workcell(), work.previous(), failure, false);
                refreshWorkcellContext(work.studio().worldId(), work.workcell().stableId());
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

    private final class CaptureCoordinator {
        private final CaptureWork work;
        private final List<ChunkCaptureArea> areas;
        private final Map<Long, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
        private final AtomicInteger remaining;
        private final AtomicBoolean failed = new AtomicBoolean(false);
        private final AtomicBoolean assemblyStarted = new AtomicBoolean(false);
        private final AtomicBoolean operationFinished = new AtomicBoolean(false);
        private volatile AutosavePersistentFailure persistentFailure;

        private CaptureCoordinator(CaptureWork work, List<ChunkCaptureArea> areas) {
            this.work = Objects.requireNonNull(work, "Jigsaw Studio capture work");
            this.areas = List.copyOf(areas);
            this.remaining = new AtomicInteger(this.areas.size());
        }

        private ActiveStudio studio() {
            return work.studio();
        }

        private ToolWorld world() {
            return work.world();
        }

        private JigsawStudioBay bay() {
            return work.bay();
        }

        private CaptureTarget captureTarget() {
            return work.captureTarget();
        }

        private ToolPlayer player() {
            return work.player();
        }

        private UUID requestId() {
            return work.requestId();
        }

        private JigsawStudioSession.SaveIdentity saveIdentity() {
            return work.saveIdentity();
        }

        private List<ChunkCaptureArea> areas() {
            return areas;
        }

        private boolean stopped() {
            return failed.get();
        }

        private boolean failed() {
            return failed.get();
        }

        private void accept(ChunkSnapshot snapshot) {
            if (failed.get() || assemblyStarted.get()) {
                return;
            }
            ChunkCaptureArea area = snapshot.area();
            long key = chunkKey(area.chunkX(), area.chunkZ());
            if (snapshots.putIfAbsent(key, snapshot) != null) {
                fail("Jigsaw Studio received duplicate snapshot for bay chunk "
                        + area.chunkX() + "," + area.chunkZ() + ".", null);
                return;
            }
            int incomplete = remaining.decrementAndGet();
            if (incomplete < 0) {
                fail("Jigsaw Studio received more bay chunk snapshots than expected.", null);
                return;
            }
            if (incomplete == 0 && assemblyStarted.compareAndSet(false, true)) {
                List<ChunkSnapshot> completed = List.copyOf(snapshots.values());
                scheduler.async(() -> assembleAndPersist(this, completed));
            }
        }

        private void fail(String failure, Throwable exception) {
            if (!failed.compareAndSet(false, true)) {
                return;
            }
            if (exception != null) {
                reportError(exception);
            }
            complete();
            message(player(), failure);
        }

        private void failPersistent(String failure, Throwable exception) {
            if (!failed.compareAndSet(false, true)) {
                return;
            }
            recordPersistentFailure(failure, exception);
            complete();
            message(player(), failure);
        }

        private void recordPersistentFailure(String failure, Throwable exception) {
            persistentFailure = new AutosavePersistentFailure(failure, exception);
        }

        private void complete() {
            if (operationFinished.compareAndSet(false, true)) {
                studio().generator().getSession().abortSave(saveIdentity());
                finishSave(requestId());
                completeAutosaveAttempt(
                        studio(),
                        saveIdentity(),
                        work.autosaveFailureState(),
                        persistentFailure);
                refreshWorkcellContext(studio().worldId(), saveIdentity().workcellId());
            }
        }
    }

    private record CaptureWork(
            ActiveStudio studio,
            ToolWorld world,
            JigsawStudioBay bay,
            CaptureTarget captureTarget,
            ToolPlayer player,
            UUID requestId,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState autosaveFailureState
    ) {
        CaptureWork {
            Objects.requireNonNull(studio, "Jigsaw Studio capture studio");
            Objects.requireNonNull(world, "Jigsaw Studio capture world");
            Objects.requireNonNull(bay, "Jigsaw Studio capture bay");
            Objects.requireNonNull(captureTarget, "Jigsaw Studio capture target");
            Objects.requireNonNull(requestId, "Jigsaw Studio capture request ID");
            Objects.requireNonNull(saveIdentity, "Jigsaw Studio capture save identity");
            Objects.requireNonNull(autosaveFailureState, "Jigsaw Studio capture autosave failure state");
        }
    }

    private record EvaluationComputation(
            JigsawStudioGraphEvaluation evaluation,
            JigsawStudioPreviewRenderer.PreviewPlan plan
    ) {
        private EvaluationComputation {
            Objects.requireNonNull(evaluation, "Jigsaw Studio graph evaluation");
            Objects.requireNonNull(plan, "Jigsaw Studio evaluation preview plan");
        }
    }

    private record MaterializationWork(
            ActiveStudio studio,
            ToolWorld world,
            ToolPlayer player,
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

    @FunctionalInterface
    public interface CommandGraphMutation {
        CommandGraphMutationResult run() throws IOException;
    }

    public record CommandGraphMutationResult(
            JigsawStudioLayout layout,
            String activateWorkcellId,
            String activatePieceKey,
            Map<String, String> rebindActiveVariants,
            Optional<VariantReloadRequest> reload,
            String message
    ) {
        public CommandGraphMutationResult {
            Objects.requireNonNull(layout, "Jigsaw Studio graph-mutation layout");
            activateWorkcellId = activateWorkcellId == null ? "" : activateWorkcellId;
            activatePieceKey = activatePieceKey == null ? "" : activatePieceKey;
            rebindActiveVariants = Map.copyOf(Objects.requireNonNull(
                    rebindActiveVariants,
                    "Jigsaw Studio graph-mutation active variant bindings"));
            reload = Objects.requireNonNull(reload, "Jigsaw Studio graph-mutation reload");
            message = message == null ? "Graph update completed." : message;
            if (activatePieceKey.isEmpty() != activateWorkcellId.isEmpty()) {
                throw new IllegalArgumentException(
                        "Jigsaw Studio graph activation requires both a workcell and piece key");
            }
            int followUpActions = (activatePieceKey.isEmpty() ? 0 : 1)
                    + (rebindActiveVariants.isEmpty() ? 0 : 1)
                    + (reload.isEmpty() ? 0 : 1);
            if (followUpActions > 1) {
                throw new IllegalArgumentException(
                        "Jigsaw Studio graph mutations may activate, rebind, or reload, but not combine them");
            }
        }

        public CommandGraphMutationResult(
                JigsawStudioLayout layout,
                String activateWorkcellId,
                String activatePieceKey,
                String message
        ) {
            this(layout, activateWorkcellId, activatePieceKey, Map.of(), Optional.empty(), message);
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

    static final class BayPopulation {
        private final Set<Long> requiredChunks;
        private final Set<Long> generatedChunks = ConcurrentHashMap.newKeySet();
        private final Set<Long> appliedChunks = ConcurrentHashMap.newKeySet();
        private final Set<Long> hydratedChunks = ConcurrentHashMap.newKeySet();
        private volatile String failure;

        BayPopulation(Set<Long> requiredChunks, String failure) {
            this.requiredChunks = Set.copyOf(requiredChunks);
            this.failure = failure == null ? "" : failure;
        }

        boolean markGenerated(long chunk) {
            if (!requiredChunks.contains(chunk)) {
                return false;
            }
            generatedChunks.add(chunk);
            return !hydratedChunks.contains(chunk) && failure.isEmpty();
        }

        boolean needsApplication(long chunk) {
            return failure.isEmpty()
                    && requiredChunks.contains(chunk)
                    && generatedChunks.contains(chunk)
                    && !appliedChunks.contains(chunk);
        }

        boolean needsVerification(long chunk) {
            return failure.isEmpty()
                    && requiredChunks.contains(chunk)
                    && generatedChunks.contains(chunk)
                    && appliedChunks.contains(chunk)
                    && !hydratedChunks.contains(chunk);
        }

        void markApplied(long chunk) {
            if (requiredChunks.contains(chunk)) {
                appliedChunks.add(chunk);
            }
        }

        void markHydrated(long chunk) {
            if (requiredChunks.contains(chunk)) {
                hydratedChunks.add(chunk);
            }
        }

        void markFullyReady() {
            generatedChunks.addAll(requiredChunks);
            appliedChunks.addAll(requiredChunks);
            hydratedChunks.addAll(requiredChunks);
        }

        synchronized boolean fail(String reason) {
            if (!failure.isEmpty()) {
                return false;
            }
            failure = reason == null || reason.isBlank() ? "unknown hydration failure" : reason;
            return true;
        }

        BayReadiness readiness() {
            boolean ready = failure.isEmpty()
                    && !requiredChunks.isEmpty()
                    && generatedChunks.containsAll(requiredChunks)
                    && hydratedChunks.containsAll(requiredChunks);
            return new BayReadiness(
                    ready,
                    failure,
                    requiredChunks.size(),
                    generatedChunks.size(),
                    hydratedChunks.size());
        }
    }

    record BayReadiness(
            boolean ready,
            String failure,
            int requiredChunks,
            int generatedChunks,
            int hydratedChunks
    ) {
    }

    enum SaveStart {
        STARTED,
        IN_PROGRESS,
        CLOSING,
        GRAPH_OPERATION,
        EXPORT_OPERATION,
        VARIANT_OPERATION
    }

    public enum ExportStart {
        STARTED,
        NOT_ACTIVE,
        NOT_OWNER,
        DIRTY,
        CLOSING,
        SAVE_IN_PROGRESS,
        OPERATION_IN_PROGRESS,
        IN_PROGRESS
    }

    private enum SaveAttempt {
        STARTED,
        RETRY,
        DEFERRED,
        PERSISTENT_FAILURE
    }

    enum DeferredDuplicationReadiness {
        READY,
        WAITING_FOR_AUTOSAVE,
        WAITING_FOR_OPERATION,
        STALE
    }

    private enum DeferredDuplicationKind {
        SINGLE,
        FAMILY
    }

    public enum CloseStart {
        STARTED,
        NOT_ACTIVE,
        NOT_OWNER,
        DIRTY,
        SAVE_IN_PROGRESS,
        OPERATION_IN_PROGRESS
    }

    private record CaptureTarget(
            JigsawStudioBounds bounds,
            IrisJigsawPiece piece,
            SourceObject object,
            int displayRotationQuarterTurns,
            boolean connectorsVisible
    ) {
        CaptureTarget {
            Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
            Objects.requireNonNull(piece, "Jigsaw Studio capture piece");
            Objects.requireNonNull(object, "Jigsaw Studio capture object");
            displayRotationQuarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        }
    }

    private record AutosaveKey(UUID requestId, String workcellId) {
        private AutosaveKey {
            Objects.requireNonNull(requestId, "Jigsaw Studio autosave request ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio autosave workcell ID");
        }
    }

    private record JigsawTileWatchKey(
            UUID requestId,
            UUID worldId,
            int worldX,
            int worldY,
            int worldZ
    ) {
        private JigsawTileWatchKey {
            Objects.requireNonNull(requestId, "Jigsaw Studio tile watch request ID");
            Objects.requireNonNull(worldId, "Jigsaw Studio tile watch world ID");
        }
    }

    record JigsawTilePollDecision(boolean changed, boolean continueWatching) {
    }

    private record JigsawTileWatch(
            JigsawTileWatchKey key,
            ActiveStudio studio,
            UUID playerId,
            String workcellId,
            KMap<String, Object> snapshot,
            AtomicBoolean scheduleFailureLogged,
            AtomicBoolean reconciliationScheduled
    ) {
        private JigsawTileWatch {
            Objects.requireNonNull(key, "Jigsaw Studio tile watch key");
            Objects.requireNonNull(studio, "Jigsaw Studio tile watch studio");
            Objects.requireNonNull(playerId, "Jigsaw Studio tile watch player ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio tile watch workcell ID");
            Objects.requireNonNull(snapshot, "Jigsaw Studio tile watch snapshot");
            Objects.requireNonNull(scheduleFailureLogged, "Jigsaw Studio tile watch schedule warning state");
            Objects.requireNonNull(reconciliationScheduled, "Jigsaw Studio tile watch retry schedule state");
        }
    }

    static final class AutosaveFailureState {
        private final AtomicInteger persistentFailures = new AtomicInteger();
        private final AtomicBoolean failureLogged = new AtomicBoolean(false);

        AutosaveFailureDecision recordPersistentFailure() {
            int failureCount = persistentFailures.incrementAndGet();
            return new AutosaveFailureDecision(
                    persistentAutosaveRetryTicks(failureCount),
                    failureLogged.compareAndSet(false, true));
        }
    }

    record AutosaveFailureDecision(int retryTicks, boolean logFailure) {
    }

    private record AutosavePersistentFailure(String detail, Throwable cause) {
        private AutosavePersistentFailure {
            Objects.requireNonNull(detail, "Jigsaw Studio persistent autosave failure detail");
        }
    }

    private record AutosaveTicket(
            AutosaveKey key,
            ActiveStudio studio,
            JigsawStudioSession.DirtyIdentity identity,
            AtomicBoolean scheduled,
            AtomicBoolean scheduleFailureLogged,
            AutosaveFailureState failureState
    ) {
        private AutosaveTicket(
                AutosaveKey key,
                ActiveStudio studio,
                JigsawStudioSession.DirtyIdentity identity,
                AtomicBoolean scheduled,
                AtomicBoolean scheduleFailureLogged
        ) {
            this(
                    key,
                    studio,
                    identity,
                    scheduled,
                    scheduleFailureLogged,
                    new AutosaveFailureState());
        }

        private AutosaveTicket {
            Objects.requireNonNull(key, "Jigsaw Studio autosave key");
            Objects.requireNonNull(studio, "Jigsaw Studio autosave studio");
            Objects.requireNonNull(identity, "Jigsaw Studio autosave dirty identity");
            Objects.requireNonNull(scheduled, "Jigsaw Studio autosave schedule state");
            Objects.requireNonNull(scheduleFailureLogged, "Jigsaw Studio autosave schedule warning state");
            Objects.requireNonNull(failureState, "Jigsaw Studio autosave failure state");
        }
    }

    private record DeferredDuplication(
            UUID requestId,
            UUID sessionId,
            UUID playerId,
            UUID worldId,
            DeferredDuplicationKind kind,
            String workcellId,
            Map<String, String> sourcePieces,
            String themeKey,
            AtomicBoolean scheduled
    ) {
        private DeferredDuplication {
            Objects.requireNonNull(requestId, "Jigsaw Studio deferred duplicate request ID");
            Objects.requireNonNull(sessionId, "Jigsaw Studio deferred duplicate session ID");
            Objects.requireNonNull(playerId, "Jigsaw Studio deferred duplicate player ID");
            Objects.requireNonNull(worldId, "Jigsaw Studio deferred duplicate world ID");
            Objects.requireNonNull(kind, "Jigsaw Studio deferred duplicate kind");
            workcellId = workcellId == null ? "" : workcellId;
            sourcePieces = Map.copyOf(Objects.requireNonNull(
                    sourcePieces,
                    "Jigsaw Studio deferred duplicate source pieces"));
            themeKey = themeKey == null ? "" : themeKey;
            Objects.requireNonNull(scheduled, "Jigsaw Studio deferred duplicate schedule state");
            if (sourcePieces.isEmpty()) {
                throw new IllegalArgumentException("A deferred duplicate requires at least one source variant");
            }
            if (kind == DeferredDuplicationKind.SINGLE && workcellId.isBlank()) {
                throw new IllegalArgumentException("A deferred single duplicate requires a workcell");
            }
            if (kind == DeferredDuplicationKind.FAMILY && themeKey.isBlank()) {
                throw new IllegalArgumentException("A deferred family duplicate requires a theme key");
            }
        }

        private static DeferredDuplication single(
                UUID requestId,
                UUID sessionId,
                ToolPlayer player,
                UUID worldId,
                String workcellId,
                String sourcePiece
        ) {
            return new DeferredDuplication(
                    requestId,
                    sessionId,
                    player.id(),
                    worldId,
                    DeferredDuplicationKind.SINGLE,
                    workcellId,
                    Map.of(workcellId, sourcePiece),
                    "",
                    new AtomicBoolean());
        }

        private static DeferredDuplication family(
                UUID requestId,
                UUID sessionId,
                ToolPlayer player,
                UUID worldId,
                Map<String, String> sourcePieces,
                String themeKey
        ) {
            return new DeferredDuplication(
                    requestId,
                    sessionId,
                    player.id(),
                    worldId,
                    DeferredDuplicationKind.FAMILY,
                    "",
                    sourcePieces,
                    themeKey,
                    new AtomicBoolean());
        }

        private boolean sameIntent(DeferredDuplication other) {
            return other != null
                    && playerId.equals(other.playerId)
                    && kind == other.kind
                    && workcellId.equals(other.workcellId)
                    && sourcePieces.equals(other.sourcePieces)
                    && themeKey.equals(other.themeKey);
        }
    }

    private record ToolConfirmation(JigsawStudioToolPayload payload, long expiresAtNanos) {
        private ToolConfirmation {
            Objects.requireNonNull(payload, "Jigsaw Studio tool confirmation payload");
        }
    }

    private record AssemblyPreview(
            UUID worldId,
            long expiresAtMillis,
            List<JigsawStudioBounds> bounds
    ) {
        AssemblyPreview {
            Objects.requireNonNull(worldId, "Jigsaw Studio preview world");
            bounds = List.copyOf(bounds);
        }
    }

    private record PlayerWorkcellContext(
            UUID worldId,
            UUID requestId,
            String workcellId
    ) {
        PlayerWorkcellContext {
            Objects.requireNonNull(worldId, "Jigsaw Studio player-context world ID");
            Objects.requireNonNull(requestId, "Jigsaw Studio player-context request ID");
            workcellId = workcellId == null ? "" : workcellId;
        }
    }

    record ChunkCaptureArea(
            int chunkX,
            int chunkZ,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ
    ) {
        ChunkCaptureArea {
            if (minimumX < 0 || minimumZ < 0 || maximumX <= minimumX || maximumZ <= minimumZ) {
                throw new IllegalArgumentException("Jigsaw Studio chunk capture intersection is invalid");
            }
        }

        boolean contains(int x, int z) {
            return x >= minimumX && x < maximumX && z >= minimumZ && z < maximumZ;
        }
    }

    record CapturedBlock(
            int x,
            int y,
            int z,
            BlockState state,
            BlockEntityData blockEntity
    ) {
        CapturedBlock {
            Objects.requireNonNull(state, "Jigsaw Studio captured block state");
        }
    }

    record CapturedConnector(
            int x,
            int y,
            int z,
            JigsawDirection direction,
            JigsawDirection top,
            String pool,
            String name,
            String targetName,
            String channel,
            JigsawJoint joint,
            String finalState,
            int selectionPriority,
            int placementPriority
    ) {
        CapturedConnector {
            Objects.requireNonNull(direction, "Jigsaw Studio captured connector direction");
            Objects.requireNonNull(top, "Jigsaw Studio captured connector top");
            Objects.requireNonNull(pool, "Jigsaw Studio captured connector pool");
            Objects.requireNonNull(name, "Jigsaw Studio captured connector name");
            Objects.requireNonNull(targetName, "Jigsaw Studio captured connector target");
            channel = channel == null ? "" : channel;
            Objects.requireNonNull(joint, "Jigsaw Studio captured connector joint");
            Objects.requireNonNull(finalState, "Jigsaw Studio captured connector final state");
        }

        static CapturedConnector from(IrisJigsawConnector connector) {
            IrisJigsawConnector source = Objects.requireNonNull(connector, "Jigsaw Studio captured connector");
            BlockPosition position = source.getPosition();
            return new CapturedConnector(
                    position.x(),
                    position.y(),
                    position.z(),
                    source.getDirection(),
                    source.getTop(),
                    source.getPool(),
                    source.getName(),
                    source.getTargetName(),
                    source.getChannel(),
                    source.getJoint(),
                    source.getFinalState(),
                    source.getSelectionPriority(),
                    source.getPlacementPriority());
        }

        IrisJigsawConnector toConnector() {
            return new IrisJigsawConnector()
                    .setPosition(new BlockPosition(x, y, z))
                    .setDirection(direction)
                    .setTop(top)
                    .setPool(pool)
                    .setName(name)
                    .setTargetName(targetName)
                    .setChannel(channel)
                    .setJoint(joint)
                    .setFinalState(finalState)
                    .setSelectionPriority(selectionPriority)
                    .setPlacementPriority(placementPriority);
        }
    }

    record ChunkSnapshot(
            ChunkCaptureArea area,
            List<CapturedBlock> blocks,
            List<CapturedConnector> connectors
    ) {
        ChunkSnapshot {
            Objects.requireNonNull(area, "Jigsaw Studio captured chunk area");
            blocks = List.copyOf(blocks);
            connectors = List.copyOf(connectors);
        }
    }

    record LocalPosition(int x, int y, int z) {
    }

    record Capture(byte[] objectContent, List<IrisJigsawConnector> connectors, boolean hasBlockEntities) {
        Capture {
            objectContent = Objects.requireNonNull(objectContent, "Jigsaw Studio object content").clone();
            connectors = List.copyOf(connectors);
        }

        @Override
        public byte[] objectContent() {
            return objectContent.clone();
        }
    }

    static final class WorkcellTopologyException extends IOException {
        WorkcellTopologyException(String message) {
            super(message);
        }
    }

    private static final class ParticleBudget {
        private final List<Particle> dust = new ArrayList<>();
        private int remaining;

        private ParticleBudget(int remaining) {
            this.remaining = remaining;
        }

        private void add(Particle particle) {
            dust.add(particle);
        }

        private List<Particle> dust() {
            return List.copyOf(dust);
        }

        private boolean consume() {
            if (remaining < 1) {
                return false;
            }
            remaining--;
            return true;
        }

        private boolean empty() {
            return remaining < 1;
        }
    }
}
