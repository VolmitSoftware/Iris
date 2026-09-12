package art.arcane.iris.command;

import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(name = "preview", description = "Assemble a read-only jigsaw preview",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsawPreview implements DirectorExecutor {
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
