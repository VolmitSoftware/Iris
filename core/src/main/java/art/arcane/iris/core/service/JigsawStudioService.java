package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioTripleSneakTracker;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioChunkWriter.BayPopulation;
import art.arcane.iris.core.service.JigsawStudioMaterializer.VariantReloadRequest;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
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
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.iris.core.service.JigsawStudioCaptureScheduler.requiredChunks;
import static art.arcane.iris.core.service.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioProtection.ownerMatches;

public final class JigsawStudioService implements IrisService, JigsawStudioMenuActions {
    static final int AUTOSAVE_RETRY_TICKS = 5;
    static final JigsawStudioPieceRules DEFAULT_PIECE_RULES =
            new JigsawStudioPieceRules(0, 30, 0, 0, false);
    static JigsawStudioService INSTANCE;

    final Map<UUID, ActiveStudio> studios = new ConcurrentHashMap<>();
    final Set<UUID> particlesDisabled = ConcurrentHashMap.newKeySet();
    final JigsawStudioTripleSneakTracker tripleSneakTracker = new JigsawStudioTripleSneakTracker();
    final JigsawStudioPreviewRenderer previewRenderer = new JigsawStudioPreviewRenderer();
    final AtomicBoolean disableStarted = new AtomicBoolean();
    final Object saveLifecycleLock = new Object();
    volatile boolean enabled;

    final JigsawStudioRegistry registry = new JigsawStudioRegistry(this);
    final JigsawStudioSaveLifecycle saveLifecycle = new JigsawStudioSaveLifecycle(this);
    final JigsawStudioVariantEditor variantEditor = new JigsawStudioVariantEditor(this);
    final JigsawStudioVariantProperties variantProperties = new JigsawStudioVariantProperties(this);
    final JigsawStudioWorkcellEditor workcellEditor = new JigsawStudioWorkcellEditor(this);
    final JigsawStudioEvaluator evaluator = new JigsawStudioEvaluator(this);
    final JigsawStudioToolbelt toolbelt = new JigsawStudioToolbelt(this);
    final JigsawStudioGraphMutations graphMutations = new JigsawStudioGraphMutations(this);
    final JigsawStudioProtectionListener protectionListener = new JigsawStudioProtectionListener(this);
    final JigsawStudioProtection protection = new JigsawStudioProtection(this);
    final JigsawStudioMaterializer materializer = new JigsawStudioMaterializer(this);
    final JigsawStudioDirtyTracker dirtyTracker = new JigsawStudioDirtyTracker(this);
    final JigsawStudioCaptureScheduler captureScheduler = new JigsawStudioCaptureScheduler(this);
    final JigsawStudioAutosaveScheduler autosaveScheduler = new JigsawStudioAutosaveScheduler(this);
    final JigsawStudioTileWatcher tileWatcher = new JigsawStudioTileWatcher(this);
    final JigsawStudioVisualization visualization = new JigsawStudioVisualization(this);
    final JigsawStudioPlayerContext playerContext = new JigsawStudioPlayerContext(this);

    public static JigsawStudioService get() {
        JigsawStudioService service = INSTANCE;
        return service == null ? IrisServices.get(JigsawStudioService.class) : service;
    }

    public static void clearAutosaveHistory(Path packRoot, String structureKey) throws IOException {
        new JigsawStudioHistoryStore(packRoot, structureKey).delete();
    }

    @Override
    public void onEnable() {
        disableStarted.set(false);
        enabled = true;
        toolbelt.menuController = new JigsawStudioMenuController(BukkitPlatform.volmitPlugin(), this);
        BukkitPlatform.volmitPlugin().registerListener(protectionListener);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        registry.quiesceForServerShutdown();
    }

