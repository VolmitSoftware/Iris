package art.arcane.iris.util.decree.specialhandlers;

import art.arcane.volmlib.util.decree.handlers.base.NullablePlayerHandlerBase;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;
import art.arcane.iris.util.decree.DecreeParameterHandler;
import art.arcane.iris.util.decree.handlers.PlayerHandler;
import org.bukkit.entity.Player;

public class NullablePlayerHandler extends PlayerHandler implements DecreeParameterHandler<Player> {
    @Override
    public Player parse(String in, boolean force) throws DecreeParsingException {
        return NullablePlayerHandlerBase.parseNullable(this, in);
    }
}
