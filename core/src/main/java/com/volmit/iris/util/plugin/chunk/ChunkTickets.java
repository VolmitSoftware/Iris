package com.volmit.iris.util.plugin.chunk;

import com.volmit.iris.Iris;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.Map;

public class ChunkTickets implements Listener {
    private final Map<World, TicketHolder> holders = new HashMap<>();

    public ChunkTickets() {
        Iris.instance.registerListener(this);
        Bukkit.getWorlds().forEach(w -> holders.put(w, new TicketHolder(w)));
    }

    public TicketHolder getHolder(@NonNull World world) {
        return holders.get(world);
    }

    public void addTicket(@NonNull Chunk chunk) {
        addTicket(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public void addTicket(@NonNull World world, int x, int z) {
        var holder = getHolder(world);
        if (holder != null) holder.addTicket(x, z);
    }

    public boolean removeTicket(@NonNull Chunk chunk) {
        return removeTicket(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public boolean removeTicket(@NonNull World world, int x, int z) {
        var holder = getHolder(world);
        if (holder != null) return holder.removeTicket(x, z);
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(@NonNull WorldLoadEvent event) {
        holders.put(event.getWorld(), new TicketHolder(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(@NonNull WorldUnloadEvent event) {
        holders.remove(event.getWorld());
    }
}
