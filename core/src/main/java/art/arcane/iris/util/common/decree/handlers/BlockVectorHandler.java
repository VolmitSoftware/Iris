package art.arcane.iris.util.decree.handlers;

import art.arcane.iris.util.decree.DecreeContext;
import art.arcane.iris.util.decree.DecreeParameterHandler;
import art.arcane.iris.util.decree.DecreeSystem;
import art.arcane.volmlib.util.decree.handlers.base.BlockVectorHandlerBase;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.List;

public class BlockVectorHandler extends BlockVectorHandlerBase implements DecreeParameterHandler<BlockVector> {
    @Override
    protected boolean isSenderPlayer() {
        return DecreeContext.get().isPlayer();
    }

    @Override
    protected BlockVector getSenderBlockVector() {
        return DecreeContext.get().player().getLocation().toVector().toBlockVector();
    }

    @Override
    protected BlockVector getLookBlockVector() {
        return DecreeContext.get().player().getTargetBlockExact(256, FluidCollisionMode.NEVER).getLocation().toVector().toBlockVector();
    }

    @Override
    protected List<?> playerPossibilities(String query) {
        return DecreeSystem.getHandler(Player.class).getPossibilities(query);
    }

    @Override
    protected String format(double value) {
        return Form.f(value, 2);
    }
}
