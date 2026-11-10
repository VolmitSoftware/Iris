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

package art.arcane.iris.core.structure.tooling;

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
import art.arcane.iris.core.service.JigsawStudioRegistry;
import art.arcane.iris.core.service.JigsawStudioRequest;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.StructureImporter;
import art.arcane.iris.core.structure.VillageImporter;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.core.structure.tooling.StudioSessions.ActiveContext;
import art.arcane.iris.core.structure.tooling.StudioSessions.SessionBinding;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.ObjectCodec;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** The Jigsaw Studio commands that create, open, close and inspect a player's studio, and that select and resize its workcells. */
final class StudioLifecycleCommands {
    static final long DEFAULT_STUDIO_SEED = 1337L;
    private static final String PLANAR = "planar";
    private static final String SPATIAL = "spatial";
    private static final String IRIS = "iris";
    private static final String VANILLA = "vanilla";
    private static final String NONE = "none";
    private static final String SELECTED = "selected";
    private static final long DEFAULT_CELL_SIZE = 15L;

    private static final Argument<String> NEW_KEY = Argument.word("key")
            .describe("New key written as structures/<key>.json");
    private static final Argument<ResourceId> EXISTING_KEY = Argument.resource("key", StructureResources.STRUCTURES)
            .describe("Existing key loaded from structures/<key>.json");
    private static final Argument<String> MODE = Argument.word("mode").orElse(PLANAR)
            .describe("planar or spatial").suggesting(context -> List.of(PLANAR, SPATIAL));
    private static final Argument<String> COMPATIBILITY = Argument.word("compatibility").orElse(IRIS)
            .describe("iris or vanilla").suggesting(context -> List.of(IRIS, VANILLA));
    private static final Argument<Long> WIDTH = size("width", "Cell width").orElse(DEFAULT_CELL_SIZE);
    private static final Argument<Long> HEIGHT = size("height", "Cell height").orElse(DEFAULT_CELL_SIZE);
    private static final Argument<Long> DEPTH = size("depth", "Cell depth").orElse(DEFAULT_CELL_SIZE);
    private static final Argument<Long> SEED = seed();
    private static final Argument<String> SOURCE = Argument.word("source")
            .describe("Registered structure key such as minecraft:village_plains");
    private static final Argument<String> TARGET = Argument.word("target").orElse("auto")
            .describe("New Iris structure key, or auto");
    private static final Argument<String> DISCARD = Argument.word("discard").orElse("false")
            .describe("Discard unsaved Studio layout changes");
    private static final Argument<String> CONFIRM = Argument.word("confirm").orElse("false")
            .describe("Confirm permanent project deletion");
    private static final Argument<Long> REQUIRED_WIDTH = size("width", "Cell width");
    private static final Argument<Long> REQUIRED_HEIGHT = size("height", "Cell height");
    private static final Argument<Long> REQUIRED_DEPTH = size("depth", "Cell depth");
    private static final Argument<String> BAY = Argument.word("bay").orElse(SELECTED)
            .describe("Workcell ID or selected");
    private static final Argument<String> GOTO_BAY = Argument.word("bay").describe("Workcell ID");
    private static final Argument<Boolean> VISIBLE = Argument.flag("visible")
            .describe("Whether particles are visible");

    private final Supplier<Optional<JigsawStudioHost>> hosts;

    StudioLifecycleCommands(Supplier<Optional<JigsawStudioHost>> hosts) {
        this.hosts = hosts;
    }

