package art.arcane.iris.core.commands;

import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.BrokenPackException;
import art.arcane.iris.core.runtime.WorldRuntimeControlService;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioProjectCreator;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioStructureEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioGraphEvaluation;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.structure.StructureImporter;
import art.arcane.iris.core.structure.VillageImporter;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionDiagnostic;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionInputKind;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionPlan;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionRequest;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionResult;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionService;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionStrategy;
import art.arcane.iris.core.structure.export.VanillaJigsawDatapackExporter;
import art.arcane.iris.core.structure.export.VanillaJigsawExportDiagnostic;
import art.arcane.iris.core.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.core.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.core.structure.export.VanillaJigsawExportResult;
import art.arcane.iris.core.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.engine.framework.structure.PlanarJigsawWorkcellResolver;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.iris.util.common.director.specialhandlers.IrisStructureHandler;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Jigsaw;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Director(name = "jigsaw", aliases = {"jig", "jgs"}, description = "Iris Jigsaw Studio tools",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsaw implements DirectorExecutor {
    private static final long DEFAULT_STUDIO_SEED = 1337L;
    static final StudioOpenCoordinator.StudioOpenKind STUDIO_OPEN_KIND =
            StudioOpenCoordinator.StudioOpenKind.JIGSAW;
    private static final Pattern EXPORT_OUTPUT_NAME = Pattern.compile("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,127}");
    private static final Map<UUID, SessionBinding> PLAYER_PACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, AdoptionPlanBinding> ADOPTION_PLANS = new ConcurrentHashMap<>();
    private static final Map<Path, IrisStructureAdoptionService> ADOPTION_SERVICES = new ConcurrentHashMap<>();
    private static final Set<UUID> EXPORTING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<Path> EXPORT_DESTINATIONS = ConcurrentHashMap.newKeySet();

    private CommandJigsawPiece piece;
    private CommandJigsawPool pool;
    private CommandJigsawConnector connector;
    private CommandJigsawVariant variant;
    private CommandJigsawWorkcell workcell;
    private CommandJigsawRules rules;
    private CommandJigsawPreview preview;
    private CommandJigsawAdopt adopt;

    @Director(description = "Create and open a new atomic Jigsaw Studio project", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void create(
            @Param(description = "Pack dimension") IrisDimension dimension,
            @Param(name = "key", aliases = {"structure", "name"},
                    description = "New key written as structures/<key>.json") String structure,
            @Param(description = "planar or spatial", defaultValue = "planar",
                    customHandler = JigsawModeHandler.class) String mode,
            @Param(description = "iris or vanilla", defaultValue = "iris",
                    customHandler = JigsawCompatibilityHandler.class) String compatibility,
            @Param(description = "Cell width", defaultValue = "15") int width,
            @Param(description = "Cell height", defaultValue = "15") int height,
            @Param(description = "Cell depth", defaultValue = "15") int depth,
            @Param(description = "Studio world seed", defaultValue = "1337") long seed
    ) {
        IrisData data = requireData(dimension);
        if (data == null) {
            return;
        }
        JigsawStudioMode studioMode;
        JigsawStudioCompatibilityTarget target;
        JigsawStudioCellDimensions dimensions;
        try {
            studioMode = parseMode(mode);
            target = parseCompatibility(compatibility);
            dimensions = new JigsawStudioCellDimensions(width, height, depth);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }

        StructureWriteResult result;
        try {
            result = JigsawStudioProjectCreator.create(
                    data.getDataFolder().toPath(),
                    new JigsawStudioProjectCreator.Options(structure, studioMode, target, dimensions));
        } catch (IOException | RuntimeException exception) {
            Iris.reportError("Failed to create Jigsaw Studio project '" + structure + "'.", exception);
            sendError("Jigsaw project creation failed: " + exception.getMessage());
            return;
        }
        if (!result.successful()) {
            sendError("Jigsaw project was not created: " + result.status());
            if (!result.conflicts().isEmpty()) {
                sendError(result.conflicts().getFirst().relativePath() + ": "
                        + result.conflicts().getFirst().reason());
            }
            return;
        }
        try {
            JigsawStudioService.clearAutosaveHistory(
                    data.getDataFolder().toPath(),
                    structure);
        } catch (IOException exception) {
            Iris.reportError("Failed to clear stale Jigsaw Studio history for '" + structure + "'.", exception);
            sendError("Jigsaw project was created, but stale autosave history could not be cleared: "
                    + exception.getMessage());
            return;
        }
        data.invalidateStructureResources();
        sender().sendMessage(C.GREEN + "Created Jigsaw project '" + structure + "' atomically.");
        open(dimension, structure, seed);
    }

    @Director(description = "Import a registered vanilla or datapack jigsaw graph into Iris Studio",
            aliases = {"import", "import-vanilla"}, sync = true, origin = DirectorOrigin.PLAYER)
    public void convert(
            @Param(description = "Pack dimension") IrisDimension dimension,
            @Param(description = "Registered structure key such as minecraft:village_plains") String source,
            @Param(description = "New Iris structure key, or auto", defaultValue = "auto") String target,
            @Param(description = "Studio world seed", defaultValue = "1337") long seed
    ) {
        if (studioIsActiveOrOpening()) {
            sendError("Close the active or opening Jigsaw Studio before importing another graph.");
            return;
        }
        IrisData data = requireData(dimension);
        if (data == null) {
            return;
        }
        NamespacedKey sourceKey;
        String targetKey;
        try {
            sourceKey = parseRegisteredStructureKey(source);
            targetKey = resolveConversionTarget(sourceKey, target);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }

        VillageImporter.Result result = VillageImporter.importVillage(
                data,
                sourceKey,
                targetKey,
                StructureImporter.Mode.ADD_ONLY);
        if (!result.success()) {
            sendError(conversionFailureMessage(result));
            return;
        }
        data.invalidateStructureResources();
        VolmitSender commandSender = sender();
        commandSender.sendMessage(C.GREEN + result.message());
        openProject(player(), commandSender, dimension, targetKey, seed);
    }

    @Director(description = "Open an existing Iris jigsaw graph in transient Studio",
            aliases = {"edit", "reopen"}, sync = true, origin = DirectorOrigin.PLAYER)
    public void open(
            @Param(description = "Pack dimension") IrisDimension dimension,
            @Param(name = "key", aliases = {"structure", "name"},
                    description = "Existing key loaded from structures/<key>.json",
                    customHandler = IrisStructureHandler.class) String structure,
            @Param(description = "Studio world seed", defaultValue = "1337") long seed
    ) {
        openProject(player(), sender(), dimension, structure, seed);
    }

    private static void openProject(
            Player targetPlayer,
            VolmitSender commandSender,
            IrisDimension dimension,
            String structure,
            long seed
    ) {
        UUID playerId = targetPlayer.getUniqueId();
        UUID ownerId = JigsawStudioActivation.activeOwnerId();
        if (ownerId != null && !ownerId.equals(playerId)) {
            sendError(commandSender, "Jigsaw Studio is already owned by another player session.");
            return;
        }
        UUID openingOwner = JigsawStudioActivation.openingOwnerId();
        if (openingOwner != null) {
            sendError(commandSender, openingOwner.equals(playerId)
                    ? "Your Jigsaw Studio is already opening."
                    : "Jigsaw Studio is already opening for another player.");
            return;
        }
        ActiveContext previous = active(targetPlayer);
        if (ownerId != null && previous == null) {
            sendError(commandSender, "Your active Jigsaw Studio binding is unavailable. Close it before opening another project.");
            return;
        }
        ActiveContext dirty = findDirtyContext();
        if (dirty != null) {
            sendError(commandSender,
                    "The current Jigsaw Studio is waiting for autosave. Wait for it to finish or close with discard=true.");
            return;
        }
        IrisData data = requireData(dimension, commandSender);
        if (data == null) {
            return;
        }
        IrisStructure irisStructure = StructureResources.structure(data.getPackResources(), structure);
        if (irisStructure == null) {
            sendError(commandSender, "No Iris jigsaw structure '" + structure + "' exists in this pack.");
            return;
        }

        JigsawStudioLayout layout;
        try {
            layout = JigsawStudioGraphMapper.map(data, irisStructure);
        } catch (RuntimeException exception) {
            Iris.reportError("Failed to map Jigsaw Studio graph '" + structure + "'.", exception);
            sendError(commandSender, "Could not map this jigsaw graph: " + exception.getMessage());
            return;
        }
        String packKey = dimension.getLoadKey();
        if (!JigsawStudioActivation.tryBeginOpen(playerId)) {
            sendError(commandSender,
                    "Jigsaw Studio began opening for another player. Try again after it finishes.");
            return;
        }
        JigsawStudioService studioService = JigsawStudioService.get();
        UUID closingRequestId = previous == null ? null : previous.request().requestId();
        if (closingRequestId != null) {
            JigsawStudioService.CloseStart closeStart = studioService.tryBeginClose(
                    closingRequestId,
                    playerId,
                    false);
            if (closeStart != JigsawStudioService.CloseStart.STARTED) {
                JigsawStudioActivation.finishOpen(playerId);
                sendError(commandSender, closeStart == JigsawStudioService.CloseStart.SAVE_IN_PROGRESS
                        ? "The current Jigsaw Studio is saving. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS
                        ? "The current Jigsaw Studio is loading a variant. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.DIRTY
                        ? "The current Jigsaw Studio is waiting for autosave. Wait for it to finish before opening another project."
                        : "The current Jigsaw Studio could not be reserved for replacement.");
                return;
            }
        }
        JigsawStudioActivation.StagedActivation staged;
        try {
            staged = JigsawStudioActivation.stage(
                    packKey,
                    structure,
                    layout.mode(),
                    compatibilityOf(irisStructure),
                    layout.cellDimensions(),
                    data,
                    layout,
                    playerId,
                    closingRequestId);
        } catch (RuntimeException exception) {
            JigsawStudioActivation.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            Iris.reportError("Failed to stage Jigsaw Studio for '" + structure + "'.", exception);
            sendError(commandSender, "Jigsaw Studio staging failed: " + exception.getMessage());
            return;
        }
        commandSender.sendMessage(C.GREEN + "Opening transient Jigsaw Studio for '" + structure + "' with "
                + layout.bays().size() + " workcells.");

        try {
            Iris.service(StudioSVC.class).openTracked(
                            commandSender,
                            seed,
                            packKey,
                            STUDIO_OPEN_KIND,
                            () -> beginStagedOpen(staged),
                            world -> finishOpen(targetPlayer, world, staged, studioService))
                    .whenComplete((result, throwable) -> J.s(() -> finishOpenAttempt(
                            commandSender,
                            playerId,
                            structure,
                            closingRequestId,
                            studioService,
                            staged,
                            result,
                            throwable)));
        } catch (Throwable exception) {
            JigsawStudioActivation.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            JigsawStudioActivation.rollback(staged);
            Iris.reportError("Failed to open Jigsaw Studio for '" + structure + "'.", exception);
            sendError(commandSender, "Jigsaw Studio open failed: " + exception.getMessage());
        }
    }

    @Director(description = "Close the active Jigsaw Studio", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void close(
            @Param(description = "Discard unsaved Studio layout changes", defaultValue = "false") boolean discard
    ) {
        Player targetPlayer = player();
        ActiveContext context = active(targetPlayer);
        if (context == null) {
            sendError("You do not have an active Jigsaw Studio session.");
            return;
        }
        if (context.session().isDirty() && !discard) {
            sendError("This Jigsaw Studio is waiting for autosave. Wait for it to finish or run close with discard=true.");
            return;
        }
        JigsawStudioService studioService = JigsawStudioService.get();
        UUID requestId = context.request().requestId();
        JigsawStudioService.CloseStart closeStart = studioService.tryBeginClose(
                requestId,
                targetPlayer.getUniqueId(),
                discard);
        if (closeStart != JigsawStudioService.CloseStart.STARTED) {
            sendError(closeStart == JigsawStudioService.CloseStart.SAVE_IN_PROGRESS
                    ? "This Jigsaw Studio is saving. Wait for the save to finish before closing it."
                    : closeStart == JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS
                    ? "This Jigsaw Studio is loading a variant. Wait for it to finish before closing it."
                    : closeStart == JigsawStudioService.CloseStart.DIRTY
                    ? "This Jigsaw Studio is waiting for autosave. Wait for it to finish or run close with discard=true."
                    : closeStart == JigsawStudioService.CloseStart.NOT_OWNER
                    ? "This Jigsaw Studio is owned by another player session."
                    : "This Jigsaw Studio session is no longer active.");
            return;
        }
        VolmitSender commandSender = sender();
        try {
            Iris.service(StudioSVC.class).close().whenComplete((result, throwable) -> J.s(() -> {
                if (throwable != null || result == null || result.failureCause() != null) {
                    studioService.cancelClose(requestId);
                    Throwable failure = throwable != null ? throwable : result == null ? null : result.failureCause();
                    commandSender.sendMessage(C.RED + "Jigsaw Studio close failed: "
                            + (failure == null ? "unknown failure" : failure.getMessage()));
                    return;
                }
                JigsawStudioActivation.deactivate(context.packKey(), requestId);
                PLAYER_PACKS.remove(targetPlayer.getUniqueId(),
                        new SessionBinding(context.packKey(), requestId));
                commandSender.sendMessage(C.GREEN + "Jigsaw Studio closed.");
            }));
        } catch (Throwable exception) {
            studioService.cancelClose(requestId);
            Iris.reportError("Failed to close Jigsaw Studio '" + context.request().structureKey() + "'.", exception);
            sendError("Jigsaw Studio close failed: " + exception.getMessage());
        }
    }

    @Director(name = "delete", aliases = "remove", description = "Delete the active owned jigsaw project",
            sync = true, origin = DirectorOrigin.PLAYER)
    public void delete(
            @Param(description = "Confirm permanent project deletion", defaultValue = "false") boolean confirm
    ) {
        if (requireActive() == null) {
            return;
        }
        if (!confirm) {
            sendError("Project deletion removes every resource owned by this jigsaw. Run again with confirm=true.");
            return;
        }
        JigsawStudioService.get().deleteProject(player());
    }

    @Director(description = "Show active Jigsaw Studio and dynamic evaluation state", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void status() {
        ActiveContext context = requireActive();
        if (context == null) {
            return;
        }
        JigsawStudioLayout layout = context.session().layout();
        String selection = context.session().selectedBayId().orElse("none");
        JigsawStudioVariant activeVariant = "none".equals(selection)
                ? null
                : context.session().activeVariant(selection).orElse(null);
        JigsawStudioBay selectedBay = "none".equals(selection) ? null : layout.get(selection);
        JigsawStudioCellDimensions selectedDimensions = selectedBay == null
                ? null
                : selectedBay.bounds().dimensions();
        Optional<JigsawStudioGraphEvaluation> evaluation = JigsawStudioService.get().evaluation(player());
        sender().sendMessage(C.AQUA + "Jigsaw Studio: " + context.request().structureKey());
        sender().sendMessage(C.GRAY + "mode=" + layout.mode()
                + ", compatibility=" + context.request().compatibilityTarget()
                + ", defaultCell=" + layout.cellDimensions().width() + "x"
                + layout.cellDimensions().height() + "x" + layout.cellDimensions().depth()
                + ", workcells=" + layout.bays().size()
                + ", variants=" + layout.variantCatalog().size());
        sender().sendMessage(C.GRAY + "selected=" + selection
                + ", activeVariant=" + (activeVariant == null ? "none" : activeVariant.pieceKey())
                + ", size=" + (selectedDimensions == null ? "none" : selectedDimensions.width() + "x"
                + selectedDimensions.height() + "x" + selectedDimensions.depth())
                + ", enabled=" + (selectedBay == null ? "n/a" : selectedBay.enabled())
                + ", autosavePending=" + context.session().isDirty());
        if (evaluation.isPresent()) {
            JigsawStudioGraphEvaluation current = evaluation.get();
            sender().sendMessage(C.GRAY + "evaluation=" + current.state()
                    + ", seed=" + current.seed()
                    + ", theme=" + (current.selectedTheme().isBlank() ? "unthemed" : current.selectedTheme())
                    + ", pieces=" + current.pieceCount()
                    + ", detail=" + current.detail());
        } else {
            sender().sendMessage(C.GRAY + "evaluation=PENDING, seed=" + DEFAULT_STUDIO_SEED);
        }
    }

    @Director(description = "Open the Jigsaw Studio workcell and variant menu", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void menu() {
        if (requireActive() != null) {
            JigsawStudioService.get().openControlMenu(player());
        }
    }

    @Director(description = "Select the Jigsaw Studio workcell containing you", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void select() {
        ActiveContext context = requireActive();
        if (context == null) {
            return;
        }
        Location location = player().getLocation();
        JigsawStudioBay bay = context.session().layout().findAt(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (bay == null) {
            sendError("Stand inside a Jigsaw Studio workcell before selecting it.");
            return;
        }
        context.session().selectBay(bay.stableId());
        sender().sendMessage(C.GREEN + "Selected " + bay.stableId() + " (" + bay.kind() + ").");
    }

    @Director(name = "bounds", aliases = {"cell", "resize"},
            description = "Set the selected Studio workcell capacity",
            sync = true, origin = DirectorOrigin.PLAYER)
    public void bounds(
            @Param(description = "Cell width") int width,
            @Param(description = "Cell height") int height,
            @Param(description = "Cell depth") int depth
    ) {
        ActiveContext context = requireActive();
        if (context == null) {
            return;
        }
        JigsawStudioBay selected = selectedBay(context);
        if (selected == null) {
            sendError("Select a Jigsaw Studio workcell before changing its capacity.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = new JigsawStudioCellDimensions(width, height, depth);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }
        if (dimensions.equals(selected.capacity())) {
            sender().sendMessage(C.YELLOW + "Workcell capacity is unchanged.");
            return;
        }
        JigsawStudioService.get().updateWorkcellDimensions(player(), selected.stableId(), dimensions);
    }

    @Director(description = "Flush the automatic save for a workcell now", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void save(
            @Param(description = "Workcell ID or selected", defaultValue = "selected") String bay
    ) {
        ActiveContext context = requireActive();
        if (context == null) {
            return;
        }
        if ("selected".equalsIgnoreCase(bay)) {
            JigsawStudioBay selected = selectedBay(context);
            if (selected == null) {
                sendError("Select a Jigsaw Studio workcell before flushing its autosave.");
                return;
            }
            JigsawStudioService.get().flushAutosave(player(), selected.stableId());
            return;
        }
        JigsawStudioService.get().flushAutosave(player(), bay);
    }

    @Director(name = "goto", aliases = "teleport", description = "Select and teleport to a Studio workcell",
            sync = true, origin = DirectorOrigin.PLAYER)
    public void gotoBay(
            @Param(description = "Workcell ID") String bay
    ) {
        if (requireActive() != null) {
            JigsawStudioService.get().teleportTo(player(), bay);
        }
    }

    @Director(description = "Enable or disable Jigsaw Studio particles", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void particles(
            @Param(description = "Whether particles are visible") boolean visible
    ) {
        if (requireActive() != null) {
            JigsawStudioService.get().setParticles(player(), visible);
        }
    }

    @Director(description = "Export the clean active graph as a vanilla datapack", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void export(
            @Param(description = "Datapack namespace", defaultValue = "iris") String namespace,
            @Param(description = "Output name under the Studio exports folder", defaultValue = "jigsaw-export")
            String output,
            @Param(description = "directory or zip", defaultValue = "zip") String format,
            @Param(description = "Replace an existing export", defaultValue = "false") boolean replace
    ) {
        ActiveContext context = requireActive();
        if (context == null) {
            return;
        }
        if (context.session().isDirty()) {
            sendError("Wait for the pending autosave or discard the edits before exporting the on-disk graph.");
            return;
        }
        VanillaJigsawExportFormat exportFormat;
        try {
            exportFormat = parseFormat(format);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }
        Path exportRoot = new File(Iris.service(StudioSVC.class).getWorkspaceFolder(), "exports")
                .toPath().toAbsolutePath().normalize();
        Path destination;
        try {
            destination = resolveExportDestination(exportRoot, output, exportFormat);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }
        VanillaJigsawExportRequest request;
        try {
            request = VanillaJigsawExportRequest.builder(
                            VanillaJigsawExportSource.forData(
                                    context.request().source(), context.request().structureKey()), destination)
                    .namespace(namespace)
                    .resourcePath(context.request().structureKey())
                    .format(exportFormat)
                    .replaceExisting(replace)
                    .build();
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }
        Player targetPlayer = player();
        UUID playerId = targetPlayer.getUniqueId();
        UUID requestId = context.request().requestId();
        JigsawStudioService studioService = JigsawStudioService.get();
        JigsawStudioService.ExportStart exportStart = studioService.tryBeginExport(requestId, playerId);
        if (exportStart != JigsawStudioService.ExportStart.STARTED) {
            sendError(exportStartError(exportStart));
            return;
        }
        if (!beginExport(playerId, destination)) {
            studioService.finishExport(requestId);
            sendError("A vanilla Jigsaw export for this player or output is already in progress.");
            return;
        }
        ExportLease lease = new ExportLease(() -> {
            finishExport(playerId, destination);
            studioService.finishExport(requestId);
        });
        ExportOperation operation = new ExportOperation(
                targetPlayer,
                sender(),
                requestId,
                context.request().structureKey(),
                request,
                lease);
        sender().sendMessage(C.GRAY + "Exporting '" + operation.structureKey()
                + "' in the background to " + destination + "...");
        try {
            J.a(() -> runExport(operation));
        } catch (RuntimeException exception) {
            operation.lease().release();
            Iris.reportError("Failed to schedule vanilla Jigsaw export for '"
                    + operation.structureKey() + "'.", exception);
            sendError("Vanilla export could not be scheduled: " + exception.getMessage());
        }
    }

    static String exportStartError(JigsawStudioService.ExportStart exportStart) {
        Objects.requireNonNull(exportStart, "Jigsaw Studio export start status");
        return switch (exportStart) {
            case NOT_ACTIVE -> "The active Jigsaw Studio is no longer available.";
            case NOT_OWNER -> "Only the Jigsaw Studio owner can export this project.";
            case DIRTY -> "Wait for the pending autosave or discard the edits before exporting the on-disk graph.";
            case CLOSING -> "The active Jigsaw Studio is closing and cannot be exported.";
            case SAVE_IN_PROGRESS -> "Wait for the current Jigsaw Studio save to finish before exporting.";
            case OPERATION_IN_PROGRESS -> "Wait for the current Jigsaw Studio operation to finish before exporting.";
            case IN_PROGRESS -> "A Jigsaw Studio export is already in progress.";
            case STARTED -> throw new IllegalArgumentException("STARTED is not an export failure");
        };
    }

    static boolean beginExport(UUID playerId, Path destination) {
        Objects.requireNonNull(playerId);
        Path normalizedDestination = Objects.requireNonNull(destination).toAbsolutePath().normalize();
        if (!EXPORTING_PLAYERS.add(playerId)) {
            return false;
        }
        if (EXPORT_DESTINATIONS.add(normalizedDestination)) {
            return true;
        }
        EXPORTING_PLAYERS.remove(playerId);
        return false;
    }

    static void finishExport(UUID playerId, Path destination) {
        EXPORTING_PLAYERS.remove(Objects.requireNonNull(playerId));
        EXPORT_DESTINATIONS.remove(Objects.requireNonNull(destination).toAbsolutePath().normalize());
    }

    private static void pruneAdoptionPlanBindings() {
        for (Map.Entry<UUID, AdoptionPlanBinding> entry : ADOPTION_PLANS.entrySet()) {
            AdoptionPlanBinding binding = entry.getValue();
            if (binding.service().plan(entry.getKey()).isEmpty()) {
                ADOPTION_PLANS.remove(entry.getKey(), binding);
            }
        }
    }

    private static boolean studioIsActiveOrOpening() {
        return JigsawStudioActivation.activeOwnerId() != null
                || JigsawStudioActivation.openingOwnerId() != null;
    }

    private static void runAdoptionApply(
            Player targetPlayer,
            VolmitSender commandSender,
            UUID planId,
            AdoptionPlanBinding binding
    ) {
        if (studioIsActiveOrOpening()) {
            ADOPTION_PLANS.putIfAbsent(planId, binding);
            J.runEntity(targetPlayer, () -> sendError(commandSender,
                    "Close the active or opening Jigsaw Studio before applying an adoption plan."));
            return;
        }
        IrisStructureAdoptionResult result;
        Throwable failure = null;
        try {
            result = binding.service().apply(binding.plan());
        } catch (Throwable throwable) {
            result = null;
            failure = throwable;
            Iris.reportError("Failed to apply Jigsaw adoption plan '" + planId + "'.", throwable);
        }
        IrisStructureAdoptionResult completedResult = result;
        Throwable completedFailure = failure;
        J.runEntity(targetPlayer, () -> finishAdoptionApply(
                targetPlayer,
                commandSender,
                binding,
                completedResult,
                completedFailure));
    }

    private static void runAdoptionInspect(
            Player targetPlayer,
            VolmitSender commandSender,
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            Path root,
            String sourceKey,
            Optional<StructureKey> targetKey,
            IrisStructureAdoptionStrategy strategy
    ) {
        IrisStructureAdoptionService service = null;
        IrisStructureAdoptionPlan plan = null;
        Throwable failure = null;
        try {
            IrisStructureAdoptionInputKind inputKind = adoptionInputKind(root, sourceKey);
            service = ADOPTION_SERVICES.computeIfAbsent(root, IrisStructureAdoptionService::new);
            plan = service.inspect(new IrisStructureAdoptionRequest(
                    sourceKey,
                    targetKey,
                    strategy,
                    inputKind));
        } catch (Throwable throwable) {
            failure = throwable;
            Iris.reportError("Failed to inspect Jigsaw adoption source '" + sourceKey + "'.", throwable);
        }
        IrisStructureAdoptionService completedService = service;
        IrisStructureAdoptionPlan completedPlan = plan;
        Throwable completedFailure = failure;
        J.runEntity(targetPlayer, () -> finishAdoptionInspect(
                commandSender,
                ownerId,
                dimension,
                data,
                completedService,
                completedPlan,
                completedFailure));
    }

    private static void finishAdoptionInspect(
            VolmitSender commandSender,
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan,
            Throwable failure
    ) {
        if (failure != null || service == null || plan == null) {
            sendError(commandSender, "Jigsaw adoption inspection failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        pruneAdoptionPlanBindings();
        if (service.plan(plan.planId()).isPresent()) {
            ADOPTION_PLANS.put(plan.planId(), new AdoptionPlanBinding(
                    ownerId, dimension, data, service, plan));
        }
        reportAdoptionPlan(commandSender, plan);
    }

    private static void finishAdoptionApply(
            Player targetPlayer,
            VolmitSender commandSender,
            AdoptionPlanBinding binding,
            IrisStructureAdoptionResult result,
            Throwable failure
    ) {
        if (failure != null || result == null) {
            sendError(commandSender, "Jigsaw adoption failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        reportAdoptionResult(commandSender, result);
        if (!result.successful()) {
            return;
        }
        StructureKey target = result.receipt().orElseThrow().targetStructure();
        binding.data().invalidateStructureResources();
        openProject(targetPlayer, commandSender, binding.dimension(), target.path(), DEFAULT_STUDIO_SEED);
    }

    private static void reportAdoptionPlan(VolmitSender commandSender, IrisStructureAdoptionPlan plan) {
        C statusColor = plan.canApply() ? C.GREEN : C.RED;
        commandSender.sendMessage(statusColor + "Adoption plan " + plan.planId() + " -> "
                + plan.disposition() + " for " + plan.targetStructure().value() + ".");
        commandSender.sendMessage(C.GRAY + "Resources=" + plan.resourceCount()
                + ", bytes=" + plan.totalSourceBytes()
                + ", errors=" + plan.errorCount()
                + ", warnings=" + plan.warningCount() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : plan.diagnostics()) {
            commandSender.sendMessage(adoptionColor(diagnostic).toString() + diagnostic.summary());
        }
        if (plan.canApply()) {
            commandSender.sendMessage(C.AQUA + "Apply with /iris jigsaw adopt apply " + plan.planId());
        }
    }

    private static void reportAdoptionResult(VolmitSender commandSender, IrisStructureAdoptionResult result) {
        commandSender.sendMessage((result.successful() ? C.GREEN : C.RED)
                + "Adoption plan " + result.planId() + ": " + result.status() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : result.diagnostics()) {
            commandSender.sendMessage(adoptionColor(diagnostic).toString() + diagnostic.summary());
        }
    }

    private static C adoptionColor(IrisStructureAdoptionDiagnostic diagnostic) {
        return switch (diagnostic.severity()) {
            case ERROR -> C.RED;
            case WARNING -> C.YELLOW;
            case INFO -> C.GRAY;
        };
    }

    private static void runExport(ExportOperation operation) {
        VanillaJigsawExportResult result = null;
        Throwable failure = null;
        try {
            result = new VanillaJigsawDatapackExporter().export(operation.request());
        } catch (Throwable throwable) {
            failure = throwable;
            Iris.reportError("Vanilla Jigsaw export failed for '" + operation.structureKey() + "'.", throwable);
        } finally {
            operation.lease().release();
        }
        VanillaJigsawExportResult completedResult = result;
        Throwable completedFailure = failure;
        Runnable response = () -> reportExport(operation, completedResult, completedFailure);
        J.runEntity(operation.player(), response);
    }

    private static void reportExport(
            ExportOperation operation,
            VanillaJigsawExportResult result,
            Throwable failure
    ) {
        if (failure != null || result == null) {
            operation.sender().sendMessage(C.RED + "Background vanilla export for '"
                    + operation.structureKey() + "' failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        for (VanillaJigsawExportDiagnostic diagnostic : result.diagnostics()) {
            C color = diagnostic.isBlocking() ? C.RED : C.YELLOW;
            operation.sender().sendMessage(color.toString() + diagnostic.code() + ": " + diagnostic.message());
        }
        ActiveContext current = active(operation.player());
        boolean sameSession = current != null
                && current.request().requestId().equals(operation.requestId());
        String contextLabel = sameSession ? "Vanilla export" : "Background vanilla export for '"
                + operation.structureKey() + "'";
        operation.sender().sendMessage((result.isSuccess() ? C.GREEN : C.RED) + contextLabel + " "
                + result.status() + ": " + result.output());
    }

    static ActiveContext active(Player player) {
        if (player == null) {
            return null;
        }
        SessionBinding binding = PLAYER_PACKS.get(player.getUniqueId());
        if (binding == null) {
            return null;
        }
        ActiveContext context = context(binding);
        if (context == null) {
            PLAYER_PACKS.remove(player.getUniqueId(), binding);
            return null;
        }
        return context;
    }

    private static ActiveContext context(SessionBinding binding) {
        JigsawStudioActivation.Request request = JigsawStudioActivation.getRequest(binding.packKey());
        JigsawStudioSession session = JigsawStudioActivation.getSession(binding.packKey());
        if (request == null || session == null
                || !binding.requestId().equals(request.requestId())
                || !request.requestId().equals(session.sessionId())) {
            return null;
        }
        return new ActiveContext(binding.packKey(), request, session);
    }

    private static ActiveContext findDirtyContext() {
        for (SessionBinding binding : PLAYER_PACKS.values()) {
            JigsawStudioActivation.Request request = JigsawStudioActivation.getRequest(binding.packKey());
            JigsawStudioSession session = JigsawStudioActivation.getSession(binding.packKey());
            if (request != null && session != null
                    && binding.requestId().equals(request.requestId())
                    && session.isDirty()) {
                return new ActiveContext(binding.packKey(), request, session);
            }
        }
        return null;
    }

    private ActiveContext requireActive() {
        ActiveContext context = active(player());
        if (context == null) {
            sendError("Open a Jigsaw Studio project first.");
        }
        return context;
    }

    private IrisData requireData(IrisDimension dimension) {
        return requireData(dimension, sender());
    }

    private static IrisData requireData(IrisDimension dimension, VolmitSender commandSender) {
        IrisData data = dimension == null ? null : dimension.getLoader();
        if (data == null) {
            sendError(commandSender, "Could not resolve the requested Iris pack dimension.");
        }
        return data;
    }

    private static void beginStagedOpen(JigsawStudioActivation.StagedActivation staged) {
        if (!JigsawStudioActivation.beginStagedGeneration(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation is no longer current");
        }
    }

    private static void finishOpenAttempt(
            VolmitSender commandSender,
            UUID playerId,
            String structureKey,
            UUID closingRequestId,
            JigsawStudioService studioService,
            JigsawStudioActivation.StagedActivation staged,
            StudioOpenCoordinator.StudioOpenResult result,
            Throwable throwable
    ) {
        JigsawStudioActivation.finishOpen(playerId);
        if (throwable == null && result != null) {
            studioService.cancelClose(closingRequestId);
            return;
        }
        studioService.cancelClose(closingRequestId);
        JigsawStudioActivation.rollback(staged);
        Throwable failure = unwrapCompletionFailure(throwable == null
                ? new IllegalStateException("Studio open completed without a result")
                : throwable);
        if (isExpectedOpenDenial(failure)) {
            Iris.warn("Jigsaw Studio open for '%s' was denied: %s",
                    structureKey, failure.getMessage());
            return;
        }
        Iris.reportError("Failed to open Jigsaw Studio for '" + structureKey + "'.", failure);
        sendError(commandSender, "Jigsaw Studio open failed: " + failure.getMessage());
    }

    private static void finishOpen(
            Player targetPlayer,
            World world,
            JigsawStudioActivation.StagedActivation staged,
            JigsawStudioService studioService
    ) {
        if (world == null) {
            throw new IllegalStateException("Jigsaw Studio opened without a world");
        }
        JigsawStudioActivation.Request request = staged.request();
        JigsawStudioSession session = staged.session();
        try {
            WorldRuntimeControlService.get().applyObjectStudioWorldRules(world);
        } catch (Throwable exception) {
            Iris.reportError("Failed to apply Jigsaw Studio world rules for '" + world.getName() + "'.", exception);
        }
        JigsawStudioBay destination = initialBay(session.layout());
        Location target = destination == null
                ? new Location(world, 0.5D, 66D, 0.5D)
                : new Location(
                        world,
                        destination.bounds().originX() + destination.bounds().dimensions().width() / 2.0D,
                        destination.bounds().maxY() + 2.0D,
                        destination.bounds().originZ() + destination.bounds().dimensions().depth() / 2.0D);
        if (destination != null) {
            session.selectBay(destination.stableId());
        }
        if (!JigsawStudioActivation.commit(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation could not be committed");
        }
        studioService.activationCommitted(world, request.requestId());
        PLAYER_PACKS.put(targetPlayer.getUniqueId(), new SessionBinding(
                request.packKey(), request.requestId()));
        try {
            J.runEntity(targetPlayer, () -> BukkitPlatform.teleportAsync(targetPlayer, target)
                    .thenRun(() -> J.runEntity(targetPlayer, () -> targetPlayer.setGameMode(GameMode.CREATIVE))));
        } catch (RuntimeException exception) {
            Iris.reportError("Failed to move the player to the committed Jigsaw Studio workcell.", exception);
        }
    }

    private static JigsawStudioBay initialBay(JigsawStudioLayout layout) {
        for (JigsawStudioBay bay : layout.bays()) {
            if (layout.defaultVariant(bay).isPresent()) {
                return bay;
            }
        }
        return layout.bays().isEmpty() ? null : layout.bays().getFirst();
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static boolean isExpectedOpenDenial(Throwable failure) {
        return unwrapCompletionFailure(failure) instanceof BrokenPackException;
    }

    private void sendError(String message) {
        sender().sendMessage(C.RED + message);
    }

    private static void sendError(VolmitSender commandSender, String message) {
        commandSender.sendMessage(C.RED + message);
    }

    private static JigsawStudioMode parseMode(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "planar", "2d", "planar_jigsaw" -> JigsawStudioMode.PLANAR_JIGSAW;
            case "spatial", "3d", "spatial_jigsaw" -> JigsawStudioMode.SPATIAL_JIGSAW;
            default -> throw new IllegalArgumentException("Mode must be planar or spatial.");
        };
    }

    private static JigsawStudioCompatibilityTarget parseCompatibility(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "extended", "iris", "iris_extended" -> JigsawStudioCompatibilityTarget.IRIS_EXTENDED;
            case "vanilla", "portable", "vanilla_portable" -> JigsawStudioCompatibilityTarget.VANILLA_PORTABLE;
            default -> throw new IllegalArgumentException("Compatibility must be iris or vanilla.");
        };
    }

    private static JigsawStudioCompatibilityTarget compatibilityOf(IrisStructure structure) {
        return structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE
                ? JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                : JigsawStudioCompatibilityTarget.IRIS_EXTENDED;
    }

    static NamespacedKey parseRegisteredStructureKey(String value) {
        String normalized = Objects.requireNonNull(value, "Registered structure key").trim()
                .toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            throw new IllegalArgumentException(
                    "Registered structure source must use a valid namespace:path key.");
        }
        return key;
    }

    static String resolveConversionTarget(NamespacedKey source, String value) {
        Objects.requireNonNull(source, "Registered structure key");
        String normalized = Objects.requireNonNull(value, "Conversion target").trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return StructureImporter.deriveName(source);
        }
        StructureKey target = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(target.namespace())) {
            throw new IllegalArgumentException("Converted Studio targets must use the iris namespace.");
        }
        return target.path();
    }

    static String conversionFailureMessage(VillageImporter.Result result) {
        String message = Objects.requireNonNull(result, "Village import result").message();
        String obsoleteInstruction = "; use 'import' for single-template structures";
        int instructionIndex = message.indexOf(obsoleteInstruction);
        if (instructionIndex < 0) {
            return message;
        }
        return message.substring(0, instructionIndex)
                + ". This Studio converter accepts registered jigsaw structures only.";
    }

    static String parseAdoptionSource(String value) {
        String normalized = Objects.requireNonNull(value, "Adoption source").trim()
                .toLowerCase(Locale.ROOT);
        StructureKey source = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(source.namespace())) {
            throw new IllegalArgumentException("Adoption sources must use the iris namespace.");
        }
        return source.path();
    }

    static Optional<StructureKey> parseAdoptionTarget(String value) {
        String normalized = Objects.requireNonNull(value, "Adoption target").trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return Optional.empty();
        }
        StructureKey target = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(target.namespace())) {
            throw new IllegalArgumentException("Adoption targets must use the iris namespace.");
        }
        return Optional.of(target);
    }

    private static IrisStructureAdoptionStrategy parseAdoptionStrategy(String value) {
        return switch (Objects.requireNonNull(value, "Adoption strategy").trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> IrisStructureAdoptionStrategy.AUTO;
            case "in-place", "in_place", "inplace", "claim" -> IrisStructureAdoptionStrategy.IN_PLACE;
            case "clone", "copy" -> IrisStructureAdoptionStrategy.CLONE;
            default -> throw new IllegalArgumentException("Adoption strategy must be auto, in-place, or clone.");
        };
    }

    static IrisStructureAdoptionInputKind adoptionInputKind(Path packRoot, String source) throws IOException {
        Path root = canonicalAdoptionRoot(packRoot);
        StructureKey sourceKey = new StructureKey("iris", parseAdoptionSource(source));
        Path manifestPath = new StructureTransactionWriter(root).ownershipManifestPath(sourceKey);
        if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return IrisStructureAdoptionInputKind.UNOWNED_IRIS;
        }
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ownership manifest is not a regular file: " + manifestPath);
        }
        StructureOwnershipManifest manifest;
        try {
            manifest = StructureOwnershipManifest.fromJson(Files.readAllBytes(manifestPath));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid ownership manifest at " + manifestPath, exception);
        }
        if (!manifest.structure().equals(sourceKey)) {
            throw new IOException("Ownership manifest at " + manifestPath
                    + " belongs to " + manifest.structure().value());
        }
        return manifest.provenance().origin() == StructureOwnershipManifest.Origin.MANAGED_DATAPACK
                ? IrisStructureAdoptionInputKind.MANAGED_DATAPACK
                : IrisStructureAdoptionInputKind.UNOWNED_IRIS;
    }

    private static Path canonicalAdoptionRoot(Path packRoot) throws IOException {
        Path normalized = Objects.requireNonNull(packRoot, "Adoption pack root").toAbsolutePath().normalize();
        return Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) ? normalized.toRealPath() : normalized;
    }

    private static VanillaJigsawExportFormat parseFormat(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "zip" -> VanillaJigsawExportFormat.ZIP;
            case "directory", "dir", "folder" -> VanillaJigsawExportFormat.DIRECTORY;
            default -> throw new IllegalArgumentException("Export format must be directory or zip.");
        };
    }

    static Path resolveExportDestination(
            Path exportRoot,
            String output,
            VanillaJigsawExportFormat format
    ) {
        Path root = Objects.requireNonNull(exportRoot, "Jigsaw export root").toAbsolutePath().normalize();
        String requested = Objects.requireNonNull(output, "Jigsaw export output name");
        String name = requested.trim();
        if (!name.equals(requested) || !EXPORT_OUTPUT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Export output must be one file name using letters, numbers, '.', '_', or '-'.");
        }
        String fileName = format == VanillaJigsawExportFormat.ZIP && !name.endsWith(".zip")
                ? name + ".zip" : name;
        Path destination = root.resolve(fileName).normalize();
        if (destination.equals(root) || !root.equals(destination.getParent())) {
            throw new IllegalArgumentException("Export output must be one artifact inside the Studio exports folder.");
        }
        return destination;
    }

    static JigsawStudioBay selectedBay(ActiveContext context) {
        String selected = context.session().selectedBayId().orElse(null);
        return selected == null ? null : context.session().layout().get(selected);
    }

    static JigsawStudioVariant selectedVariant(ActiveContext context) {
        JigsawStudioBay selected = selectedBay(context);
        return selected == null
                ? null
                : context.session().activeVariant(selected.stableId()).orElse(null);
    }

    static boolean runGraphMutation(
            Player player,
            ActiveContext context,
            JigsawStudioService.CommandGraphMutation mutation
    ) {
        return JigsawStudioService.get().runCommandGraphMutation(
                player,
                context.request().requestId(),
                mutation);
    }

    static JigsawStudioLayout loadPersistedLayout(ActiveContext context) throws IOException {
        IrisData source = context.request().source();
        source.invalidateStructureResources();
        IrisStructure structure = StructureResources.structure(source.getPackResources(), context.request().structureKey());
        if (structure == null) {
            throw new IOException("The structure could not be reloaded after the graph transaction");
        }
        return JigsawStudioGraphMapper.map(source, structure);
    }

    static JigsawStudioService.CommandGraphMutationResult mappedMutationResult(
            ActiveContext context,
            String activateWorkcellId,
            String activatePieceKey,
            String message
    ) throws IOException {
        return new JigsawStudioService.CommandGraphMutationResult(
                loadPersistedLayout(context),
                activateWorkcellId,
                activatePieceKey,
                message);
    }

    static String workcellIdForVariant(JigsawStudioLayout layout, String pieceKey) throws IOException {
        JigsawStudioVariant variant = layout.variantCatalog().find(pieceKey).orElse(null);
        if (variant == null) {
            throw new IOException("Persisted variant '" + pieceKey + "' did not map to a Studio workcell");
        }
        return variant.archetype()
                .map(archetype -> archetype.stableId())
                .orElse(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
    }

    static PieceWorkcellResolution resolvePieceWorkcell(
            JigsawStudioLayout layout,
            IrisJigsawPiece piece,
            IrisObject object
    ) {
        JigsawStudioLayout activeLayout = Objects.requireNonNull(layout, "Jigsaw Studio layout");
        IrisJigsawPiece activePiece = Objects.requireNonNull(piece, "Jigsaw piece");
        IrisObject activeObject = Objects.requireNonNull(object, "Jigsaw piece object");
        String workcellId;
        IrisPosition requiredDimensions;
        if (activeLayout.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            IrisJigsawWorkcellArchetype modelArchetype = IrisJigsawWorkcellArchetype.fromPiece(activePiece);
            workcellId = JigsawPlanarArchetype.fromModel(modelArchetype).stableId();
            requiredDimensions = PlanarJigsawWorkcellResolver.canonicalDimensions(activePiece, activeObject);
        } else {
            workcellId = JigsawStudioLayout.SPATIAL_WORKCELL_ID;
            requiredDimensions = new IrisPosition(
                    activeObject.getW(),
                    activeObject.getH(),
                    activeObject.getD());
        }
        JigsawStudioBay workcell = activeLayout.get(workcellId);
        if (workcell == null) {
            throw new IllegalArgumentException("Workcell '" + workcellId + "' is not configured");
        }
        return new PieceWorkcellResolution(workcell, requiredDimensions);
    }

    static String nextThemeKey(ActiveContext context) throws IOException {
        IrisStructure structure = StructureResources.structure(
                context.request().source().getPackResources(),
                context.request().structureKey());
        if (structure == null) {
            throw new IOException("The active jigsaw structure could not be loaded");
        }
        Set<String> declared = new HashSet<>();
        if (structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet themeSet : structure.getThemeSets()) {
                if (themeSet != null && themeSet.getKey() != null) {
                    declared.add(themeSet.getKey());
                }
            }
        }
        int candidate = 1;
        while (candidate > 0) {
            String key = "variant-" + candidate;
            if (!declared.contains(key)) {
                return key;
            }
            candidate = Math.incrementExact(candidate);
        }
        throw new IOException("Jigsaw Studio cannot allocate another numbered family key");
    }

    static void sendError(DirectorExecutor executor, String message) {
        executor.sender().sendMessage(C.RED + message);
    }

    public record ActiveContext(
            String packKey,
            JigsawStudioActivation.Request request,
            JigsawStudioSession session
    ) {
    }

    record PieceWorkcellResolution(
            JigsawStudioBay workcell,
            IrisPosition requiredDimensions
    ) {
        PieceWorkcellResolution {
            workcell = Objects.requireNonNull(workcell, "Jigsaw Studio workcell");
            requiredDimensions = Objects.requireNonNull(requiredDimensions, "Jigsaw piece dimensions");
        }

        boolean fits() {
            JigsawStudioCellDimensions capacity = workcell.capacity();
            return requiredDimensions.getX() <= capacity.width()
                    && requiredDimensions.getY() <= capacity.height()
                    && requiredDimensions.getZ() <= capacity.depth();
        }
    }

    private record SessionBinding(String packKey, UUID requestId) {
    }

    private record ExportOperation(
            Player player,
            VolmitSender sender,
            UUID requestId,
            String structureKey,
            VanillaJigsawExportRequest request,
            ExportLease lease
    ) {
    }

    static final class ExportLease {
        private final Runnable release;
        private final AtomicBoolean released;

        ExportLease(Runnable release) {
            this.release = Objects.requireNonNull(release, "Jigsaw export lease release action");
            this.released = new AtomicBoolean();
        }

        void release() {
            if (released.compareAndSet(false, true)) {
                release.run();
            }
        }
    }

    private record AdoptionPlanBinding(
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan
    ) {
    }

    public static final class JigsawModeHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>("planar", "spatial");
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return parseMode(input) == JigsawStudioMode.PLANAR_JIGSAW ? "planar" : "spatial";
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException(exception.getMessage());
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }

    public static final class JigsawCompatibilityHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>("iris", "vanilla");
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return parseCompatibility(input) == JigsawStudioCompatibilityTarget.IRIS_EXTENDED
                        ? "iris" : "vanilla";
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException(exception.getMessage());
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }

    public static final class JigsawAdoptionStrategyHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>("auto", "in-place", "clone");
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return switch (parseAdoptionStrategy(input)) {
                    case AUTO -> "auto";
                    case IN_PLACE -> "in-place";
                    case CLONE -> "clone";
                };
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException(exception.getMessage());
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }

    public static final class JigsawAdoptionPlanHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>();
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return UUID.fromString(input).toString();
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException("Adoption plan ID must be a UUID.");
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }

    @Director(name = "adopt", description = "Inspect and apply safe ownership adoption plans",
            origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawAdopt implements DirectorExecutor {
        @Director(description = "Inspect an Iris graph for safe Studio ownership adoption", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void inspect(
                @Param(description = "Pack dimension") IrisDimension dimension,
                @Param(description = "Existing Iris structure key") String source,
                @Param(description = "New owned Iris key, or auto", defaultValue = "auto") String target,
                @Param(description = "auto, in-place, or clone", defaultValue = "auto",
                        customHandler = JigsawAdoptionStrategyHandler.class) String strategy
        ) {
            VolmitSender commandSender = sender();
            IrisData data = requireData(dimension, commandSender);
            if (data == null) {
                return;
            }
            String sourceKey;
            Optional<StructureKey> targetKey;
            IrisStructureAdoptionStrategy adoptionStrategy;
            Path root;
            try {
                sourceKey = parseAdoptionSource(source);
                targetKey = parseAdoptionTarget(target);
                adoptionStrategy = parseAdoptionStrategy(strategy);
                root = canonicalAdoptionRoot(data.getDataFolder().toPath());
            } catch (IOException | IllegalArgumentException exception) {
                sendError(commandSender, "Could not inspect adoption source: " + exception.getMessage());
                return;
            }
            Player targetPlayer = player();
            UUID ownerId = targetPlayer.getUniqueId();
            commandSender.sendMessage(C.GRAY + "Inspecting Jigsaw adoption source '" + sourceKey
                    + "' asynchronously...");
            try {
                J.a(() -> runAdoptionInspect(
                        targetPlayer,
                        commandSender,
                        ownerId,
                        dimension,
                        data,
                        root,
                        sourceKey,
                        targetKey,
                        adoptionStrategy));
            } catch (RuntimeException exception) {
                Iris.reportError("Failed to schedule Jigsaw adoption inspection for '"
                        + sourceKey + "'.", exception);
                sendError(commandSender, "Jigsaw adoption inspection could not be scheduled: "
                        + exception.getMessage());
            }
        }

        @Director(description = "Apply one inspected adoption plan without overwriting", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void apply(
                @Param(description = "Plan UUID returned by adopt inspect",
                        customHandler = JigsawAdoptionPlanHandler.class) String planId
        ) {
            VolmitSender commandSender = sender();
            UUID parsedPlanId;
            try {
                parsedPlanId = UUID.fromString(planId);
            } catch (IllegalArgumentException exception) {
                sendError(commandSender, "Adoption plan ID must be a UUID.");
                return;
            }
            if (studioIsActiveOrOpening()) {
                sendError(commandSender,
                        "Close the active or opening Jigsaw Studio before applying an adoption plan.");
                return;
            }
            pruneAdoptionPlanBindings();
            AdoptionPlanBinding binding = ADOPTION_PLANS.get(parsedPlanId);
            UUID playerId = player().getUniqueId();
            if (binding == null || !binding.ownerId().equals(playerId)) {
                sendError(commandSender, "No active adoption plan '" + parsedPlanId
                        + "' belongs to your player session. Run adopt inspect again.");
                return;
            }
            if (binding.service().plan(parsedPlanId).isEmpty()) {
                ADOPTION_PLANS.remove(parsedPlanId, binding);
                sendError(commandSender, "Adoption plan '" + parsedPlanId
                        + "' expired or was already consumed. Run adopt inspect again.");
                return;
            }
            if (!ADOPTION_PLANS.remove(parsedPlanId, binding)) {
                sendError(commandSender, "Adoption plan '" + parsedPlanId + "' is already being applied.");
                return;
            }
            Player targetPlayer = player();
            commandSender.sendMessage(C.GRAY + "Applying adoption plan '" + parsedPlanId
                    + "' asynchronously without overwrite...");
            try {
                J.a(() -> runAdoptionApply(targetPlayer, commandSender, parsedPlanId, binding));
            } catch (RuntimeException exception) {
                ADOPTION_PLANS.putIfAbsent(parsedPlanId, binding);
                Iris.reportError("Failed to schedule Jigsaw adoption plan '" + parsedPlanId + "'.", exception);
                sendError(commandSender, "Jigsaw adoption could not be scheduled: " + exception.getMessage());
            }
        }
    }

    @Director(name = "connector", description = "Inspect and configure targeted jigsaw connectors",
            origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawConnector implements DirectorExecutor {
        @Director(description = "Set the Iris-only channel on the targeted jigsaw marker", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void channel(
                @Param(description = "Exact channel, or none to clear") String channel
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            Block target = player().getTargetBlockExact(8);
            if (target == null || !(target.getBlockData() instanceof Jigsaw)) {
                sendError(this, "Look directly at a jigsaw marker within eight blocks.");
                return;
            }
            JigsawStudioBay bay = context.session().layout().findAt(
                    target.getX(), target.getY(), target.getZ());
            JigsawStudioVariant activeVariant = bay == null
                    ? null
                    : context.session().activeVariant(bay.stableId()).orElse(null);
            if (activeVariant == null) {
                sendError(this, "The targeted marker must be inside a workcell with an active variant.");
                return;
            }
            if (!activeVariant.owned()) {
                sendError(this, "The active variant is read-only. Adopt it into this project before editing it.");
                return;
            }
            IrisObject object = context.request().source().getObjectLoader().load(activeVariant.objectKey());
            if (object == null) {
                sendError(this, "The active variant object '" + activeVariant.objectKey() + "' is missing.");
                return;
            }
            IrisPosition canonicalPosition = new IrisPosition(
                    target.getX() - bay.bounds().originX(),
                    target.getY() - bay.bounds().originY(),
                    target.getZ() - bay.bounds().originZ());
            IrisPosition position;
            try {
                position = activeVariant.canonicalToSourcePosition(
                        canonicalPosition,
                        new JigsawStudioCellDimensions(object.getW(), object.getH(), object.getD()));
            } catch (IllegalArgumentException exception) {
                sendError(this, "Connector channel update failed: " + exception.getMessage());
                return;
            }
            String normalized = channel == null || channel.isBlank() || "none".equalsIgnoreCase(channel)
                    ? "none"
                    : channel.trim();
            runGraphMutation(player(), context, () -> {
                JigsawStudioGraphEditor.updateConnectorChannel(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        activeVariant.pieceKey(),
                        position,
                        channel);
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Persisted connector channel '" + normalized + "' on piece '"
                                + activeVariant.pieceKey() + "' at " + position.getX() + "," + position.getY()
                                + "," + position.getZ() + ".");
            });
        }
    }

    @Director(name = "pool", description = "Manage owned jigsaw pools", origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawPool implements DirectorExecutor {
        @Director(description = "Create an empty owned jigsaw pool", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void create(
                @Param(description = "New owned pool resource key") String poolKey,
                @Param(description = "Owned fallback pool key, or none", defaultValue = "none")
                String fallbackPoolKey
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            String fallback = "none".equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
            runGraphMutation(player(), context, () -> {
                JigsawStudioGraphEditor.createPool(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        fallback);
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Created owned pool '" + poolKey + "' with fallback '"
                                + (fallback.isBlank() ? "none" : fallback) + "'.");
            });
        }
    }

    @Director(name = "piece", description = "Manage contextual Studio pieces", origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawPiece implements DirectorExecutor {
        @Director(description = "Create an owned piece from the selected Studio topology", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void create(
                @Param(description = "Owned pool resource key") String poolKey,
                @Param(description = "New owned piece and object key") String pieceKey,
                @Param(description = "Positive selection weight", defaultValue = "1") int weight
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawPlanarTopology topology = null;
            JigsawStudioBay targetWorkcell = context.session().layout().get(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
            if (context.session().layout().mode() == JigsawStudioMode.PLANAR_JIGSAW) {
                Location location = player().getLocation();
                JigsawStudioBay contextual = context.session().layout().findAt(
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
                if (contextual == null) {
                    contextual = selectedBay(context);
                } else {
                    context.session().selectBay(contextual.stableId());
                }
                if (contextual == null || contextual.topology().isEmpty()) {
                    sendError(this, "Stand in or select a planar topology workcell before creating a variant.");
                    return;
                }
                topology = contextual.topology().get();
                targetWorkcell = contextual;
            }
            if (targetWorkcell == null) {
                sendError(this, "The selected Jigsaw Studio workcell is no longer available.");
                return;
            }
            JigsawPlanarTopology selectedTopology = topology;
            String activationWorkcellId = targetWorkcell.stableId();
            JigsawStudioCellDimensions dimensions = targetWorkcell.capacity();
            String topologyText = selectedTopology == null ? "blank spatial" : selectedTopology.name();
            runGraphMutation(player(), context, () -> {
                JigsawStudioGraphEditor.createPiece(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        pieceKey,
                        weight,
                        dimensions,
                        selectedTopology);
                return mappedMutationResult(
                        context,
                        activationWorkcellId,
                        pieceKey,
                        "Created owned " + topologyText + " piece '" + pieceKey
                                + "' and added it to pool '" + poolKey + "' atomically.");
            });
        }

        @Director(description = "Add an existing jigsaw piece to an owned pool transactionally", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void add(
                @Param(description = "Owned pool resource key") String poolKey,
                @Param(description = "Existing jigsaw piece key") String pieceKey,
                @Param(description = "Positive selection weight", defaultValue = "1") int weight
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            IrisJigsawPiece piece = StructureResources.piece(context.request().source().getPackResources(), pieceKey);
            if (piece == null) {
                sendError(this, "No jigsaw piece '" + pieceKey + "' exists in this pack.");
                return;
            }
            if (piece.getObject() == null || piece.getObject().isBlank()) {
                sendError(this, "Jigsaw piece '" + pieceKey + "' does not declare an object.");
                return;
            }
            IrisObject object = context.request().source().getObjectLoader().load(piece.getObject());
            if (object == null) {
                sendError(this, "Jigsaw piece '" + pieceKey + "' has no loadable object.");
                return;
            }
            PieceWorkcellResolution resolution;
            try {
                resolution = resolvePieceWorkcell(context.session().layout(), piece, object);
            } catch (IllegalArgumentException exception) {
                sendError(this, "Jigsaw piece '" + pieceKey + "' cannot be assigned to a Studio workcell: "
                        + exception.getMessage());
                return;
            }
            if (!resolution.fits()) {
                IrisPosition required = resolution.requiredDimensions();
                JigsawStudioCellDimensions capacity = resolution.workcell().capacity();
                sendError(this, "Jigsaw piece '" + pieceKey + "' requires " + required.getX() + "x"
                        + required.getY() + "x" + required.getZ() + " in " + resolution.workcell().displayName()
                        + ", but that workcell capacity is " + capacity.width() + "x" + capacity.height()
                        + "x" + capacity.depth() + ".");
                return;
            }
            String objectKey = piece.getObject();
            runGraphMutation(player(), context, () -> {
                if (!JigsawStudioGraphEditor.ownsPiece(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        pieceKey,
                        objectKey)) {
                    throw new IOException("Piece add accepts only piece/object resources already owned by this project; "
                            + "use piece create for a new owned piece");
                }
                JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.addPiece(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        pieceKey,
                        weight);
                if (!update.changed()) {
                    throw new IOException("The owned pool is missing or already references '" + pieceKey + "'");
                }
                JigsawStudioLayout layout = loadPersistedLayout(context);
                String activationWorkcellId = workcellIdForVariant(layout, pieceKey);
                return new JigsawStudioService.CommandGraphMutationResult(
                        layout,
                        activationWorkcellId,
                        pieceKey,
                        "Added '" + pieceKey + "' to pool '" + poolKey + "' in one graph transaction.");
            });
        }

        @Director(description = "Remove the selected piece from an owned pool transactionally", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void remove(
                @Param(description = "Owned pool resource key") String poolKey
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioVariant selected = selectedVariant(context);
            if (selected == null) {
                sendError(this, "Select a workcell with an active variant before removing its pool entry.");
                return;
            }
            String pieceKey = selected.pieceKey();
            runGraphMutation(player(), context, () -> {
                JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.removePiece(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        pieceKey);
                if (!update.changed()) {
                    throw new IOException("The owned pool does not reference selected piece '" + pieceKey + "'");
                }
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Removed '" + pieceKey + "' from pool '" + poolKey + "' in one graph transaction.");
            });
        }

        @Director(description = "Allow or forbid cardinal rotation of the selected piece", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void rotatable(
                @Param(description = "Whether the selected piece may rotate") boolean rotatable
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioVariant selected = selectedVariant(context);
            if (selected == null) {
                sendError(this, "Select a workcell with an active variant before changing rotation.");
                return;
            }
            if (!rotatable
                    && context.request().compatibilityTarget()
                    == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
                sendError(this, "Vanilla-portable pieces must remain rotatable.");
                return;
            }
            String pieceKey = selected.pieceKey();
            runGraphMutation(player(), context, () -> {
                JigsawStudioGraphEditor.updateRotatable(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        pieceKey,
                        rotatable);
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Persisted piece '" + pieceKey + "' rotatable=" + rotatable + ".");
            });
        }

        @Director(description = "Resize the selected piece object exactly to workcell capacity", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void expand() {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay workcell = selectedBay(context);
            if (workcell == null) {
                sendError(this, "Select a workcell before expanding its active variant.");
                return;
            }
            JigsawStudioService.get().expandVariantToCell(player(), workcell.stableId());
        }
    }

    @Director(name = "workcell", aliases = "cell", description = "Manage the selected Studio workcell",
            origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawWorkcell implements DirectorExecutor {
        @Director(description = "Set capacity without resizing any variant object", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void capacity(
                @Param(description = "Capacity width") int width,
                @Param(description = "Capacity height") int height,
                @Param(description = "Capacity depth") int depth
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selected = selectedBay(context);
            if (selected == null) {
                sendError(this, "Select a workcell before changing its capacity.");
                return;
            }
            JigsawStudioCellDimensions dimensions;
            try {
                dimensions = new JigsawStudioCellDimensions(width, height, depth);
            } catch (IllegalArgumentException exception) {
                sendError(this, exception.getMessage());
                return;
            }
            JigsawStudioService.get().updateWorkcellDimensions(
                    player(),
                    selected.stableId(),
                    dimensions);
        }

        @Director(description = "Set the selected workcell's author-facing label", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void label(
                @Param(description = "Display label; quote labels containing spaces") String displayName
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selected = selectedBay(context);
            if (selected == null) {
                sendError(this, "Select a workcell before renaming it.");
                return;
            }
            JigsawStudioService.get().updateWorkcellDisplayName(
                    player(),
                    selected.stableId(),
                    displayName);
        }

        @Director(name = "label-reset", aliases = "reset-label",
                description = "Reset the selected workcell to its canonical solver label", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void labelReset() {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selected = selectedBay(context);
            if (selected == null) {
                sendError(this, "Select a workcell before resetting its label.");
                return;
            }
            JigsawStudioService.get().updateWorkcellDisplayName(player(), selected.stableId(), "");
        }
    }

    @Director(name = "variant", description = "Manage selected jigsaw variants", origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawVariant implements DirectorExecutor {
        @Director(description = "Set the selected piece weight in a pool", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void weight(
                @Param(description = "Pool resource key") String poolKey,
                @Param(description = "Positive selection weight") int weight
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioVariant selected = selectedVariant(context);
            if (selected == null) {
                sendError(this, "Select a workcell with an active variant before changing its weight.");
                return;
            }
            String pieceKey = selected.pieceKey();
            runGraphMutation(player(), context, () -> {
                JigsawStudioPoolEditor.WeightUpdate update = JigsawStudioPoolEditor.updateWeight(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        pieceKey,
                        weight);
                if (!update.changed()) {
                    throw new IOException("Pool '" + poolKey + "' does not reference selected piece '"
                            + pieceKey + "'");
                }
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Updated " + update.changedEntries() + " pool entry weight atomically in "
                                + update.poolPath() + ".");
            });
        }

        @Director(description = "Resize only the active variant object within workcell capacity", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void resize(
                @Param(description = "Variant width") int width,
                @Param(description = "Variant height") int height,
                @Param(description = "Variant depth") int depth
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selectedWorkcell = selectedBay(context);
            JigsawStudioVariant selectedVariant = selectedVariant(context);
            if (selectedWorkcell == null || selectedVariant == null) {
                sendError(this, "Select a workcell with an active variant before resizing it.");
                return;
            }
            JigsawStudioCellDimensions dimensions;
            try {
                dimensions = new JigsawStudioCellDimensions(width, height, depth);
            } catch (IllegalArgumentException exception) {
                sendError(this, exception.getMessage());
                return;
            }
            JigsawStudioService.get().resizeVariant(
                    player(),
                    selectedWorkcell.stableId(),
                    selectedVariant.pieceKey(),
                    dimensions);
        }

        @Director(description = "Set the active variant's author-facing label", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void label(
                @Param(description = "Display label; quote labels containing spaces") String displayName
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selectedWorkcell = selectedBay(context);
            JigsawStudioVariant selectedVariant = selectedVariant(context);
            if (selectedWorkcell == null || selectedVariant == null) {
                sendError(this, "Select a workcell with an active variant before renaming it.");
                return;
            }
            JigsawStudioService.get().updateVariantDisplayName(
                    player(),
                    selectedWorkcell.stableId(),
                    selectedVariant.pieceKey(),
                    displayName);
        }

        @Director(name = "label-reset", aliases = "reset-label",
                description = "Reset the active variant to its resource-key label", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void labelReset() {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selectedWorkcell = selectedBay(context);
            JigsawStudioVariant selectedVariant = selectedVariant(context);
            if (selectedWorkcell == null || selectedVariant == null) {
                sendError(this, "Select a workcell with an active variant before resetting its label.");
                return;
            }
            JigsawStudioService.get().updateVariantDisplayName(
                    player(),
                    selectedWorkcell.stableId(),
                    selectedVariant.pieceKey(),
                    "");
        }

        @Director(description = "Duplicate the active variant in only this workcell", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void duplicate() {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            JigsawStudioBay selected = selectedBay(context);
            if (selected == null || selectedVariant(context) == null) {
                sendError(this, "Select a workcell with an active variant before duplicating it.");
                return;
            }
            JigsawStudioService.get().createVariant(player(), selected.stableId(), true);
        }

        @Director(name = "duplicate-family", aliases = "family",
                description = "Duplicate every enabled workcell's active variant as one coherent family",
                sync = true, origin = DirectorOrigin.PLAYER)
        public void duplicateFamily(
                @Param(description = "Family key or next", defaultValue = "next") String themeKey
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            String selectedTheme = themeKey;
            if ("next".equalsIgnoreCase(themeKey)) {
                try {
                    selectedTheme = nextThemeKey(context);
                } catch (IOException | ArithmeticException exception) {
                    sendError(this, exception.getMessage());
                    return;
                }
            }
            JigsawStudioService.get().duplicateActiveFamily(player(), selectedTheme);
        }
    }

    @Director(name = "rules", aliases = "rule", description = "Manage structure-wide jigsaw rules",
            origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawRules implements DirectorExecutor {
        @Director(description = "Set maximum expansion depth and horizontal radius", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void limits(
                @Param(description = "Maximum expansion depth") int maxDepth,
                @Param(description = "Maximum radius in chunks") int maxSizeChunks
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            if (context.request().compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                    && (maxDepth > 20 || maxSizeChunks > 8)) {
                sendError(this, "Vanilla-portable rules require maxDepth <= 20 and maxSizeChunks <= 8.");
                return;
            }
            runGraphMutation(player(), context, () -> {
                JigsawStudioStructureEditor.updateLimits(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        maxDepth,
                        maxSizeChunks);
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Persisted jigsaw limits: maxDepth=" + maxDepth
                                + ", maxSizeChunks=" + maxSizeChunks + ".");
            });
        }

        @Director(description = "Set or clear one owned pool's direct fallback", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void fallback(
                @Param(description = "Owned pool resource key") String poolKey,
                @Param(description = "Owned fallback pool key, or none") String fallbackPoolKey
        ) {
            ActiveContext context = active(player());
            if (context == null) {
                sendError(this, "Open a Jigsaw Studio project first.");
                return;
            }
            String fallback = "none".equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
            runGraphMutation(player(), context, () -> {
                JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.updateFallback(
                        context.request().source().getDataFolder().toPath(),
                        context.request().structureKey(),
                        poolKey,
                        fallback);
                if (!update.changed()) {
                    return mappedMutationResult(context, "", "", "Pool fallback is unchanged.");
                }
                return mappedMutationResult(
                        context,
                        "",
                        "",
                        "Persisted pool '" + poolKey + "' fallback as '"
                                + (fallback.isBlank() ? "none" : fallback) + "'.");
            });
        }
    }

    @Director(name = "preview", description = "Assemble a read-only jigsaw preview",
            origin = DirectorOrigin.PLAYER)
    public static class CommandJigsawPreview implements DirectorExecutor {
        @Director(name = "goto", aliases = "teleport", description = "Teleport to the live seed-1337 preview",
                sync = true, origin = DirectorOrigin.PLAYER)
        public void gotoPreview() {
            JigsawStudioService.get().goToPreview(player());
        }

        @Director(description = "Assemble a deterministic preview at your location", sync = true,
                origin = DirectorOrigin.PLAYER)
        public void assemble(
                @Param(description = "Assembly seed", defaultValue = "1337") long seed
        ) {
            JigsawStudioService.get().previewStudio(player(), seed);
        }

    }
}
