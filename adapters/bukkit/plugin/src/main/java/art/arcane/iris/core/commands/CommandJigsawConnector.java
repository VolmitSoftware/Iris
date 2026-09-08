package art.arcane.iris.core.commands;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Jigsaw;

@Director(name = "connector", description = "Inspect and configure targeted jigsaw connectors",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsawConnector implements DirectorExecutor {
    @Director(description = "Set the Iris-only channel on the targeted jigsaw marker", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void channel(
            @Param(description = "Exact channel, or none to clear") String channel
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        Block target = player().getTargetBlockExact(8);
        if (target == null || !(target.getBlockData() instanceof Jigsaw)) {
            CommandJigsaw.sendError(this, "Look directly at a jigsaw marker within eight blocks.");
            return;
        }
        JigsawStudioBay bay = context.session().layout().findAt(
                target.getX(), target.getY(), target.getZ());
        JigsawStudioVariant activeVariant = bay == null
                ? null
                : context.session().activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null) {
            CommandJigsaw.sendError(this, "The targeted marker must be inside a workcell with an active variant.");
            return;
        }
        if (!activeVariant.owned()) {
            CommandJigsaw.sendError(this, "The active variant is read-only. Adopt it into this project before editing it.");
            return;
        }
        IrisObject object = context.request().source().getObjectLoader().load(activeVariant.objectKey());
        if (object == null) {
            CommandJigsaw.sendError(this, "The active variant object '" + activeVariant.objectKey() + "' is missing.");
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
            CommandJigsaw.sendError(this, "Connector channel update failed: " + exception.getMessage());
            return;
        }
        String normalized = channel == null || channel.isBlank() || "none".equalsIgnoreCase(channel)
                ? "none"
                : channel.trim();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioGraphEditor.updateConnectorChannel(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    activeVariant.pieceKey(),
                    position,
                    channel);
            return CommandJigsaw.mappedMutationResult(
                    context,
                    "",
                    "",
                    "Persisted connector channel '" + normalized + "' on piece '"
                            + activeVariant.pieceKey() + "' at " + position.getX() + "," + position.getY()
                            + "," + position.getZ() + ".");
        });
    }
}