    List<Command> commands() {
        return List.of(
                Command.named("create").describe("Create and open a new atomic Jigsaw Studio project")
                        .argument(NEW_KEY).argument(MODE).argument(COMPATIBILITY)
                        .argument(WIDTH).argument(HEIGHT).argument(DEPTH).argument(SEED)
                        .runs(this::create),
                Command.named("convert").alias("import-vanilla")
                        .describe("Import a registered vanilla or datapack jigsaw graph into Iris Studio")
                        .argument(SOURCE).argument(TARGET).argument(SEED)
                        .runs(this::convert),
                Command.named("open").alias("edit", "reopen")
                        .describe("Open an existing Iris jigsaw graph in transient Studio")
                        .argument(EXISTING_KEY).argument(SEED)
                        .runs(this::open),
                Command.named("close").describe("Close the active Jigsaw Studio")
                        .argument(DISCARD).runs(this::close),
                Command.named("delete").alias("remove").describe("Delete the active owned jigsaw project")
                        .argument(CONFIRM).runs(this::delete),
                Command.named("status").describe("Show active Jigsaw Studio and dynamic evaluation state")
                        .runs(this::status),
                Command.named("menu").describe("Open the Jigsaw Studio workcell and variant menu")
                        .runs(this::menu),
                Command.named("select").describe("Select the Jigsaw Studio workcell containing you")
                        .runs(this::select),
                Command.named("bounds").alias("cell", "resize")
                        .describe("Set the selected Studio workcell capacity")
                        .argument(REQUIRED_WIDTH).argument(REQUIRED_HEIGHT).argument(REQUIRED_DEPTH)
                        .runs(this::bounds),
                Command.named("save").describe("Flush the automatic save for a workcell now")
                        .argument(BAY).runs(this::save),
                Command.named("goto").alias("teleport").describe("Select and teleport to a Studio workcell")
                        .argument(GOTO_BAY).runs(this::gotoBay),
                Command.named("particles").describe("Enable or disable Jigsaw Studio particles")
                        .argument(VISIBLE).runs(this::particles));
    }

    private static Argument<Long> size(String name, String description) {
        return Argument.integer(name, Integer.MIN_VALUE, Integer.MAX_VALUE).describe(description);
    }

    private static Argument<Long> seed() {
        return Argument.integer("seed", Long.MIN_VALUE, Long.MAX_VALUE).orElse(DEFAULT_STUDIO_SEED)
                .describe("Studio world seed");
    }

    private void create(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ToolPack pack = context.pack();
        String structure = context.get(NEW_KEY);
        JigsawStudioMode studioMode;
        JigsawStudioCompatibilityTarget target;
        JigsawStudioCellDimensions dimensions;
        try {
            studioMode = parseMode(context.get(MODE));
            target = parseCompatibility(context.get(COMPATIBILITY));
            dimensions = cellDimensions(context.get(WIDTH), context.get(HEIGHT), context.get(DEPTH));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }

        CreateOutcome outcome;
        try {
            JigsawStudioProjectCreator.Options options =
                    new JigsawStudioProjectCreator.Options(structure, studioMode, target, dimensions);
            outcome = pack.edit(root -> createProject(root, pack.objects(), options, structure));
        } catch (IOException | RuntimeException exception) {
            pack.log().error("Failed to create Jigsaw Studio project '" + structure + "'.", exception);
            use.error("Jigsaw project creation failed: " + exception.getMessage());
            return;
        }
        StructureWriteResult result = outcome.result();
        if (!result.successful()) {
            use.error("Jigsaw project was not created: " + result.status());
            if (!result.conflicts().isEmpty()) {
                use.error(result.conflicts().getFirst().relativePath() + ": "
                        + result.conflicts().getFirst().reason());
            }
            return;
        }
        if (outcome.historyFailure() != null) {
            pack.log().error("Failed to clear stale Jigsaw Studio history for '" + structure + "'.", outcome.historyFailure());
            use.error("Jigsaw project was created, but stale autosave history could not be cleared: "
                    + outcome.historyFailure().getMessage());
            return;
        }
        use.say(TextColor.GREEN, "Created Jigsaw project '" + structure + "' atomically.");
        openProject(use, structure);
    }

    private static CreateOutcome createProject(Path root, ObjectCodec codec, JigsawStudioProjectCreator.Options options,
                                               String structure) throws IOException {
        StructureWriteResult result = JigsawStudioProjectCreator.create(root, codec, options);
        if (!result.successful()) {
            return new CreateOutcome(result, null);
        }
        try {
            JigsawStudioService.clearAutosaveHistory(root, structure);
        } catch (IOException exception) {
            return new CreateOutcome(result, exception);
        }
        return new CreateOutcome(result, null);
    }

    private record CreateOutcome(StructureWriteResult result, IOException historyFailure) {
    }

