package com.volmit.iris.core.link;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.MissingResourceException;

@RequiredArgsConstructor
public abstract class ExternalDataProvider {

    @Getter
    private final String pluginId;

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(pluginId);
    }

    public boolean isReady() {
        return getPlugin() != null && getPlugin().isEnabled();
    }

    public abstract void init();

    public abstract BlockData getBlockData(Identifier blockId) throws MissingResourceException;

    public abstract ItemStack getItemStack(Identifier itemId) throws MissingResourceException;

    public abstract Identifier[] getBlockTypes();

    public abstract Identifier[] getItemTypes();

    public abstract boolean isValidProvider(Identifier id, boolean isItem);
}
