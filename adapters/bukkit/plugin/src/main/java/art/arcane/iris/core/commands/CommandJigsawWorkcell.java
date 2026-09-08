package art.arcane.iris.core.commands;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(name = "workcell", aliases = "cell", description = "Manage the selected Studio workcell",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsawWorkcell implements DirectorExecutor {
    @Director(description = "Set capacity without resizing any variant object", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void capacity(
            @Param(description = "Capacity width") int width,
            @Param(description = "Capacity height") int height,
            @Param(description = "Capacity depth") int depth
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selected = CommandJigsaw.selectedBay(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell before changing its capacity.");
            return;
        }
        JigsawStudioCellDimensions dimensions;
        try {
            dimensions = new JigsawStudioCellDimensions(width, height, depth);
        } catch (IllegalArgumentException exception) {
            CommandJigsaw.sendError(this, exception.getMessage());
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selected = CommandJigsaw.selectedBay(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell before renaming it.");
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        JigsawStudioBay selected = CommandJigsaw.selectedBay(context);
        if (selected == null) {
            CommandJigsaw.sendError(this, "Select a workcell before resetting its label.");
            return;
        }
        JigsawStudioService.get().updateWorkcellDisplayName(player(), selected.stableId(), "");
    }
}
