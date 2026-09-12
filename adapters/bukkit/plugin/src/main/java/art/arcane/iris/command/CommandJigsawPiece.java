package art.arcane.iris.command;

import art.arcane.iris.studio.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.studio.jigsaw.JigsawStudioBay;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.studio.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioMode;
import art.arcane.iris.studio.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariant;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.Location;

import java.io.IOException;

@Director(name = "piece", description = "Manage contextual Studio pieces", origin = DirectorOrigin.PLAYER)
public class CommandJigsawPiece implements DirectorExecutor {
    @Director(description = "Create an owned piece from the selected Studio topology", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void create(
            @Param(description = "Owned pool resource key") String poolKey,
            @Param(description = "New owned piece and object key") String pieceKey,
            @Param(description = "Positive selection weight", defaultValue = "1") int weight
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawPlanarTopology topology = null;
        JigsawStudioBay targetWorkcell = context.session().layout().get(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
        if (context.session().layout().mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            Location location = player().getLocation();
            JigsawStudioBay contextual = context.session().layout().findAt(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (contextual == null) {
                contextual = CommandJigsaw.selectedBay(context);
            } else {
                context.session().selectBay(contextual.stableId());
            }
            if (contextual == null || contextual.topology().isEmpty()) {
                CommandJigsaw.sendError(this, "Stand in or select a planar topology workcell before creating a variant.");
                return;
            }
            topology = contextual.topology().get();
            targetWorkcell = contextual;
        }
        if (targetWorkcell == null) {
            CommandJigsaw.sendError(this, "The selected Jigsaw Studio workcell is no longer available.");
            return;
        }
        JigsawPlanarTopology selectedTopology = topology;
        String activationWorkcellId = targetWorkcell.stableId();
        JigsawStudioCellDimensions dimensions = targetWorkcell.capacity();
        String topologyText = selectedTopology == null ? "blank spatial" : selectedTopology.name();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioGraphEditor.createPiece(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    poolKey,
                    pieceKey,
                    weight,
                    dimensions,
                    selectedTopology);
            return CommandJigsaw.mappedMutationResult(
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        IrisJigsawPiece piece = context.request().source().getJigsawPieceLoader().load(pieceKey);
        if (piece == null) {
            CommandJigsaw.sendError(this, "No jigsaw piece '" + pieceKey + "' exists in this pack.");
            return;
        }
        if (piece.getObject() == null || piece.getObject().isBlank()) {
            CommandJigsaw.sendError(this, "Jigsaw piece '" + pieceKey + "' does not declare an object.");
            return;
        }
        IrisObject object = context.request().source().getObjectLoader().load(piece.getObject());
        if (object == null) {
            CommandJigsaw.sendError(this, "Jigsaw piece '" + pieceKey + "' has no loadable object.");
            return;
        }
        CommandJigsaw.PieceWorkcellResolution resolution;
        try {
            resolution = CommandJigsaw.resolvePieceWorkcell(context.session().layout(), piece, object);
        } catch (IllegalArgumentException exception) {
            CommandJigsaw.sendError(this, "Jigsaw piece '" + pieceKey + "' cannot be assigned to a Studio workcell: "
                    + exception.getMessage());
            return;
        }
        if (!resolution.fits()) {
            IrisPosition required = resolution.requiredDimensions();
            JigsawStudioCellDimensions capacity = resolution.workcell().capacity();
            CommandJigsaw.sendError(this, "Jigsaw piece '" + pieceKey + "' requires " + required.getX() + "x"
                    + required.getY() + "x" + required.getZ() + " in " + resolution.workcell().displayName()
                    + ", but that workcell capacity is " + capacity.width() + "x" + capacity.height()
                    + "x" + capacity.depth() + ".");
            return;
        }
        String objectKey = piece.getObject();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
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
            JigsawStudioLayout layout = CommandJigsaw.loadPersistedLayout(context);
            String activationWorkcellId = CommandJigsaw.workcellIdForVariant(layout, pieceKey);
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioVariant selected = CommandJigsaw.selectedVariant(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before removing its pool entry.");
            return;
        }
        String pieceKey = selected.pieceKey();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.removePiece(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    poolKey,
                    pieceKey);
            if (!update.changed()) {
                throw new IOException("The owned pool does not reference selected piece '" + pieceKey + "'");
            }
            return CommandJigsaw.mappedMutationResult(
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioVariant selected = CommandJigsaw.selectedVariant(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before changing rotation.");
            return;
        }
        if (!rotatable
                && context.request().compatibilityTarget()
                == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            CommandJigsaw.sendError(this, "Vanilla-portable pieces must remain rotatable.");
            return;
        }
        String pieceKey = selected.pieceKey();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioGraphEditor.updateRotatable(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    pieceKey,
                    rotatable);
            return CommandJigsaw.mappedMutationResult(
                    context,
                    "",
                    "",
                    "Persisted piece '" + pieceKey + "' rotatable=" + rotatable + ".");
        });
    }

    @Director(description = "Resize the selected piece object exactly to workcell capacity", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void expand() {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay workcell = CommandJigsaw.selectedBay(context);
        if (workcell == null) {
            CommandJigsaw.sendError(this, "Select a workcell before expanding its active variant.");
            return;
        }
        JigsawStudioService.get().expandVariantToCell(player(), workcell.stableId());
    }
}
