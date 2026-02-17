package art.arcane.iris.util.decree.specialhandlers;

import art.arcane.volmlib.util.director.handlers.base.NullablePlayerHandlerBase;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.iris.util.decree.DirectorParameterHandler;
import art.arcane.iris.util.decree.handlers.PlayerHandler;
import org.bukkit.entity.Player;

public class NullablePlayerHandler extends PlayerHandler implements DirectorParameterHandler<Player> {
    @Override
    public Player parse(String in, boolean force) throws DirectorParsingException {
        return NullablePlayerHandlerBase.parseNullable(this, in);
    }
}
