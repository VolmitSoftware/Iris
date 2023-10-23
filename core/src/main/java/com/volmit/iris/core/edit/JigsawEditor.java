/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.edit;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.service.WandSVC;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
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
import java.util.List;
import java.util.concurrent.ExecutionException;

public class JigsawEditor implements Listener {
    public static final KMap<Player, JigsawEditor> editors = new KMap<>();
    private final Player player;
    private final IrisObject object;
    private final File targetSaveLocation;
    private final IrisJigsawPiece piece;
    private final Location origin;
    private final Cuboid cuboid;
    private final int ticker;
    private final KMap<IrisPosition, Runnable> falling = new KMap<>();
    private final ChronoLatch cl = new ChronoLatch(100);
    private Location target;

    public JigsawEditor(Player player, IrisJigsawPiece piece, IrisObject object, File saveLocation) {
        if (editors.containsKey(player)) {
            editors.get(player).close();
        }

        editors.put(player, this);
        if (object == null) {
            throw new RuntimeException("Object is null! " + piece.getObject());
        }
        this.object = object;
        this.player = player;
        origin = player.getLocation().clone().add(0, 7, 0);
        target = origin;
        this.targetSaveLocation = saveLocation;
        this.piece = piece == null ? new IrisJigsawPiece() : piece;
        this.piece.setObject(object.getLoadKey());
        cuboid = new Cuboid(origin.clone(), origin.clone().add(object.getW() - 1, object.getH() - 1, object.getD() - 1));
        ticker = J.sr(this::onTick, 0);
        J.s(() -> object.placeCenterY(origin));
        Iris.instance.registerListener(this);
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.getPlayer().equals(player)) {
            try {
                target = player.getTargetBlockExact(7).getLocation();
            } catch (Throwable ex) {
                Iris.reportError(ex);
                target = player.getLocation();
                return;
            }

            if (cuboid.contains(target)) {
                for (IrisPosition i : falling.k()) {
                    Location at = toLocation(i);

                    if (at.equals(target)) {
                        falling.remove(i).run();
                    }
                }
            }
        }
    }

    public Location toLocation(IrisPosition i) {
        return origin.clone()
                .add(new Vector(i.getX(), i.getY(), i.getZ()))
                .add(object.getCenter())
                .getBlock()
                .getLocation();
    }

    public IrisPosition toPosition(Location l) {
        return new IrisPosition(l.clone().getBlock().getLocation()
                .subtract(origin.clone())
                .subtract(object.getCenter())
                .add(1, 1, 1)
                .toVector());
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if (e.getClickedBlock() != null && cuboid.contains(e.getClickedBlock().getLocation()) && e.getPlayer().equals(player)) {
                IrisPosition pos = toPosition(e.getClickedBlock().getLocation());
                IrisJigsawPieceConnector connector = null;
                for (IrisJigsawPieceConnector i : piece.getConnectors()) {
                    if (i.getPosition().equals(pos)) {
                        connector = i;
                        break;
                    }
                }

                if (!player.isSneaking() && connector == null) {
                    connector = new IrisJigsawPieceConnector();
                    connector.setDirection(IrisDirection.getDirection(e.getBlockFace()));
                    connector.setPosition(pos);
                    piece.getConnectors().add(connector);
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
                } else if (player.isSneaking() && connector != null) {
                    piece.getConnectors().remove(connector);
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
                } else if (connector != null && !player.isSneaking()) {
                    connector.setDirection(IrisDirection.getDirection(e.getBlockFace()));
                    player.playSound(e.getClickedBlock().getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 1f);
                }
            }
        }
    }

    private void removeKey(JSONObject o, String... path) {
        if (path.length == 1) {
            o.remove(path[0]);
            return;
        }

        List<String> s = new java.util.ArrayList<>(List.of(path));
        s.remove(0);
        removeKey(o.getJSONObject(path[0]), s.toArray(new String[0]));
    }

    private List<JSONObject> getObjectsInArray(JSONObject a) { // This gets all the objects in an array that are connectors
        KList<JSONObject> o = new KList<>();

        for (int i = 0; i < a.getJSONArray("connectors").length(); i++) {
            o.add(a.getJSONArray("connectors").getJSONObject(i));
        }

        return o;
    }

    public void close() {
        exit();
        try {
            JSONObject j = new JSONObject(new Gson().toJson(piece));
            // Remove sub-key
            removeKey(j, "placementOptions", "translateCenter"); // should work
            J.attempt(() -> j.getJSONObject("placementOptions").remove("translateCenter")); // otherwise

            // remove root key
            removeKey(j, "placementOptions"); // should work
            j.remove("placementOptions"); // otherwise

            // Remove key in all objects in array
            for (JSONObject i : getObjectsInArray(j)) {
                removeKey(i, "rotateConnector");
            }

            IO.writeAll(targetSaveLocation, j.toString(4));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    public void exit() {
        J.car(ticker);
        Iris.instance.unregisterListener(this);
        try {
            J.sfut(() -> {
                object.unplaceCenterY(origin);
                falling.v().forEach(Runnable::run);
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        editors.remove(player);
    }

    public void onTick() {
        if (cl.flip()) {
            Iris.service(WandSVC.class).draw(cuboid, player);

            f:
            for (IrisPosition i : falling.k()) {
                for (IrisJigsawPieceConnector j : piece.getConnectors()) {
                    if (j.getPosition().equals(i)) {
                        continue f;
                    }
                }

                falling.remove(i).run();
            }

            for (IrisJigsawPieceConnector i : piece.getConnectors()) {
                IrisPosition pos = i.getPosition();
                Location at = toLocation(pos);

                Vector dir = i.getDirection().toVector().clone();


                for (int ix = 0; ix < RNG.r.i(1, 3); ix++) {
                    at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().getBlock().getLocation().add(0.25, 0.25, 0.25).add(RNG.r.d(0.5), RNG.r.d(0.5), RNG.r.d(0.5)), 0, dir.getX(), dir.getY(), dir.getZ(), 0.092 + RNG.r.d(-0.03, 0.08));
                }

                if (at.getBlock().getLocation().equals(target)) {
                    continue;
                }

                if (!falling.containsKey(pos)) {
                    if (at.getBlock().getType().isAir()) {
                        at.getBlock().setType(Material.STONE);
                    }

                    falling.put(pos, BlockSignal.forever(at.getBlock()));
                }
            }
        }
    }
}
