package art.arcane.iris.core.commands;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(name = "pool", description = "Manage owned jigsaw pools", origin = DirectorOrigin.PLAYER)
public class CommandJigsawPool implements DirectorExecutor {
    @Director(description = "Create an empty owned jigsaw pool", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void create(
            @Param(description = "New owned pool resource key") String poolKey,
            @Param(description = "Owned fallback pool key, or none", defaultValue = "none")
            String fallbackPoolKey
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        String fallback = "none".equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioGraphEditor.createPool(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    poolKey,
                    fallback);
            return CommandJigsaw.mappedMutationResult(
                    context,
                    "",
                    "",
                    "Created owned pool '" + poolKey + "' with fallback '"
                            + (fallback.isBlank() ? "none" : fallback) + "'.");
        });
    }
}
