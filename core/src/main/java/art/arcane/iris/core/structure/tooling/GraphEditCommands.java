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

import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioStructureEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.tooling.GraphMutations.PieceWorkcellResolution;
import art.arcane.iris.core.structure.tooling.StudioSessions.ActiveContext;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.value.IrisPosition;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.world.ToolWorld;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static art.arcane.iris.core.structure.tooling.GraphMutations.loadPersistedLayout;
import static art.arcane.iris.core.structure.tooling.GraphMutations.mappedMutationResult;
import static art.arcane.iris.core.structure.tooling.GraphMutations.runGraphMutation;
import static art.arcane.iris.core.structure.tooling.StudioUse.selectedBay;
import static art.arcane.iris.core.structure.tooling.StudioUse.selectedVariant;

/** The commands that edit the open graph: connectors, pools, pieces, workcells, variants, the rules and the preview. */
final class GraphEditCommands {
    private static final String NONE = "none";
    private static final String JIGSAW_BLOCK = "minecraft:jigsaw";
    private static final int CONNECTOR_REACH_BLOCKS = 8;
    private static final int VANILLA_PORTABLE_MAX_DEPTH = 20;
    private static final int VANILLA_PORTABLE_MAX_SIZE_CHUNKS = 8;
    private static final String NEXT = "next";

    private static final Argument<String> CHANNEL = Argument.word("channel").describe("Exact channel, or none to clear");
    private static final Argument<String> NEW_POOL = Argument.word("poolKey").describe("New owned pool resource key");
    private static final Argument<String> NEW_POOL_FALLBACK = Argument.word("fallbackPoolKey").orElse(NONE)
            .describe("Owned fallback pool key, or none");
    private static final Argument<String> OWNED_POOL = Argument.word("poolKey").describe("Owned pool resource key");
    private static final Argument<String> NEW_PIECE = Argument.word("pieceKey").describe("New owned piece and object key");
    private static final Argument<String> EXISTING_PIECE = Argument.word("pieceKey").describe("Existing jigsaw piece key");
    private static final Argument<Long> OPTIONAL_WEIGHT = weight().orElse(1L);
    private static final Argument<Boolean> ROTATABLE = Argument.flag("rotatable")
            .describe("Whether the selected piece may rotate");
    private static final Argument<Long> CAPACITY_WIDTH = size("width", "Capacity width");
    private static final Argument<Long> CAPACITY_HEIGHT = size("height", "Capacity height");
    private static final Argument<Long> CAPACITY_DEPTH = size("depth", "Capacity depth");
    private static final Argument<String> LABEL = Argument.word("displayName")
            .describe("Display label; quote labels containing spaces");
    private static final Argument<String> VARIANT_POOL = Argument.word("poolKey").describe("Pool resource key");
    private static final Argument<Long> REQUIRED_WEIGHT = weight();
    private static final Argument<Long> VARIANT_WIDTH = size("width", "Variant width");
    private static final Argument<Long> VARIANT_HEIGHT = size("height", "Variant height");
    private static final Argument<Long> VARIANT_DEPTH = size("depth", "Variant depth");
    private static final Argument<String> THEME_KEY = Argument.word("themeKey").orElse(NEXT)
            .describe("Family key or next");
    private static final Argument<Long> MAX_DEPTH = Argument.integer("maxDepth", Integer.MIN_VALUE, Integer.MAX_VALUE)
            .describe("Maximum expansion depth");
    private static final Argument<Long> MAX_SIZE_CHUNKS = Argument.integer("maxSizeChunks", Integer.MIN_VALUE, Integer.MAX_VALUE)
            .describe("Maximum radius in chunks");
    private static final Argument<String> FALLBACK_POOL = Argument.word("fallbackPoolKey")
            .describe("Owned fallback pool key, or none");
    private static final Argument<Long> ASSEMBLY_SEED = Argument.integer("seed", Long.MIN_VALUE, Long.MAX_VALUE)
            .orElse(StudioLifecycleCommands.DEFAULT_STUDIO_SEED).describe("Assembly seed");

