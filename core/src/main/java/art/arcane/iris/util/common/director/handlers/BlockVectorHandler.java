package art.arcane.iris.util.common.director.handlers;

import art.arcane.iris.util.common.director.DirectorContext;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.iris.util.common.director.DirectorSystem;
import art.arcane.volmlib.util.director.handlers.base.BlockVectorHandlerBase;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.List;

public class BlockVectorHandler extends BlockVectorHandlerBase implements DirectorParameterHandler<BlockVector> {
    @Override
    protected boolean isSenderPlayer() {
        return DirectorContext.get().isPlayer();
    }

    @Override
    protected BlockVector getSenderBlockVector() {
        return DirectorContext.get().player().getLocation().toVector().toBlockVector();
    }

    @Override
    protected BlockVector getLookBlockVector() {
        return DirectorContext.get().player().getTargetBlockExact(256, FluidCollisionMode.NEVER).getLocation().toVector().toBlockVector();
    }

    @Override
    protected List<?> playerPossibilities(String query) {
        return DirectorSystem.getHandler(Player.class).getPossibilities(query);
    }

    @Override
    protected String format(double value) {
        return Form.f(value, 2);
    }
}
