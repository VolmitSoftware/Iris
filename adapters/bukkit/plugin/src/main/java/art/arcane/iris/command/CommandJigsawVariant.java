package art.arcane.iris.command;

import art.arcane.iris.studio.jigsaw.JigsawStudioBay;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariant;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.io.IOException;

@Director(name = "variant", description = "Manage selected jigsaw variants", origin = DirectorOrigin.PLAYER)
public class CommandJigsawVariant implements DirectorExecutor {
    @Director(description = "Set the selected piece weight in a pool", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void weight(
            @Param(description = "Pool resource key") String poolKey,
            @Param(description = "Positive selection weight") int weight
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioVariant selected = CommandJigsaw.selectedVariant(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before changing its weight.");
            return;
        }
        String pieceKey = selected.pieceKey();
        CommandJigsaw.runGraphMutation(player(), context, () -> {
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
            return CommandJigsaw.mappedMutationResult(
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selectedWorkcell = CommandJigsaw.selectedBay(context);
        JigsawStudioVariant selectedVariant = CommandJigsaw.selectedVariant(context);
        if (selectedWorkcell == null || selectedVariant == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before resizing it.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = new JigsawStudioCellDimensions(width, height, depth);
        } catch (IllegalArgumentException exception) {
            CommandJigsaw.sendError(this, exception.getMessage());
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selectedWorkcell = CommandJigsaw.selectedBay(context);
        JigsawStudioVariant selectedVariant = CommandJigsaw.selectedVariant(context);
        if (selectedWorkcell == null || selectedVariant == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before renaming it.");
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selectedWorkcell = CommandJigsaw.selectedBay(context);
        JigsawStudioVariant selectedVariant = CommandJigsaw.selectedVariant(context);
        if (selectedWorkcell == null || selectedVariant == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before resetting its label.");
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selected = CommandJigsaw.selectedBay(context);
        if (selected == null || CommandJigsaw.selectedVariant(context) == null) {
            CommandJigsaw.sendError(this, "Select a workcell with an active variant before duplicating it.");
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        String selectedTheme = themeKey;
        if ("next".equalsIgnoreCase(themeKey)) {
            try {
                selectedTheme = CommandJigsaw.nextThemeKey(context);
            } catch (IOException | ArithmeticException exception) {
                CommandJigsaw.sendError(this, exception.getMessage());
                return;
            }
        }
        JigsawStudioService.get().duplicateActiveFamily(player(), selectedTheme);
    }
}
