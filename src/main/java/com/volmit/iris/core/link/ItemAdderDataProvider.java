package com.volmit.iris.core.link;

import com.volmit.iris.util.collection.KList;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class ItemAdderDataProvider extends ExternalDataProvider {

    public ItemAdderDataProvider() {
        super("ItemsAdder");
    }

    @Override
    public void init() { }

    @Override
    public BlockData getBlockData(NamespacedKey blockId) throws MissingResourceException {
        return CustomBlock.getBaseBlockData(blockId.toString());
    }

    @Override
    public ItemStack getItemStack(NamespacedKey itemId) throws MissingResourceException {
        CustomStack stack = CustomStack.getInstance(itemId.toString());
        if(stack == null)
            throw new MissingResourceException("Failed to find ItemData!", itemId.getNamespace(), itemId.getKey());
        return stack.getItemStack();
    }

    @Override
    public NamespacedKey[] getBlockTypes() {
        KList<NamespacedKey> keys = new KList<>();
        for(String s : ItemsAdder.getNamespacedBlocksNamesInConfig())
            keys.add(NamespacedKey.fromString(s));
        return keys.toArray(new NamespacedKey[0]);
    }

    @Override
    public boolean isValidProvider(NamespacedKey blockId) {
        for(NamespacedKey k : getBlockTypes())
            if(k.equals(blockId)) {
                return true;
            }
        return false;
    }
}
