package art.arcane.iris.util.decree.context;

import art.arcane.volmlib.util.decree.context.WorldContextHandlerBase;
import art.arcane.iris.util.decree.DecreeContextHandler;
import art.arcane.iris.util.plugin.VolmitSender;
import org.bukkit.World;

public class WorldContextHandler extends WorldContextHandlerBase<VolmitSender> implements DecreeContextHandler<World> {
    @Override
    protected boolean isPlayer(VolmitSender sender) {
        return sender.isPlayer();
    }

    @Override
    protected World getWorld(VolmitSender sender) {
        return sender.player().getWorld();
    }
}
