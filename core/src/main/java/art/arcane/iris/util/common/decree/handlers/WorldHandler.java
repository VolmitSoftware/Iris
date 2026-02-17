package art.arcane.iris.util.decree.handlers;

import art.arcane.volmlib.util.director.handlers.base.WorldHandlerBase;
import art.arcane.iris.util.decree.DirectorParameterHandler;
import org.bukkit.World;

public class WorldHandler extends WorldHandlerBase implements DirectorParameterHandler<World> {
    @Override
    protected String excludedPrefix() {
        return "iris/";
    }
}
