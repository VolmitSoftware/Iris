package com.volmit.iris.platform.bukkit;

import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.PlatformWorld;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.stream.Stream;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBukkit extends JavaPlugin implements IrisPlatform {
    private static IrisBukkit instance;

    public void onEnable() {
        instance = this;
    }

    public void onDisable() {

    }

    public static IrisBukkit getInstance() {
        return instance;
    }

    @Override
    public String getPlatformName() {
        return "Bukkit";
    }

    @Override
    public Stream<PlatformBlock> getBlocks() {
        //This is because it's a method extension
        //noinspection Convert2MethodRef
        return Arrays.stream(Material.values()).parallel().filter(Material::isBlock).map(Material::createBlockData).map(i -> i.bukkitBlock());
    }

    @Override
    public Stream<PlatformBiome> getBiomes() {
        //This is because it's a method extension
        //noinspection Convert2MethodRef
        return Arrays.stream(Biome.values()).parallel().filter((i) -> i != Biome.CUSTOM).map(i -> i.bukkitBiome());
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

        return w.bukkitWorld();
    }
}
