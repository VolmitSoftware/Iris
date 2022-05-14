package com.volmit.iris.core.link;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.MissingResourceException;

@RequiredArgsConstructor
public abstract class BlockDataProvider {

    @Getter
    private final String pluginId;

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(pluginId);
    }

    public boolean isPresent() {
        return getPlugin() != null;
    }

    public abstract void init();

    public abstract BlockData getBlockData(NamespacedKey blockId) throws MissingResourceException;

    public abstract NamespacedKey[] getBlockTypes();

    public abstract boolean isProviderBlock(NamespacedKey namespace);
}
