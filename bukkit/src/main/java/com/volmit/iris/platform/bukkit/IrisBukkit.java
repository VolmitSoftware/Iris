package com.volmit.iris.platform.bukkit;

import art.arcane.amulet.io.IO;
import art.arcane.amulet.logging.LogListener;
import com.volmit.iris.engine.EngineConfiguration;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.platform.block.PlatformBlock;
import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.platform.PlatformWorld;
import com.volmit.iris.platform.bukkit.wrapper.BukkitBiome;
import com.volmit.iris.platform.bukkit.wrapper.BukkitBlock;
import com.volmit.iris.platform.bukkit.wrapper.BukkitKey;
import com.volmit.iris.platform.bukkit.wrapper.BukkitWorld;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Warning;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

public class IrisBukkit extends JavaPlugin implements IrisPlatform, LogListener {
    private static final String TAG = ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "Iris" + ChatColor.DARK_GREEN + "/";
    private static final String TAG_STRIPPED = ChatColor.stripColor(TAG);
    private static IrisBukkit instance;
    private ConsoleCommandSender consoleSender;

    public void onEnable() {
        instance = this;
        consoleSender = Bukkit.getConsoleSender();
        LogListener.listener.set(this);

        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            World world = Bukkit.createWorld(new WorldCreator("iristests/" + UUID.randomUUID()).generator(new IrisBukkitChunkGenerator(this, EngineConfiguration.builder()
                .timings(true).mutable(true)
                .build())));
            for(Player i : Bukkit.getOnlinePlayers())
            {
                i.teleport(world.getSpawnLocation());
            }
        }, 10);
    }

    public void onDisable() {
        World w = null;
        for(World i : Bukkit.getWorlds()) {
            if(!i.getName().startsWith("iristest")) {
                w = i;
                break;
            }
        }

        for(World i : Bukkit.getWorlds()) {
            if(i.getName().startsWith("iristest"))
            {
                for(Player j : i.getPlayers())
                {
                    j.teleport(w.getSpawnLocation());
                }

                if(i.getGenerator() instanceof Closeable c)
                {
                    try {
                        c.close();
                    } catch(IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                BukkitWorld.of(i).unloadChunks(false, true);
                File folder = i.getWorldFolder();
                Bukkit.unloadWorld(i, false);
                IO.delete(folder);
            }
        }
    }

    public static IrisBukkit getInstance() {
        return instance;
    }

    private void messageConsole(String color, String key, Object o) {
        try {
            consoleSender.sendMessage(TAG + key + ChatColor.DARK_GRAY + "]: " + color + o.toString());
        }

        catch(Throwable e) {
            System.out.println(TAG_STRIPPED + key + "]: " + o.toString());
        }
    }

    @Override
    public void logError(String k, Object o) {
        messageConsole(ChatColor.RED.toString(), k, o);
    }

    @Override
    public void logInfo(String k, Object o) {
        messageConsole(ChatColor.WHITE.toString(), k, o);
    }

    @Override
    public void logWarning(String k, Object o) {
        messageConsole(ChatColor.YELLOW.toString(), k, o);
    }

    @Override
    public void logDebug(String k, Object o) {
        messageConsole(ChatColor.GRAY.toString(), k, o);
    }

    @Override
    public String getPlatformName() {
        return "Bukkit";
    }

    @Override
    public Stream<PlatformBlock> getBlocks() {
        //This is because it's a method extension
        //noinspection Convert2MethodRef
        return Arrays.stream(Material.values())
            .filter(i -> !i.isLegacy())
            .filter(Material::isBlock)
            .map(Material::createBlockData).map(i -> BukkitBlock.of(i));
    }

    @Override
    public Stream<PlatformBiome> getBiomes() {
        //This is because it's a method extension
        //noinspection Convert2MethodRef
        return Arrays.stream(Biome.values()).parallel().filter((i) -> i != Biome.CUSTOM).map(i -> BukkitBiome.of(i));
    }

    @Override
    public boolean isWorldLoaded(String name) {
        return Bukkit.getWorlds().keepWhere(i -> i.getName().equals(name)).isNotEmpty();
    }

    @Override
    public PlatformWorld getWorld(String name) {
        World w = Bukkit.getWorld(name);

        if(w == null)
        {
            return null;
        }

        return BukkitWorld.of(w);
    }

    @Override
    public PlatformBlock parseBlock(String raw) {
        return BukkitBlock.of(Bukkit.createBlockData(raw));
    }

    @Override
    public PlatformNamespaceKey key(String namespace, String key) {
        return BukkitKey.of(namespace, key);
    }

    @Override
    public File getStudioFolder(String dimension) {
        File f = new File(getDataFolder(), "packs/" + dimension);
        f.mkdirs();
        return f;
    }

    @Override
    public File getStudioFolder() {
        File f = new File(getDataFolder(), "packs/");
        f.mkdirs();
        return f;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new IrisBukkitChunkGenerator(this, EngineConfiguration.builder()
            .threads(4)
            .mutable(true)
            .timings(true)
            .build());
    }

    @Override
    public void i(String s, Object o) {
        logInfo(s, o);
    }

    @Override
    public void f(String s, Object o) {
        logError(s, o);
    }

    @Override
    public void w(String s, Object o) {
        logWarning(s, o);
    }

    @Override
    public void d(String s, Object o) {
        logDebug(s, o);
    }
}