    private void convert(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        if (studioIsActiveOrOpening(use.registry())) {
            use.error("Close the active or opening Jigsaw Studio before importing another graph.");
            return;
        }
        Optional<PlatformStructures> platform = PlatformStructuresAccess.require(context);
        if (platform.isEmpty()) {
            return;
        }
        ResourceId sourceKey;
        String targetKey;
        try {
            sourceKey = parseRegisteredStructureKey(context.get(SOURCE));
            targetKey = resolveConversionTarget(sourceKey, context.get(TARGET));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }

        VillageImporter.Result result = VillageImporter.importVillage(
                context.pack(),
                platform.get(),
                sourceKey,
                targetKey,
                StructureImporter.Mode.ADD_ONLY);
        if (!result.success()) {
            use.error(conversionFailureMessage(result));
            return;
        }
        use.say(TextColor.GREEN, result.message());
        openProject(use, targetKey);
    }

    private void open(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        openProject(begun.get(), StructureKeys.keyOf(context.pack().resources(), context.get(EXISTING_KEY)));
    }

    /** Stages the studio of {@code structure}, opens its tool world and moves the player in once the studio is committed. */
    static void openProject(StudioUse use, String structure) {
        ToolPack pack = use.pack();
        ToolPlayer player = use.player();
        JigsawStudioHost host = use.host();
        JigsawStudioService studioService = use.studio();
        JigsawStudioRegistry registry = use.registry();
        UUID playerId = player.id();
        UUID ownerId = registry.activeOwnerId();
        if (ownerId != null && !ownerId.equals(playerId)) {
            use.error("Jigsaw Studio is already owned by another player session.");
            return;
        }
        UUID openingOwner = registry.openingOwnerId();
        if (openingOwner != null) {
            use.error(openingOwner.equals(playerId)
                    ? "Your Jigsaw Studio is already opening."
                    : "Jigsaw Studio is already opening for another player.");
            return;
        }
        ActiveContext previous = StudioSessions.active(registry, playerId);
        if (ownerId != null && previous == null) {
            use.error("Your active Jigsaw Studio binding is unavailable. Close it before opening another project.");
            return;
        }
        ActiveContext dirty = StudioSessions.findDirtyContext(registry);
        if (dirty != null) {
            use.error("The current Jigsaw Studio is waiting for autosave. Wait for it to finish or close with discard=true.");
            return;
        }
        IrisStructure irisStructure = StructureResources.structure(pack.resources(), structure);
        if (irisStructure == null) {
            use.error("No Iris jigsaw structure '" + structure + "' exists in this pack.");
            return;
        }

        JigsawStudioLayout layout;
        try {
            layout = JigsawStudioGraphMapper.map(pack, JigsawStudioRequest.rootOf(pack), structure, irisStructure);
        } catch (RuntimeException exception) {
            pack.log().error("Failed to map Jigsaw Studio graph '" + structure + "'.", exception);
            use.error("Could not map this jigsaw graph: " + exception.getMessage());
            return;
        }
        String packKey = host.packKey(pack);
        if (!registry.tryBeginOpen(playerId)) {
            use.error("Jigsaw Studio began opening for another player. Try again after it finishes.");
            return;
        }
        UUID closingRequestId = previous == null ? null : previous.request().requestId();
        if (closingRequestId != null) {
            JigsawStudioService.CloseStart closeStart = studioService.tryBeginClose(
                    closingRequestId,
                    playerId,
                    false);
            if (closeStart != JigsawStudioService.CloseStart.STARTED) {
                registry.finishOpen(playerId);
                use.error(closeStart == JigsawStudioService.CloseStart.SAVE_IN_PROGRESS
                        ? "The current Jigsaw Studio is saving. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS
                        ? "The current Jigsaw Studio is loading a variant. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.DIRTY
                        ? "The current Jigsaw Studio is waiting for autosave. Wait for it to finish before opening another project."
                        : "The current Jigsaw Studio could not be reserved for replacement.");
                return;
            }
        }
        JigsawStudioRegistry.StagedActivation staged;
        try {
            staged = registry.stage(
                    packKey,
                    structure,
                    layout.mode(),
                    compatibilityOf(irisStructure),
                    layout.cellDimensions(),
                    pack,
                    layout,
                    playerId,
                    closingRequestId);
        } catch (RuntimeException exception) {
            registry.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            pack.log().error("Failed to stage Jigsaw Studio for '" + structure + "'.", exception);
            use.error("Jigsaw Studio staging failed: " + exception.getMessage());
            return;
        }
        use.say(TextColor.GREEN, "Opening transient Jigsaw Studio for '" + structure + "' with "
                + layout.bays().size() + " workcells.");

        Optional<Throwable> admissionFailure = host.reportAdmissionFailure(use.sender(), packKey);
        if (admissionFailure.isPresent()) {
            finishOpenAttempt(use, structure, closingRequestId, staged, null, admissionFailure.get());
            return;
        }
        try {
            beginStagedOpen(registry, staged);
            studioService.open(pack, staged)
                    .whenComplete((world, throwable) -> player.run(() -> {
                        Throwable failure = throwable;
                        if (failure == null) {
                            failure = commitOpenedStudio(use, world, staged);
                        }
                        finishOpenAttempt(use, structure, closingRequestId, staged, world, failure);
                    }));
        } catch (Throwable exception) {
            registry.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            registry.rollback(staged);
            pack.log().error("Failed to open Jigsaw Studio for '" + structure + "'.", exception);
            use.error("Jigsaw Studio open failed: " + exception.getMessage());
        }
    }

