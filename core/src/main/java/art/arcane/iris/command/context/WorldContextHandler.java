package art.arcane.iris.command.context;

import art.arcane.volmlib.util.director.context.WorldContextHandlerBase;
import art.arcane.iris.command.DirectorContextHandler;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
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
