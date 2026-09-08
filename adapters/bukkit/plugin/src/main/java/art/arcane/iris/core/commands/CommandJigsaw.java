package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioProjectCreator;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioGraphEvaluation;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.structure.StructureImporter;
import art.arcane.iris.core.structure.VillageImporter;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.core.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.core.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.core.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.engine.framework.structure.PlanarJigsawWorkcellResolver;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisStructure;
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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Director(name = "jigsaw", aliases = {"jig", "jgs"}, description = "Iris Jigsaw Studio tools",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsaw implements DirectorExecutor {
    static final long DEFAULT_STUDIO_SEED = 1337L;
    static final StudioOpenCoordinator.StudioOpenKind STUDIO_OPEN_KIND =
            StudioOpenCoordinator.StudioOpenKind.JIGSAW;
    static final Map<UUID, SessionBinding> PLAYER_PACKS = new ConcurrentHashMap<>();
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
        CommandJigsawOpen.openProject(player(), commandSender, dimension, targetKey, seed);
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
        CommandJigsawOpen.openProject(player(), sender(), dimension, structure, seed);
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
            exportFormat = CommandJigsawExports.parseFormat(format);
        } catch (IllegalArgumentException exception) {
            sendError(exception.getMessage());
            return;
        }
        Path exportRoot = new File(Iris.service(StudioSVC.class).getWorkspaceFolder(), "exports")
                .toPath().toAbsolutePath().normalize();
        Path destination;
        try {
            destination = CommandJigsawExports.resolveExportDestination(exportRoot, output, exportFormat);
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
            sendError(CommandJigsawExports.exportStartError(exportStart));
            return;
        }
        if (!CommandJigsawExports.beginExport(playerId, destination)) {
            studioService.finishExport(requestId);
            sendError("A vanilla Jigsaw export for this player or output is already in progress.");
            return;
        }
        CommandJigsawExports.ExportLease lease = new CommandJigsawExports.ExportLease(() -> {
            CommandJigsawExports.finishExport(playerId, destination);
            studioService.finishExport(requestId);
        });
        CommandJigsawExports.ExportOperation operation = new CommandJigsawExports.ExportOperation(
                targetPlayer,
                sender(),
                requestId,
                context.request().structureKey(),
                request,
                lease);
        sender().sendMessage(C.GRAY + "Exporting '" + operation.structureKey()
                + "' in the background to " + destination + "...");
        try {
            J.a(() -> CommandJigsawExports.runExport(operation));
        } catch (RuntimeException exception) {
            operation.lease().release();
            Iris.reportError("Failed to schedule vanilla Jigsaw export for '"
                    + operation.structureKey() + "'.", exception);
            sendError("Vanilla export could not be scheduled: " + exception.getMessage());
        }
    }

    static boolean studioIsActiveOrOpening() {
        return JigsawStudioActivation.activeOwnerId() != null
                || JigsawStudioActivation.openingOwnerId() != null;
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

    static ActiveContext findDirtyContext() {
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

    static IrisData requireData(IrisDimension dimension, VolmitSender commandSender) {
        IrisData data = dimension == null ? null : dimension.getLoader();
        if (data == null) {
            sendError(commandSender, "Could not resolve the requested Iris pack dimension.");
        }
        return data;
    }

    private void sendError(String message) {
        sender().sendMessage(C.RED + message);
    }

    static void sendError(VolmitSender commandSender, String message) {
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

    static JigsawStudioCompatibilityTarget compatibilityOf(IrisStructure structure) {
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
        IrisStructure structure = source.load(IrisStructure.class, context.request().structureKey(), false);
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
        IrisStructure structure = context.request().source().load(
                IrisStructure.class,
                context.request().structureKey(),
                false);
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

    record SessionBinding(String packKey, UUID requestId) {
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

}