    public boolean teleportTo(Player player, String bayId) {
        if (player == null || bayId == null || bayId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> teleportTo(player, bayId));
        }
        ActiveStudio studio = studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (saveLifecycle.reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            message(player, "Close and reopen Jigsaw Studio before loading variants in the resized layout.");
            return false;
        }
        JigsawStudioBay bay = findBay(studio.generator().getSession().layout(), bayId);
        if (bay == null) {
            message(player, "No Jigsaw Studio bay matches '" + bayId + "'.");
            return false;
        }
        JigsawStudioBounds bounds = bay.bounds();
        Location destination = new Location(
                player.getWorld(),
                bounds.originX() + bounds.dimensions().width() / 2.0D,
                bounds.maxY() + 2.0D,
                bounds.originZ() + bounds.dimensions().depth() / 2.0D,
                player.getLocation().getYaw(),
                45.0F
        );
        studio.generator().getSession().selectBay(bay.stableId());
        BukkitPlatform.teleportAsync(player, destination).thenRun(() -> J.runEntity(
                player,
                () -> playerContext.reconcilePlayerContext(player, player.getLocation())));
        message(player, "Selected Jigsaw Studio workcell '" + bay.stableId() + "'.");
        visualization.ensureVisualizationLoop(player);
        return true;
    }

    @Override
    public boolean teleportToWorkcell(Player player, String workcellId) {
        return teleportTo(player, workcellId);
    }

    public boolean setParticles(Player player, boolean visible) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> setParticles(player, visible));
        }
        if (!studios.containsKey(player.getWorld().getUID())) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (visible) {
            particlesDisabled.remove(player.getUniqueId());
            visualization.ensureVisualizationLoop(player);
        } else {
            particlesDisabled.add(player.getUniqueId());
        }
        message(player, "Jigsaw Studio particles " + (visible ? "enabled." : "disabled."));
        return true;
    }

    public boolean previewStudio(Player player, long seed) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> previewStudio(player, seed));
        }
        ActiveStudio studio = studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        Location location = player.getLocation();
        IrisPosition origin = new IrisPosition(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        J.a(() -> {
            try {
                IrisStructure structure = request.source().load(
                        IrisStructure.class, request.structureKey(), false);
                if (structure == null) {
                    throw new IOException("The active structure resource no longer exists");
                }
                StructureAssembler assembler = StructureAssembler.forData(
                        request.source(), structure, origin);
                StructureAssemblyResult assembly = assembler.assemble(new RNG(seed));
                if (!assembly.hasOutput()) {
                    message(player, "The active graph assembled zero preview pieces.");
                    return;
                }
                List<PlacedStructurePiece> pieces = assembly.pieces();
                if (!J.runEntity(player, () -> {
                    if (isCurrentRequest(studio, request.requestId())) {
                        visualization.showAssemblyPreview(player, pieces);
                        message(player, "Showing a " + pieces.size()
                                + "-piece particle preview for 10 seconds.");
                    }
                })) {
                    message(player, "The preview completed after the player session ended.");
                }
            } catch (Throwable exception) {
                IrisLogging.reportError(exception);
                message(player, "Preview assembly failed: " + failureMessage(exception));
            }
        });
        return true;
    }

    public Optional<JigsawStudioGraphEvaluation> evaluation(Player player) {
        if (player == null || !J.isOwnedByCurrentRegion(player)) {
            return Optional.empty();
        }
        ActiveStudio studio = studios.get(player.getWorld().getUID());
        if (studio == null || !ownerMatches(player, studio)) {
            return Optional.empty();
        }
        return Optional.ofNullable(evaluator.evaluations.get(studio.generator().getRequest().requestId()));
    }

    public boolean goToPreview(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> goToPreview(player));
        }
        ActiveStudio studio = studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        JigsawStudioPreviewRenderer.PreviewBounds bounds = previewRenderer.bounds(requestId);
        if (bounds == null || bounds.isEmpty()) {
            JigsawStudioGraphEvaluation evaluation = evaluator.evaluations.get(requestId);
            message(player, evaluation == null
                    ? "The seed-1337 preview is still being evaluated."
                    : "The seed-1337 preview has no generated blocks: " + evaluation.detail());
            return false;
        }
        Location destination = new Location(
                studio.world(),
                bounds.centerX() + 0.5D,
                bounds.maximumY() + 2.0D,
                bounds.centerZ() + 0.5D,
                player.getLocation().getYaw(),
                player.getLocation().getPitch());
        BukkitPlatform.teleportAsync(player, destination).thenRun(() -> J.runEntity(
                player,
                () -> message(player, "Teleported to the live seed-1337 preview.")));
        return true;
    }

    public boolean giveTool(Player player, JigsawStudioToolPayload payload) {
        if (player == null || payload == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> giveTool(player, payload));
        }
        ActiveStudio studio = studios.get(player.getWorld().getUID());
        if (studio == null
                || !studio.generator().getRequest().requestId().equals(payload.requestId())
                || !authorizeOwner(player, studio)) {
            message(player, "This tool belongs to a different or closed Jigsaw Studio session.");
            return false;
        }
        ItemStack tool = toolbelt.toolCodec.create(payload);
        Map<Integer, ItemStack> rejected = player.getInventory().addItem(tool);
        if (!rejected.isEmpty()) {
            message(player, "Your inventory is full; no Jigsaw Studio tool was added.");
            return false;
        }
        message(player, "Added " + payload.action().displayName() + " tool.");
        return true;
    }

    void markEvaluationStale(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        evaluator.evaluations.computeIfPresent(
                requestId,
                (ignored, current) -> current.state() == JigsawStudioEvaluationState.STALE
                        ? current
                        : current.stale("Autosave is pending for one or more edited workcells"));
    }

    boolean isCurrentRequest(ActiveStudio studio, UUID requestId) {
        if (studio == null
                || requestId == null
                || studios.get(studio.worldId()) != studio
                || !studio.generator().getRequest().requestId().equals(requestId)) {
            return false;
        }
        JigsawStudioActivation.Request active = JigsawStudioActivation.getRequest(
                studio.generator().getRequest().packKey());
        return active != null && active.requestId().equals(requestId);
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    void reloadSessionLayout(ActiveStudio studio) throws IOException {
        JigsawStudioLayout layout = loadMappedLayout(studio);
        studio.generator().getSession().replaceLayout(layout);
        for (JigsawStudioBay workcell : layout.bays()) {
            studio.generator().invalidateRender(workcell.stableId());
        }
    }

    static boolean isNaturalStudioSpawn(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL;
    }

    static void disableNaturalStudioSpawning(World world) {
        Objects.requireNonNull(world, "world").setGameRule(GameRules.SPAWN_MOBS, false);
    }

    static boolean sameBlockPosition(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    static JigsawStudioBay findBay(JigsawStudioLayout layout, String requested) {
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

    static String failureMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    static void message(Player player, String text) {
        if (player != null) {
            J.runEntity(player, () -> ComponentMessenger.sendLiteral(player, "[Iris Jigsaw Studio] " + text));
        }
    }

    static void playSaveSound(Player player) {
        if (player != null) {
            J.runEntity(player, () -> player.playSound(
                    player.getLocation(),
                    "minecraft:block.note_block.bell",
                    0.65F,
                    1.65F));
        }
    }

    static void report(Player player, boolean enabled, String text) {
        if (enabled) {
            message(player, text);
        }
    }

    public void quiesceForServerShutdown() {
        registry.quiesceForServerShutdown();
    }

    public void register(Engine engine, JigsawStudioGenerator generator) {
        registry.register(engine, generator);
    }

    public void activationCommitted(World world, UUID requestId) {
        registry.activationCommitted(world, requestId);
    }

    public void markChunkGenerated(
            Engine engine,
            JigsawStudioGenerator generator,
            int chunkX,
            int chunkZ
    ) {
        registry.markChunkGenerated(engine, generator, chunkX, chunkZ);
    }

    public void unregister(World world) {
        registry.unregister(world);
    }

    public CloseStart tryBeginClose(UUID requestId, UUID ownerId, boolean discard) {
        return saveLifecycle.tryBeginClose(requestId, ownerId, discard);
    }

    public String closeProtectionFailure(UUID requestId) {
        return saveLifecycle.closeProtectionFailure(requestId);
    }

    public CompletableFuture<Void> awaitCloseForReplacement(UUID requestId, UUID ownerId) {
        return saveLifecycle.awaitCloseForReplacement(requestId, ownerId);
    }

    public void cancelClose(UUID requestId) {
        saveLifecycle.cancelClose(requestId);
    }

    public ExportStart tryBeginExport(UUID requestId, UUID ownerId) {
        return saveLifecycle.tryBeginExport(requestId, ownerId);
    }

    public void finishExport(UUID requestId) {
        saveLifecycle.finishExport(requestId);
    }

    public boolean saveSelected(Player player) {
        return saveLifecycle.saveSelected(player);
    }

    public boolean saveBay(Player player, String bayId) {
        return saveLifecycle.saveBay(player, bayId);
    }

    public boolean switchVariant(
            Player player,
            String workcellId,
            String targetPieceKey,
            boolean discardDirty
    ) {
        return variantEditor.switchVariant(player, workcellId, targetPieceKey, discardDirty);
    }

    public boolean createVariant(
            Player player,
            String workcellId,
            boolean duplicateActive
    ) {
        return variantEditor.createVariant(player, workcellId, duplicateActive);
    }

    @Override
    public boolean duplicateActiveFamily(Player player, String themeKey) {
        return variantEditor.duplicateActiveFamily(player, themeKey);
    }

    @Override
    public boolean deleteVariant(Player player, String workcellId, String pieceKey) {
        return variantEditor.deleteVariant(player, workcellId, pieceKey);
    }

    public boolean deleteProject(Player player) {
        return variantEditor.deleteProject(player);
    }

    @Override
    public boolean unlinkVariantMembership(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex
    ) {
        return variantEditor.unlinkVariantMembership(player, workcellId, pieceKey, poolKey, entryIndex);
    }

    @Override
    public boolean updateVariantThemes(
            Player player,
            String workcellId,
            String pieceKey,
            List<String> themes
    ) {
        return variantProperties.updateVariantThemes(player, workcellId, pieceKey, themes);
    }

    @Override
    public boolean updateVariantRules(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioPieceRules rules
    ) {
        return variantProperties.updateVariantRules(player, workcellId, pieceKey, rules);
    }

    public boolean toggleVariantRotatable(Player player, String workcellId) {
        return variantProperties.toggleVariantRotatable(player, workcellId);
    }

    public boolean expandVariantToCell(Player player, String workcellId) {
        return variantProperties.expandVariantToCell(player, workcellId);
    }

    @Override
    public boolean resizeVariant(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) {
        return variantProperties.resizeVariant(player, workcellId, pieceKey, dimensions);
    }

    @Override
    public boolean updateVariantDisplayName(
            Player player,
            String workcellId,
            String pieceKey,
            String displayName
    ) {
        return variantProperties.updateVariantDisplayName(player, workcellId, pieceKey, displayName);
    }

    @Override
    public boolean adjustVariantWeight(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int delta
    ) {
        return variantProperties.adjustVariantWeight(player, workcellId, pieceKey, poolKey, entryIndex, delta);
    }

    @Override
    public boolean adjustVariantChance(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int deltaPercentagePoints
    ) {
        return variantProperties.adjustVariantChance(
                player, workcellId, pieceKey, poolKey, entryIndex, deltaPercentagePoints);
    }

    @Override
    public boolean setConnectorBlocksVisible(Player player, String workcellId, boolean visible) {
        return workcellEditor.setConnectorBlocksVisible(player, workcellId, visible);
    }

    @Override
    public boolean resetConnectorBlocks(Player player, String workcellId) {
        return workcellEditor.resetConnectorBlocks(player, workcellId);
    }

    @Override
    public boolean setWorkcellEnabled(Player player, String workcellId, boolean enabled) {
        return workcellEditor.setWorkcellEnabled(player, workcellId, enabled);
    }

    @Override
    public boolean updateWorkcellDimensions(
            Player player,
            String workcellId,
            JigsawStudioCellDimensions dimensions
    ) {
        return workcellEditor.updateWorkcellDimensions(player, workcellId, dimensions);
    }

    @Override
    public boolean setRequireCaps(Player player, boolean requireCaps) {
        return workcellEditor.setRequireCaps(player, requireCaps);
    }

    @Override
    public boolean updateThemeSetWeight(Player player, String themeKey, int weight) {
        return workcellEditor.updateThemeSetWeight(player, themeKey, weight);
    }

    @Override
    public boolean updateWorkcellDisplayName(
            Player player,
            String workcellId,
            String displayName
    ) {
        return workcellEditor.updateWorkcellDisplayName(player, workcellId, displayName);
    }

    public Optional<JigsawStudioMenuState> menuState(Player player) {
        return toolbelt.menuState(player);
    }

    public boolean selectWorkcell(Player player, String workcellId) {
        return toolbelt.selectWorkcell(player, workcellId);
    }

    public boolean openControlMenu(Player player) {
        return toolbelt.openControlMenu(player);
    }

    public void closeMenu(Player player) {
        toolbelt.closeMenu(player);
    }

    public boolean runCommandGraphMutation(
            Player player,
            UUID expectedRequestId,
            CommandGraphMutation task
    ) {
        return graphMutations.runCommandGraphMutation(player, expectedRequestId, task);
    }

    public boolean markDirty(World world, int worldX, int worldY, int worldZ) {
        return dirtyTracker.markDirty(world, worldX, worldY, worldZ);
    }

    public int markAllDirty(World world) {
        return dirtyTracker.markAllDirty(world);
    }

    @Override
    public boolean undoAutosave(Player player) {
        return autosaveScheduler.undoAutosave(player);
    }

    public boolean flushAutosave(Player player, String workcellId) {
        return autosaveScheduler.flushAutosave(player, workcellId);
    }

    public boolean showAssemblyPreview(Player player, List<PlacedStructurePiece> pieces) {
        return visualization.showAssemblyPreview(player, pieces);
    }

    public void reconcilePlayerContext(Player player, Location location) {
        playerContext.reconcilePlayerContext(player, location);
    }

    public void refreshWorkcellContext(UUID worldId, String workcellId) {
        playerContext.refreshWorkcellContext(worldId, workcellId);
    }

    record ActiveStudio(
            UUID worldId,
            World world,
            Engine engine,
            JigsawStudioGenerator generator,
            ConcurrentHashMap<String, BayPopulation> populations,
            Set<Long> hydrationsInProgress,
            AtomicLong evaluationGeneration
    ) {
        ActiveStudio {
            Objects.requireNonNull(worldId, "Jigsaw Studio world ID");
            Objects.requireNonNull(world, "Jigsaw Studio world");
            Objects.requireNonNull(engine, "Jigsaw Studio engine");
            Objects.requireNonNull(generator, "Jigsaw Studio generator");
            Objects.requireNonNull(populations, "Jigsaw Studio bay populations");
            Objects.requireNonNull(hydrationsInProgress, "Jigsaw Studio hydrations");
            Objects.requireNonNull(evaluationGeneration, "Jigsaw Studio evaluation generation");
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

    public enum CloseStart {
        STARTED,
        NOT_ACTIVE,
        NOT_OWNER,
        DIRTY,
        SAVE_IN_PROGRESS,
        OPERATION_IN_PROGRESS
    }

}
