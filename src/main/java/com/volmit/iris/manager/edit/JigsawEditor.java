package com.volmit.iris.manager.edit;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.*;
import com.volmit.iris.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;

public class JigsawEditor implements Listener {
    public static final KMap<Player, JigsawEditor> editors = new KMap<>();
    private final Player player;
    private final IrisObject object;
    private final File targetSaveLocation;
    private final IrisJigsawPiece piece;
    private final Location origin;
    private final Cuboid cuboid;
    private final int ticker;
    private Location target;
    private final KMap<IrisPosition, Runnable> falling = new KMap<>();
    private final ChronoLatch cl = new ChronoLatch(100);

    public JigsawEditor(Player player, IrisJigsawPiece piece, IrisObject object, File saveLocation)
    {
        if(editors.containsKey(player))
        {
            editors.get(player).close();
        }

        editors.put(player, this);
        this.object = object;
        this.player = player;
        origin = player.getLocation().clone().add(0, 7, 0);
        target = origin;
        this.targetSaveLocation = saveLocation;
        this.piece = piece == null ? new IrisJigsawPiece() : piece;
        this.piece.setObject(object.getLoadKey());
        cuboid = new Cuboid(origin.clone(), origin.clone().add(object.getW()-1, object.getH()-1, object.getD()-1));
        ticker = J.sr(this::onTick, 0);
        object.placeCenterY(origin);
        Iris.instance.registerListener(this);
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getPlayer().equals(player))
        {
            try
            {
                target = player.getTargetBlockExact(7).getLocation();
            }

            catch(Throwable ex)
            {
                target = player.getLocation();
                return;
            }

            if(cuboid.contains(target))
            {
                for(IrisPosition i : falling.k())
                {
                    Location at = toLocation(i);

                    if(at.equals(target))
                    {
                        falling.remove(i).run();
                    }
                }
            }
        }
    }

    public Location toLocation(IrisPosition i)
    {
        return origin.clone()
                .add(new Vector(i.getX(), i.getY(), i.getZ()))
                .add(object.getCenter())
                .getBlock()
                .getLocation();
    }

    public IrisPosition toPosition(Location l)
    {
        return new IrisPosition(l.clone().getBlock().getLocation()
                .subtract(origin.clone())
                .subtract(object.getCenter())
                .add(1,1,1)
                .toVector());
    }

    @EventHandler
    public void on(PlayerInteractEvent e)
    {
        if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
        {
            if(e.getClickedBlock() != null && cuboid.contains(e.getClickedBlock().getLocation()) && e.getPlayer().equals(player))
            {
                IrisPosition pos = toPosition(e.getClickedBlock().getLocation());
                IrisJigsawPieceConnector connector = null;
                for(IrisJigsawPieceConnector i : piece.getConnectors())
                {
                    if(i.getPosition().equals(pos))
                    {
                        connector = i;
                        break;
                    }
                }

                if(!player.isSneaking() && connector == null)
                {
                    connector = new IrisJigsawPieceConnector();
                    connector.setDirection(IrisDirection.getDirection(e.getBlockFace()));
                    connector.setPosition(pos);
                    piece.getConnectors().add(connector);
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
                }

                else if(player.isSneaking() && connector != null)
                {
                    piece.getConnectors().remove(connector);
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
                }

                else if(connector != null && !player.isSneaking())
                {
                    connector.setDirection(IrisDirection.getDirection(e.getBlockFace()));
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 1f);
                }
            }
        }
    }

    public void close()
    {
        J.car(ticker);
        Iris.instance.unregisterListener(this);
        object.unplaceCenterY(origin);
        editors.remove(player);
        falling.v().forEach(Runnable::run);
        try {
            IO.writeAll(targetSaveLocation, new JSONObject(new Gson().toJson(piece)).toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onTick()
    {
        if(cl.flip())
        {
            Iris.wand.draw(cuboid, player);

            f: for(IrisPosition i : falling.k())
            {
                for(IrisJigsawPieceConnector j : piece.getConnectors())
                {
                    if(j.getPosition().equals(i))
                    {
                        continue f;
                    }
                }

                falling.remove(i).run();
            }

            for(IrisJigsawPieceConnector i : piece.getConnectors())
            {
                IrisPosition pos = i.getPosition();
                Location at = toLocation(pos);

                Vector dir = i.getDirection().toVector().clone();


                for(int ix = 0; ix < RNG.r.i(1, 3); ix++)
                {
                    at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().getBlock().getLocation().add(0.25, 0.25, 0.25).add(RNG.r.d(0.5), RNG.r.d(0.5), RNG.r.d(0.5)), 0, dir.getX(), dir.getY(), dir.getZ(), 0.092 + RNG.r.d(-0.03, 0.08));
                }

                if(at.getBlock().getLocation().equals(target))
                {
                    continue;
                }

                if(!falling.containsKey(pos))
                {
                    if(at.getBlock().getType().isAir())
                    {
                        at.getBlock().setType(Material.STONE);
                    }

                    falling.put(pos, BlockSignal.forever(at.getBlock()));
                }
            }
        }
    }
}
