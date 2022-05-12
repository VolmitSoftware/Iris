package com.volmit.iris.core.link;

import org.bukkit.block.data.BlockData;

import java.util.MissingResourceException;

public class ItemAdderLink extends BlockDataProvider {

    public ItemAdderLink() {
        super("ItemAdder", "itemadder");
    }

    @Override
    public BlockData getBlockData(String blockId) throws MissingResourceException {
        throw new MissingResourceException("Fuck you, not implemented yet.", getPluginId(), blockId);
    }

    @Override
    public String[] getBlockTypes() {
        return new String[0];
    }
}
