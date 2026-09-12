package art.arcane.iris.platform.bukkit.chunk;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkTickets implements Listener {
    // Concurrent + self-healing: mutated from event handlers and read from command threads,
    // and a holder dropped by an aborted unload must recreate on next use, not NPE forever.
    private final Map<World, TicketHolder> holders = new ConcurrentHashMap<>();

    public ChunkTickets() {
        BukkitPlatform.volmitPlugin().registerListener(this);
        Bukkit.getWorlds().forEach(w -> holders.put(w, new TicketHolder(w)));
    }

    public TicketHolder getHolder(@NonNull World world) {
        return holders.computeIfAbsent(world, TicketHolder::new);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(@NonNull WorldUnloadEvent event) {
        TicketHolder holder = holders.remove(event.getWorld());
        if (holder != null) {
            holder.releaseAll();
        }
    }
}
