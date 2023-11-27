package com.volmit.iris.core.service;

import com.volmit.iris.core.tools.IrisToolbelt;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
public class ChunkHandlerSVC implements Listener {
    // Does nothing for now
    private final JavaPlugin plugin;
    private static BukkitTask task;
    private final Map<World, ChunkUnloader> worlds = new ConcurrentHashMap<>();

    private static final Map<Chunk, Set<Player>> playersInChunk = new ConcurrentHashMap<>();

    public ChunkHandlerSVC(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (World world : Bukkit.getWorlds()) {
            if (IrisToolbelt.isIrisWorld(world)) {
                worlds.put(world, new ChunkUnloader(plugin, world));
            }
        }

        startTask();
    }

    private void startTask() {
        if (task == null) {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    worlds.values().forEach(ChunkUnloader::update);
                }
            }.runTaskTimerAsynchronously(plugin, 0L, 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Chunk previousChunk = event.getFrom().getChunk();
        Chunk currentChunk = event.getTo().getChunk();

        if (!previousChunk.equals(currentChunk)) {
            playersInChunk.computeIfAbsent(previousChunk, k -> ConcurrentHashMap.newKeySet()).remove(player);
            playersInChunk.computeIfAbsent(currentChunk, k -> ConcurrentHashMap.newKeySet()).add(player);
        }
    }

    public static void exit() {
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (IrisToolbelt.isIrisWorld(world)) {
            worlds.put(world, new ChunkUnloader(plugin, world));
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        worlds.remove(event.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (worlds.containsKey(world)) {
            worlds.get(world).onChunkLoad(event.getChunk());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        World world = event.getWorld();
        if (worlds.containsKey(world)) {
            worlds.get(world).onChunkUnload(event.getChunk());
        }
    }

    private static class ChunkUnloader {
        private final JavaPlugin plugin;
        private final World world;
        private final Map<Chunk, Long> chunks = new ConcurrentHashMap<>();

        private ChunkUnloader(JavaPlugin plugin, World world) {
            this.plugin = plugin;
            this.world = world;
        }

        public void onChunkLoad(Chunk chunk) {
            // System.out.printf("%s > Loaded Chunk [x=%s, z=%s]%n", world.getName(), chunk.getX(), chunk.getZ());
            chunks.put(chunk, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3));
        }

        public void onChunkUnload(Chunk chunk) {
            chunks.remove(chunk);
            playersInChunk.remove(chunk);
        }

        public void update() {
            try {
                long currentTime = System.currentTimeMillis();
                Set<Chunk> chunkSet = new HashSet<>(chunks.keySet());
                for (Chunk chunk : chunkSet) {
                    if (!chunk.isLoaded()) {
                        continue;
                    }

                    if (isChunkNearby(chunk)) {
                        chunks.put(chunk, currentTime + TimeUnit.MINUTES.toMillis(3));
                    } else if (chunks.get(chunk) <= currentTime) {
                        unloadChunk(chunk);
                    }
                }
            } catch (Exception e) {
                // Log the error message
                System.out.println("Error in update method: " + e.getMessage());
            }
        }


        private boolean isChunkNearby(Chunk chunk) {
            Set<Player> players = playersInChunk.get(chunk);
            if (players == null) {
                players = ConcurrentHashMap.newKeySet();
                playersInChunk.put(chunk, players);
            }
            return !players.isEmpty();
        }

        private void unloadChunk(Chunk chunk) {
            try {
               // System.out.printf("%s > Unloading Chunk [x=%s, z=%s]%n", world.getName(), chunk.getX(), chunk.getZ());
                Bukkit.getScheduler().runTask(plugin, () -> chunk.unload(true));
            } catch (Exception e) {
                System.out.println("Error unloading chunk: " + e.getMessage());
            }
        }
    }
}