    private final Supplier<Optional<JigsawStudioHost>> hosts;

    GraphEditCommands(Supplier<Optional<JigsawStudioHost>> hosts) {
        this.hosts = hosts;
    }

    Command connector() {
        return Command.named("connector").describe("Inspect and configure targeted jigsaw connectors")
                .child(Command.named("channel").describe("Set the Iris-only channel on the targeted jigsaw marker")
                        .argument(CHANNEL).runs(this::connectorChannel));
    }

    Command pool() {
        return Command.named("pool").describe("Manage owned jigsaw pools")
                .child(Command.named("create").describe("Create an empty owned jigsaw pool")
                        .argument(NEW_POOL).argument(NEW_POOL_FALLBACK).runs(this::poolCreate));
    }

    Command piece() {
        return Command.named("piece").describe("Manage contextual Studio pieces")
                .child(Command.named("create").describe("Create an owned piece from the selected Studio topology")
                        .argument(OWNED_POOL).argument(NEW_PIECE).argument(OPTIONAL_WEIGHT).runs(this::pieceCreate))
                .child(Command.named("add").describe("Add an existing jigsaw piece to an owned pool transactionally")
                        .argument(OWNED_POOL).argument(EXISTING_PIECE).argument(OPTIONAL_WEIGHT).runs(this::pieceAdd))
                .child(Command.named("remove").describe("Remove the selected piece from an owned pool transactionally")
                        .argument(OWNED_POOL).runs(this::pieceRemove))
                .child(Command.named("rotatable").describe("Allow or forbid cardinal rotation of the selected piece")
                        .argument(ROTATABLE).runs(this::pieceRotatable))
                .child(Command.named("expand").describe("Resize the selected piece object exactly to workcell capacity")
                        .runs(this::pieceExpand));
    }

    Command workcell() {
        return Command.named("workcell").describe("Manage the selected Studio workcell")
                .child(Command.named("capacity").describe("Set capacity without resizing any variant object")
                        .argument(CAPACITY_WIDTH).argument(CAPACITY_HEIGHT).argument(CAPACITY_DEPTH)
                        .runs(this::workcellCapacity))
                .child(Command.named("label").describe("Set the selected workcell's author-facing label")
                        .argument(LABEL).runs(this::workcellLabel))
                .child(Command.named("label-reset").alias("reset-label")
                        .describe("Reset the selected workcell to its canonical solver label")
                        .runs(this::workcellLabelReset));
    }

    Command variant() {
        return Command.named("variant").describe("Manage selected jigsaw variants")
                .child(Command.named("weight").describe("Set the selected piece weight in a pool")
                        .argument(VARIANT_POOL).argument(REQUIRED_WEIGHT).runs(this::variantWeight))
                .child(Command.named("resize").describe("Resize only the active variant object within workcell capacity")
                        .argument(VARIANT_WIDTH).argument(VARIANT_HEIGHT).argument(VARIANT_DEPTH).runs(this::variantResize))
                .child(Command.named("label").describe("Set the active variant's author-facing label")
                        .argument(LABEL).runs(this::variantLabel))
                .child(Command.named("label-reset").alias("reset-label")
                        .describe("Reset the active variant to its resource-key label").runs(this::variantLabelReset))
                .child(Command.named("duplicate").describe("Duplicate the active variant in only this workcell")
                        .runs(this::variantDuplicate))
                .child(Command.named("duplicate-family").alias("family")
                        .describe("Duplicate every enabled workcell's active variant as one coherent family")
                        .argument(THEME_KEY).runs(this::variantDuplicateFamily));
    }

    Command rules() {
        return Command.named("rules").alias("rule").describe("Manage structure-wide jigsaw rules")
                .child(Command.named("limits").describe("Set maximum expansion depth and horizontal radius")
                        .argument(MAX_DEPTH).argument(MAX_SIZE_CHUNKS).runs(this::rulesLimits))
                .child(Command.named("fallback").describe("Set or clear one owned pool's direct fallback")
                        .argument(OWNED_POOL).argument(FALLBACK_POOL).runs(this::rulesFallback));
    }

