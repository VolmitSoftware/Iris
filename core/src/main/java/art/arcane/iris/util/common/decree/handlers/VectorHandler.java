package art.arcane.iris.util.decree.handlers;

import art.arcane.iris.util.decree.DecreeContext;
import art.arcane.iris.util.decree.DirectorParameterHandler;
import art.arcane.iris.util.decree.DecreeSystem;
import art.arcane.volmlib.util.director.handlers.base.VectorHandlerBase;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class VectorHandler extends VectorHandlerBase implements DirectorParameterHandler<Vector> {
    @Override
    protected boolean isSenderPlayer() {
        return DecreeContext.get().isPlayer();
    }

    @Override
    protected Vector getSenderVector() {
        return DecreeContext.get().player().getLocation().toVector();
    }

    @Override
    protected Vector getLookVector() {
        return DecreeContext.get().player().getTargetBlockExact(256, FluidCollisionMode.NEVER).getLocation().toVector();
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
