package art.arcane.iris.util.common.director.handlers;

import art.arcane.volmlib.util.director.handlers.base.WorldHandlerBase;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import org.bukkit.World;

public class WorldHandler extends WorldHandlerBase implements DirectorParameterHandler<World> {
    @Override
    protected String excludedPrefix() {
        return "iris/";
    }
}
