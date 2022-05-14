package com.volmit.iris.core.link;

import com.volmit.iris.util.collection.KList;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;

import java.util.MissingResourceException;

public class ItemAdderLink extends BlockDataProvider {

    public ItemAdderLink() {
        super("ItemsAdder");
    }

    @Override
    public void init() { }

    @Override
    public BlockData getBlockData(NamespacedKey blockId) throws MissingResourceException {
        return CustomBlock.getBaseBlockData(blockId.toString());
    }

    @Override
    public NamespacedKey[] getBlockTypes() {
        KList<NamespacedKey> keys = new KList<>();
        for(String s : ItemsAdder.getNamespacedBlocksNamesInConfig())
            keys.add(NamespacedKey.fromString(s));
        return keys.toArray(new NamespacedKey[0]);
    }

    @Override
    public boolean isProviderBlock(NamespacedKey blockId) {
        for(NamespacedKey k : getBlockTypes())
            if(k.equals(blockId)) {
                return true;
            }
        return false;
    }
}
