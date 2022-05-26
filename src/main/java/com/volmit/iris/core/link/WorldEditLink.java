package com.volmit.iris.core.link;

import com.mojang.datafixers.util.Pair;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.MissingSessionException;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldEditLink {

    public static Pair<Location, Location> getSelection(Player p) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(p));
        try {
            if(session == null)
                throw new MissingSessionException();
            Region r = session.getSelection(BukkitAdapter.adapt(p.getWorld()));
            BlockVector3 p1 = r.getMinimumPoint();
            BlockVector3 p2 = r.getMaximumPoint();
            return new Pair<>(new Location(p.getWorld(), p1.getX(), p1.getY(), p1.getZ()), new Location(p.getWorld(), p2.getX(), p2.getY(), p2.getZ()));
        } catch(MissingSessionException e) {
            return new Pair<>(null, new Location(null, 0, 0, 0));
        } catch(IncompleteRegionException e) {
            return new Pair<>(new Location(null, 0, 0, 0), null);
        }

    }
}
