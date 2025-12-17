package com.volmit.iris.util.plugin.chunk;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.collection.KMap;
import lombok.NonNull;
import org.bukkit.Chunk;
import org.bukkit.World;

public class TicketHolder {
    private final World world;
    private final KMap<Long, Long> tickets = new KMap<>();

    public TicketHolder(@NonNull World world) {
        this.world = world;
    }

    public void addTicket(@NonNull Chunk chunk) {
        if (chunk.getWorld() != world) return;
        addTicket(chunk.getX(), chunk.getZ());
    }

    public void addTicket(int x, int z) {
        tickets.compute(Cache.key(x, z), ($, ref) -> {
            if (ref == null) {
                world.addPluginChunkTicket(x, z, Iris.instance);
                return 1L;
            }
            return ++ref;
        });
    }

    public boolean removeTicket(@NonNull Chunk chunk) {
        if (chunk.getWorld() != world) return false;
        return removeTicket(chunk.getX(), chunk.getZ());
    }

    public boolean removeTicket(int x, int z) {
        return tickets.compute(Cache.key(x, z), ($, ref) -> {
            if (ref == null) return null;
            if (--ref <= 0) {
                world.removePluginChunkTicket(x, z, Iris.instance);
                return null;
            }
            return ref;
        }) == null;
    }
}