    private void close(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        boolean discard = flagValue(DISCARD.name(), context.get(DISCARD));
        ToolPlayer player = use.player();
        ActiveContext active = StudioSessions.active(use.registry(), player.id());
        if (active == null) {
            use.error("You do not have an active Jigsaw Studio session.");
            return;
        }
        if (active.session().isDirty() && !discard) {
            use.error("This Jigsaw Studio is waiting for autosave. Wait for it to finish or run close with discard=true.");
            return;
        }
        JigsawStudioService studioService = use.studio();
        UUID requestId = active.request().requestId();
        JigsawStudioService.CloseStart closeStart = studioService.tryBeginClose(
                requestId,
                player.id(),
                discard);
        if (closeStart != JigsawStudioService.CloseStart.STARTED) {
            use.error(closeStart == JigsawStudioService.CloseStart.SAVE_IN_PROGRESS
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
        try {
            use.host().closeStudio().whenComplete((failure, throwable) -> player.run(() -> {
                if (throwable != null || failure.isPresent()) {
                    studioService.cancelClose(requestId);
                    Throwable cause = throwable != null ? throwable : failure.get();
                    use.error("Jigsaw Studio close failed: " + cause.getMessage());
                    return;
                }
                use.registry().deactivate(active.packKey(), requestId);
                StudioSessions.unbind(player.id(), new SessionBinding(active.packKey(), requestId));
                use.say(TextColor.GREEN, "Jigsaw Studio closed.");
            }));
        } catch (Throwable exception) {
            studioService.cancelClose(requestId);
            context.pack().log().error("Failed to close Jigsaw Studio '" + active.request().structureKey() + "'.", exception);
            use.error("Jigsaw Studio close failed: " + exception.getMessage());
        }
    }

    private void delete(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty() || begun.get().requireActive() == null) {
            return;
        }
        StudioUse use = begun.get();
        if (!flagValue(CONFIRM.name(), context.get(CONFIRM))) {
            use.error("Project deletion removes every resource owned by this jigsaw. Run again with confirm=true.");
            return;
        }
        use.onPlayer(() -> use.studio().deleteProject(use.player()));
    }

    private void status(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioLayout layout = active.session().layout();
        String selection = active.session().selectedBayId().orElse(NONE);
        JigsawStudioVariant activeVariant = NONE.equals(selection)
                ? null
                : active.session().activeVariant(selection).orElse(null);
        JigsawStudioBay selectedBay = NONE.equals(selection) ? null : layout.get(selection);
        JigsawStudioCellDimensions selectedDimensions = selectedBay == null
                ? null
                : selectedBay.bounds().dimensions();
        Optional<JigsawStudioGraphEvaluation> evaluation = use.readFromPlayer(() -> use.studio().evaluation(use.player()));
        use.say(TextColor.AQUA, "Jigsaw Studio: " + active.request().structureKey());
        use.say(TextColor.GRAY, "mode=" + layout.mode()
                + ", compatibility=" + active.request().compatibilityTarget()
                + ", defaultCell=" + layout.cellDimensions().width() + "x"
                + layout.cellDimensions().height() + "x" + layout.cellDimensions().depth()
                + ", workcells=" + layout.bays().size()
                + ", variants=" + layout.variantCatalog().size());
        use.say(TextColor.GRAY, "selected=" + selection
                + ", activeVariant=" + (activeVariant == null ? NONE : activeVariant.pieceKey())
                + ", size=" + (selectedDimensions == null ? NONE : selectedDimensions.width() + "x"
                + selectedDimensions.height() + "x" + selectedDimensions.depth())
                + ", enabled=" + (selectedBay == null ? "n/a" : selectedBay.enabled())
                + ", autosavePending=" + active.session().isDirty());
        if (evaluation.isPresent()) {
            JigsawStudioGraphEvaluation current = evaluation.get();
            use.say(TextColor.GRAY, "evaluation=" + current.state()
                    + ", seed=" + current.seed()
                    + ", theme=" + (current.selectedTheme().isBlank() ? "unthemed" : current.selectedTheme())
                    + ", pieces=" + current.pieceCount()
                    + ", detail=" + current.detail());
        } else {
            use.say(TextColor.GRAY, "evaluation=PENDING, seed=" + DEFAULT_STUDIO_SEED);
        }
    }

    private void menu(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty() || begun.get().requireActive() == null) {
            return;
        }
        StudioUse use = begun.get();
        use.onPlayer(() -> use.studio().openControlMenu(use.player()));
    }

