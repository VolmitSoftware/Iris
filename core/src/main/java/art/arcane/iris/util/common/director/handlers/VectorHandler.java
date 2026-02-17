package art.arcane.iris.util.director.handlers;

import art.arcane.iris.util.director.DirectorContext;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.iris.util.director.DirectorSystem;
import art.arcane.volmlib.util.director.handlers.base.VectorHandlerBase;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class VectorHandler extends VectorHandlerBase implements DirectorParameterHandler<Vector> {
    @Override
    protected boolean isSenderPlayer() {
        return DirectorContext.get().isPlayer();
    }

    @Override
    protected Vector getSenderVector() {
        return DirectorContext.get().player().getLocation().toVector();
    }

    @Override
    protected Vector getLookVector() {
        return DirectorContext.get().player().getTargetBlockExact(256, FluidCollisionMode.NEVER).getLocation().toVector();
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
