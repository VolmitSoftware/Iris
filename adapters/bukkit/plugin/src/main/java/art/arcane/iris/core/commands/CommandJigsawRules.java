package art.arcane.iris.core.commands;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioStructureEditor;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(name = "rules", aliases = "rule", description = "Manage structure-wide jigsaw rules",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsawRules implements DirectorExecutor {
    @Director(description = "Set maximum expansion depth and horizontal radius", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void limits(
            @Param(description = "Maximum expansion depth") int maxDepth,
            @Param(description = "Maximum radius in chunks") int maxSizeChunks
    ) {
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        if (context.request().compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && (maxDepth > 20 || maxSizeChunks > 8)) {
            CommandJigsaw.sendError(this, "Vanilla-portable rules require maxDepth <= 20 and maxSizeChunks <= 8.");
            return;
        }
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioStructureEditor.updateLimits(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    maxDepth,
                    maxSizeChunks);
            return CommandJigsaw.mappedMutationResult(
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
        CommandJigsaw.ActiveContext context = CommandJigsaw.active(player());
        if (context == null) {
            CommandJigsaw.sendError(this, "Open a Jigsaw Studio project first.");
            return;
        }
        String fallback = "none".equalsIgnoreCase(fallbackPoolKey) ? "" : fallbackPoolKey;
        CommandJigsaw.runGraphMutation(player(), context, () -> {
            JigsawStudioPoolEditor.PoolUpdate update = JigsawStudioPoolEditor.updateFallback(
                    context.request().source().getDataFolder().toPath(),
                    context.request().structureKey(),
                    poolKey,
                    fallback);
            if (!update.changed()) {
                return CommandJigsaw.mappedMutationResult(context, "", "", "Pool fallback is unchanged.");
            }
            return CommandJigsaw.mappedMutationResult(
                    context,
                    "",
                    "",
                    "Persisted pool '" + poolKey + "' fallback as '"
                            + (fallback.isBlank() ? "none" : fallback) + "'.");
        });
    }
}
