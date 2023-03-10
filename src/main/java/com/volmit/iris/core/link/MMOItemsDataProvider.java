package com.volmit.iris.core.link;

import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class MMOItemsDataProvider extends ExternalDataProvider {

    public MMOItemsDataProvider(String pluginId) {
        super(pluginId);
    }

    @Override
    public void init() {

    }

    @Override
    public BlockData getBlockData(NamespacedKey blockId) throws MissingResourceException {
        return null;
    }

    @Override
    public ItemStack getItemStack(NamespacedKey itemId) throws MissingResourceException {
        return null;
    }

    @Override
    public NamespacedKey[] getBlockTypes() {
        return new NamespacedKey[0];
    }

    @Override
    public NamespacedKey[] getItemTypes() {
        return new NamespacedKey[0];
    }

    @Override
    public boolean isValidProvider(NamespacedKey namespace, boolean isItem) {
        return false;
    }
}
