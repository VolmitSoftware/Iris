package art.arcane.iris.util.director.context;

import art.arcane.volmlib.util.director.context.WorldContextHandlerBase;
import art.arcane.iris.util.director.DirectorContextHandler;
import art.arcane.iris.util.plugin.VolmitSender;
import org.bukkit.World;

public class WorldContextHandler extends WorldContextHandlerBase<VolmitSender> implements DirectorContextHandler<World> {
    @Override
    protected boolean isPlayer(VolmitSender sender) {
        return sender.isPlayer();
    }

    @Override
    protected World getWorld(VolmitSender sender) {
        return sender.player().getWorld();
    }
}