    Command preview() {
        return Command.named("preview").describe("Assemble a read-only jigsaw preview")
                .child(Command.named("goto").alias("teleport").describe("Teleport to the live seed-1337 preview")
                        .runs(this::previewGoto))
                .child(Command.named("assemble").describe("Assemble a deterministic preview at your location")
                        .argument(ASSEMBLY_SEED).runs(this::previewAssemble));
    }

    private static Argument<Long> weight() {
        return Argument.integer("weight", Integer.MIN_VALUE, Integer.MAX_VALUE).describe("Positive selection weight");
    }

    private static Argument<Long> size(String name, String description) {
        return Argument.integer(name, Integer.MIN_VALUE, Integer.MAX_VALUE).describe(description);
    }

    private static JigsawStudioCellDimensions dimensions(long width, long height, long depth) {
        return new JigsawStudioCellDimensions((int) width, (int) height, (int) depth);
    }

    private void connectorChannel(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        BlockPosition target = targetedJigsawMarker(use);
        if (target == null) {
            use.error("Look directly at a jigsaw marker within eight blocks.");
            return;
        }
        var bay = active.session().layout().findAt(target.x(), target.y(), target.z());
        JigsawStudioVariant activeVariant = bay == null
                ? null
                : active.session().activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null) {
            use.error("The targeted marker must be inside a workcell with an active variant.");
            return;
        }
        if (!activeVariant.owned()) {
            use.error("The active variant is read-only. Adopt it into this project before editing it.");
            return;
        }
        IrisObject object = active.request().source().getObjectLoader().load(activeVariant.objectKey());
        if (object == null) {
            use.error("The active variant object '" + activeVariant.objectKey() + "' is missing.");
            return;
        }
        IrisPosition canonicalPosition = new IrisPosition(
                target.x() - bay.bounds().originX(),
                target.y() - bay.bounds().originY(),
                target.z() - bay.bounds().originZ());
        IrisPosition position;
        try {
            position = activeVariant.canonicalToSourcePosition(
                    canonicalPosition,
                    new JigsawStudioCellDimensions(object.getW(), object.getH(), object.getD()));
        } catch (IllegalArgumentException exception) {
            use.error("Connector channel update failed: " + exception.getMessage());
            return;
        }
        String channel = context.get(CHANNEL);
        String normalized = channel == null || channel.isBlank() || NONE.equalsIgnoreCase(channel)
                ? NONE
                : channel.trim();
        runGraphMutation(use, active, () -> {
            JigsawStudioGraphEditor.updateConnectorChannel(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    activeVariant.pieceKey(),
                    position,
                    channel);
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Persisted connector channel '" + normalized + "' on piece '"
                            + activeVariant.pieceKey() + "' at " + position.getX() + "," + position.getY()
                            + "," + position.getZ() + ".");
        });
    }

    /** Where the player looks when it is a jigsaw marker within reach; null otherwise. */
    private static BlockPosition targetedJigsawMarker(StudioUse use) {
        Optional<BlockPosition> target = use.readFromPlayer(() -> use.player().targetBlock(CONNECTOR_REACH_BLOCKS));
        Optional<ToolWorld> world = use.readFromPlayer(() -> use.player().world());
        if (target.isEmpty() || world.isEmpty()) {
            return null;
        }
        try {
            boolean marker = world.get().call(target.get(), () -> world.get().block(target.get()))
                    .get().material().toString().equals(JIGSAW_BLOCK);
            return marker ? target.get() : null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException failure) {
            return null;
        }
    }

    private void poolCreate(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(NEW_POOL);
        String fallbackPoolKey = context.get(NEW_POOL_FALLBACK);
        String fallback = NONE.equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
        runGraphMutation(use, active, () -> {
            JigsawStudioGraphEditor.createPool(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    fallback);
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Created owned pool '" + poolKey + "' with fallback '"
                            + (fallback.isBlank() ? NONE : fallback) + "'.");
        });
    }

    private void pieceCreate(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(OWNED_POOL);
        String pieceKey = context.get(NEW_PIECE);
        int weight = context.get(OPTIONAL_WEIGHT).intValue();
        JigsawPlanarTopology topology = null;
        JigsawStudioBay targetWorkcell = active.session().layout().get(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
        if (active.session().layout().mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            BlockPosition location = use.readFromPlayer(use.player()::position);
            JigsawStudioBay contextual = active.session().layout().findAt(location.x(), location.y(), location.z());
            if (contextual == null) {
                contextual = selectedBay(active);
            } else {
                active.session().selectBay(contextual.stableId());
            }
            if (contextual == null || contextual.topology().isEmpty()) {
                use.error("Stand in or select a planar topology workcell before creating a variant.");
                return;
            }
            topology = contextual.topology().get();
            targetWorkcell = contextual;
        }
        if (targetWorkcell == null) {
            use.error("The selected Jigsaw Studio workcell is no longer available.");
            return;
        }
        JigsawPlanarTopology selectedTopology = topology;
        String activationWorkcellId = targetWorkcell.stableId();
        JigsawStudioCellDimensions dimensions = targetWorkcell.capacity();
        String topologyText = selectedTopology == null ? "blank spatial" : selectedTopology.name();
        runGraphMutation(use, active, () -> {
            JigsawStudioGraphEditor.createPiece(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    pieceKey,
                    weight,
                    dimensions,
                    selectedTopology);
            return mappedMutationResult(
                    active,
                    activationWorkcellId,
                    pieceKey,
                    "Created owned " + topologyText + " piece '" + pieceKey
                            + "' and added it to pool '" + poolKey + "' atomically.");
        });
    }

    private void pieceAdd(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(OWNED_POOL);
        String pieceKey = context.get(EXISTING_PIECE);
        int weight = context.get(OPTIONAL_WEIGHT).intValue();
        IrisJigsawPiece piece = StructureResources.piece(active.request().source().getPackResources(), pieceKey);
        if (piece == null) {
            use.error("No jigsaw piece '" + pieceKey + "' exists in this pack.");
            return;
        }
        if (piece.getObject() == null || piece.getObject().isBlank()) {
            use.error("Jigsaw piece '" + pieceKey + "' does not declare an object.");
            return;
        }
        IrisObject object = active.request().source().getObjectLoader().load(piece.getObject());
        if (object == null) {
            use.error("Jigsaw piece '" + pieceKey + "' has no loadable object.");
            return;
        }
        PieceWorkcellResolution resolution;
        try {
            resolution = GraphMutations.resolvePieceWorkcell(active.session().layout(), piece, object);
        } catch (IllegalArgumentException exception) {
            use.error("Jigsaw piece '" + pieceKey + "' cannot be assigned to a Studio workcell: "
                    + exception.getMessage());
            return;
        }
        if (!resolution.fits()) {
            IrisPosition required = resolution.requiredDimensions();
            JigsawStudioCellDimensions capacity = resolution.workcell().capacity();
            use.error("Jigsaw piece '" + pieceKey + "' requires " + required.getX() + "x"
                    + required.getY() + "x" + required.getZ() + " in " + resolution.workcell().displayName()
                    + ", but that workcell capacity is " + capacity.width() + "x" + capacity.height()
                    + "x" + capacity.depth() + ".");
            return;
        }
        String objectKey = piece.getObject();
        runGraphMutation(use, active, () -> {
            if (!JigsawStudioGraphEditor.ownsPiece(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    pieceKey,
                    objectKey)) {
                throw new IOException("Piece add accepts only piece/object resources already owned by this project; "
                        + "use piece create for a new owned piece");
            }
            JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.addPiece(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    pieceKey,
                    weight);
            if (!update.changed()) {
                throw new IOException("The owned pool is missing or already references '" + pieceKey + "'");
            }
            JigsawStudioLayout layout = loadPersistedLayout(active);
            String activationWorkcellId = GraphMutations.workcellIdForVariant(layout, pieceKey);
            return new JigsawStudioService.CommandGraphMutationResult(
                    layout,
                    activationWorkcellId,
                    pieceKey,
                    "Added '" + pieceKey + "' to pool '" + poolKey + "' in one graph transaction.");
        });
    }

    private void pieceRemove(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(OWNED_POOL);
        JigsawStudioVariant selected = selectedVariant(active);
        if (selected == null) {
            use.error("Select a workcell with an active variant before removing its pool entry.");
            return;
        }
        String pieceKey = selected.pieceKey();
        runGraphMutation(use, active, () -> {
            JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.removePiece(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    pieceKey);
            if (!update.changed()) {
                throw new IOException("The owned pool does not reference selected piece '" + pieceKey + "'");
            }
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Removed '" + pieceKey + "' from pool '" + poolKey + "' in one graph transaction.");
        });
    }

    private void pieceRotatable(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        boolean rotatable = context.get(ROTATABLE);
        JigsawStudioVariant selected = selectedVariant(active);
        if (selected == null) {
            use.error("Select a workcell with an active variant before changing rotation.");
            return;
        }
        if (!rotatable
                && active.request().compatibilityTarget()
                == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            use.error("Vanilla-portable pieces must remain rotatable.");
            return;
        }
        String pieceKey = selected.pieceKey();
        runGraphMutation(use, active, () -> {
            JigsawStudioGraphEditor.updateRotatable(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    pieceKey,
                    rotatable);
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Persisted piece '" + pieceKey + "' rotatable=" + rotatable + ".");
        });
    }

    private void pieceExpand(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay workcell = selectedBay(active);
        if (workcell == null) {
            use.error("Select a workcell before expanding its active variant.");
            return;
        }
        use.onPlayer(() -> use.studio().expandVariantToCell(use.player(), workcell.stableId()));
    }

    private void workcellCapacity(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selected = selectedBay(active);
        if (selected == null) {
            use.error("Select a workcell before changing its capacity.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = dimensions(context.get(CAPACITY_WIDTH), context.get(CAPACITY_HEIGHT), context.get(CAPACITY_DEPTH));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        use.onPlayer(() -> use.studio().updateWorkcellDimensions(use.player(), selected.stableId(), dimensions));
    }

    private void workcellLabel(CommandContext context) {
        renameWorkcell(context, "Select a workcell before renaming it.", LABEL);
    }

    private void workcellLabelReset(CommandContext context) {
        renameWorkcell(context, "Select a workcell before resetting its label.", null);
    }

    /** Names the selected workcell by {@code label}, or resets its label when there is none. */
    private void renameWorkcell(CommandContext context, String noSelectionMessage, Argument<String> label) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selected = selectedBay(active);
        if (selected == null) {
            use.error(noSelectionMessage);
            return;
        }
        String displayName = label == null ? "" : context.get(label);
        use.onPlayer(() -> use.studio().updateWorkcellDisplayName(use.player(), selected.stableId(), displayName));
    }

    private void variantWeight(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(VARIANT_POOL);
        int weight = context.get(REQUIRED_WEIGHT).intValue();
        JigsawStudioVariant selected = selectedVariant(active);
        if (selected == null) {
            use.error("Select a workcell with an active variant before changing its weight.");
            return;
        }
        String pieceKey = selected.pieceKey();
        runGraphMutation(use, active, () -> {
            JigsawStudioPoolEditor.WeightUpdate update = JigsawStudioPoolEditor.updateWeight(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    pieceKey,
                    weight);
            if (!update.changed()) {
                throw new IOException("Pool '" + poolKey + "' does not reference selected piece '"
                        + pieceKey + "'");
            }
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Updated " + update.changedEntries() + " pool entry weight atomically in "
                            + update.poolPath() + ".");
        });
    }

    private void variantResize(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selectedWorkcell = selectedBay(active);
        JigsawStudioVariant selectedVariant = selectedVariant(active);
        if (selectedWorkcell == null || selectedVariant == null) {
            use.error("Select a workcell with an active variant before resizing it.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = dimensions(context.get(VARIANT_WIDTH), context.get(VARIANT_HEIGHT), context.get(VARIANT_DEPTH));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        use.onPlayer(() -> use.studio().resizeVariant(
                use.player(),
                selectedWorkcell.stableId(),
                selectedVariant.pieceKey(),
                dimensions));
    }

    private void variantLabel(CommandContext context) {
        renameVariant(context, "Select a workcell with an active variant before renaming it.", LABEL);
    }

    private void variantLabelReset(CommandContext context) {
        renameVariant(context, "Select a workcell with an active variant before resetting its label.", null);
    }

    /** Names the active variant by {@code label}, or resets its label when there is none. */
    private void renameVariant(CommandContext context, String noSelectionMessage, Argument<String> label) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selectedWorkcell = selectedBay(active);
        JigsawStudioVariant selectedVariant = selectedVariant(active);
        if (selectedWorkcell == null || selectedVariant == null) {
            use.error(noSelectionMessage);
            return;
        }
        String displayName = label == null ? "" : context.get(label);
        use.onPlayer(() -> use.studio().updateVariantDisplayName(
                use.player(),
                selectedWorkcell.stableId(),
                selectedVariant.pieceKey(),
                displayName));
    }

    private void variantDuplicate(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        JigsawStudioBay selected = selectedBay(active);
        if (selected == null || selectedVariant(active) == null) {
            use.error("Select a workcell with an active variant before duplicating it.");
            return;
        }
        use.onPlayer(() -> use.studio().createVariant(use.player(), selected.stableId(), true));
    }

    private void variantDuplicateFamily(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String themeKey = context.get(THEME_KEY);
        String selectedTheme = themeKey;
        if (NEXT.equalsIgnoreCase(themeKey)) {
            try {
                selectedTheme = GraphMutations.nextThemeKey(active);
            } catch (IOException | ArithmeticException exception) {
                use.error(exception.getMessage());
                return;
            }
        }
        String familyKey = selectedTheme;
        use.onPlayer(() -> use.studio().duplicateActiveFamily(use.player(), familyKey));
    }

    private void rulesLimits(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        int maxDepth = context.get(MAX_DEPTH).intValue();
        int maxSizeChunks = context.get(MAX_SIZE_CHUNKS).intValue();
        if (active.request().compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && (maxDepth > VANILLA_PORTABLE_MAX_DEPTH || maxSizeChunks > VANILLA_PORTABLE_MAX_SIZE_CHUNKS)) {
            use.error("Vanilla-portable rules require maxDepth <= 20 and maxSizeChunks <= 8.");
            return;
        }
        runGraphMutation(use, active, () -> {
            JigsawStudioStructureEditor.updateLimits(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    maxDepth,
                    maxSizeChunks);
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Persisted jigsaw limits: maxDepth=" + maxDepth
                            + ", maxSizeChunks=" + maxSizeChunks + ".");
        });
    }

    private void rulesFallback(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        String poolKey = context.get(OWNED_POOL);
        String fallbackPoolKey = context.get(FALLBACK_POOL);
        String fallback = NONE.equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
        runGraphMutation(use, active, () -> {
            JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.updateFallback(
                    active.request().source().getDataFolder().toPath(),
                    active.request().structureKey(),
                    poolKey,
                    fallback);
            if (!update.changed()) {
                return mappedMutationResult(active, "", "", "Pool fallback is unchanged.");
            }
            return mappedMutationResult(
                    active,
                    "",
                    "",
                    "Persisted pool '" + poolKey + "' fallback as '"
                            + (fallback.isBlank() ? NONE : fallback) + "'.");
        });
    }

    private void previewGoto(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        use.onPlayer(() -> use.studio().goToPreview(use.player()));
    }

    private void previewAssemble(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        long seed = context.get(ASSEMBLY_SEED);
        use.onPlayer(() -> use.studio().previewStudio(use.player(), seed));
    }
}