    private void select(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        BlockPosition location = use.readFromPlayer(use.player()::position);
        JigsawStudioBay bay = active.session().layout().findAt(location.x(), location.y(), location.z());
        if (bay == null) {
            use.error("Stand inside a Jigsaw Studio workcell before selecting it.");
            return;
        }
        active.session().selectBay(bay.stableId());
        use.say(TextColor.GREEN, "Selected " + bay.stableId() + " (" + bay.kind() + ").");
    }

    private void bounds(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selected = StudioUse.selectedBay(active);
        if (selected == null) {
            use.error("Select a Jigsaw Studio workcell before changing its capacity.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = cellDimensions(context.get(REQUIRED_WIDTH), context.get(REQUIRED_HEIGHT), context.get(REQUIRED_DEPTH));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        if (dimensions.equals(selected.capacity())) {
            use.say(TextColor.YELLOW, "Workcell capacity is unchanged.");
            return;
        }
        use.onPlayer(() -> use.studio().updateWorkcellDimensions(use.player(), selected.stableId(), dimensions));
    }

    private void save(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String bay = context.get(BAY);
        if (SELECTED.equalsIgnoreCase(bay)) {
            JigsawStudioBay selected = StudioUse.selectedBay(active);
            if (selected == null) {
                use.error("Select a Jigsaw Studio workcell before flushing its autosave.");
                return;
            }
            use.onPlayer(() -> use.studio().flushAutosave(use.player(), selected.stableId()));
            return;
        }
        use.onPlayer(() -> use.studio().flushAutosave(use.player(), bay));
    }

    private void gotoBay(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty() || begun.get().requireActive() == null) {
            return;
        }
        StudioUse use = begun.get();
        String bay = context.get(GOTO_BAY);
        use.onPlayer(() -> use.studio().teleportTo(use.player(), bay));
    }

