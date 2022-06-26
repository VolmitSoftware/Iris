package com.volmit.iris.platform.bukkit;

import art.arcane.amulet.MagicalSugar;
import art.arcane.amulet.logging.LogListener;
import com.volmit.iris.engine.EngineConfiguration;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.engine.object.biome.NativeBiome;
import com.volmit.iris.engine.object.block.IrisBlock;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformDataTransformer;
import com.volmit.iris.platform.PlatformTransformer;
import com.volmit.iris.platform.bukkit.transformers.BukkitBiomeTransformer;
import com.volmit.iris.platform.bukkit.transformers.BukkitBlockDataTransformer;
import com.volmit.iris.platform.bukkit.transformers.BukkitNamespaceTransformer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBukkit extends JavaPlugin implements IrisPlatform<NamespacedKey, BlockData, Biome> {
    private static IrisBukkit instance;
    private PlatformTransformer<NamespacedKey, NSKey> namespaceTransformer;
    private PlatformDataTransformer<BlockData, IrisBlock> blockDataTransformer;
    private PlatformDataTransformer<Biome, NativeBiome> biomeTransformer;

    public void onEnable()
    {
        instance = this;
        namespaceTransformer = new BukkitNamespaceTransformer();
        blockDataTransformer = new BukkitBlockDataTransformer();
        biomeTransformer = new BukkitBiomeTransformer();
    }

    public void onDisable()
    {

    }

    public static IrisBukkit getInstance()
    {
        return instance;
    }

    @Override
    public String getPlatformName() {
        return "Bukkit";
    }
}