    private void particles(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty() || begun.get().requireActive() == null) {
            return;
        }
        StudioUse use = begun.get();
        boolean visible = context.get(VISIBLE);
        use.onPlayer(() -> use.studio().setParticles(use.player(), visible));
    }

    private static boolean studioIsActiveOrOpening(JigsawStudioRegistry registry) {
        return registry.activeOwnerId() != null
                || registry.openingOwnerId() != null;
    }

    private static void beginStagedOpen(JigsawStudioRegistry registry, JigsawStudioRegistry.StagedActivation staged) {
        if (!registry.beginStagedGeneration(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation is no longer current");
        }
    }

    private static void finishOpenAttempt(
            StudioUse use,
            String structureKey,
            UUID closingRequestId,
            JigsawStudioRegistry.StagedActivation staged,
            ToolWorld result,
            Throwable throwable
    ) {
        JigsawStudioRegistry registry = use.registry();
        registry.finishOpen(use.player().id());
        if (throwable == null && result != null) {
            use.studio().cancelClose(closingRequestId);
            return;
        }
        use.studio().cancelClose(closingRequestId);
        registry.rollback(staged);
        Throwable failure = unwrapCompletionFailure(throwable == null
                ? new IllegalStateException("Studio open completed without a result")
                : throwable);
        ToolPack pack = use.pack();
        if (use.host().isExpectedOpenDenial(failure)) {
            pack.log().warn("Jigsaw Studio open for '" + structureKey + "' was denied: " + failure.getMessage());
            return;
        }
        pack.log().error("Failed to open Jigsaw Studio for '" + structureKey + "'.", failure);
        use.error("Jigsaw Studio open failed: " + failure.getMessage());
    }

    /** Commits the staged studio once its world is open and moves the player in; the failure, or null when all went well. */
    private static Throwable commitOpenedStudio(
            StudioUse use,
            ToolWorld world,
            JigsawStudioRegistry.StagedActivation staged
    ) {
        try {
            enterCommittedStudio(use, world, staged);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static void enterCommittedStudio(
            StudioUse use,
            ToolWorld world,
            JigsawStudioRegistry.StagedActivation staged
    ) {
        if (world == null) {
            throw new IllegalStateException("Jigsaw Studio opened without a world");
        }
        JigsawStudioRegistry registry = use.registry();
        JigsawStudioRequest request = staged.request();
        JigsawStudioSession session = staged.session();
        JigsawStudioBay destination = initialBay(session.layout());
        double targetX = destination == null
                ? 0.5D
                : destination.bounds().originX() + destination.bounds().dimensions().width() / 2.0D;
        double targetY = destination == null ? 66D : destination.bounds().maxY() + 2.0D;
        double targetZ = destination == null
                ? 0.5D
                : destination.bounds().originZ() + destination.bounds().dimensions().depth() / 2.0D;
        if (destination != null) {
            session.selectBay(destination.stableId());
        }
        if (!registry.commit(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation could not be committed");
        }
        use.studio().activationCommitted(request.requestId());
        StudioSessions.bind(use.player().id(), new SessionBinding(
                request.packKey(), request.requestId()));
        ToolPlayer player = use.player();
        try {
            player.teleport(world, targetX, targetY, targetZ, 0.0F, 0.0F)
                    .thenRun(() -> player.creative(true));
        } catch (RuntimeException exception) {
            use.pack().log().error("Failed to move the player to the committed Jigsaw Studio workcell.", exception);
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

    private static JigsawStudioCellDimensions cellDimensions(long width, long height, long depth) {
        return new JigsawStudioCellDimensions((int) width, (int) height, (int) depth);
    }

    /** Whether the text says true, written either as a value or as {@code name=value}, which is how the commands tell it. */
    static boolean flagValue(String name, String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        String prefix = name + "=";
        return Boolean.parseBoolean(value.startsWith(prefix) ? value.substring(prefix.length()) : value);
    }

    static JigsawStudioMode parseMode(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "planar", "2d", "planar_jigsaw" -> JigsawStudioMode.PLANAR_JIGSAW;
            case "spatial", "3d", "spatial_jigsaw" -> JigsawStudioMode.SPATIAL_JIGSAW;
            default -> throw new IllegalArgumentException("Mode must be planar or spatial.");
        };
    }

    static JigsawStudioCompatibilityTarget parseCompatibility(String value) {
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

    static ResourceId parseRegisteredStructureKey(String value) {
        String normalized = Objects.requireNonNull(value, "Registered structure key").trim()
                .toLowerCase(Locale.ROOT);
        try {
            return ResourceId.ofPlatformKey(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Registered structure source must use a valid namespace:path key.");
        }
    }

    static String resolveConversionTarget(ResourceId source, String value) {
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
}
