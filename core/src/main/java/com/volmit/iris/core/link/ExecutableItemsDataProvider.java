package com.volmit.iris.core.link;

import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;
import java.util.Optional;

public class ExecutableItemsDataProvider extends ExternalDataProvider {
    public ExecutableItemsDataProvider() {
        super("ExecutableItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up ExecutableItems Link...");
    }

    @Override
    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        return ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(itemId.key())
                .map(item -> item.buildItem(1, Optional.empty()))
                .orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key()));
    }

    @Override
    public Identifier[] getBlockTypes() {
        return new Identifier[0];
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : ExecutableItemsAPI.getExecutableItemsManager().getExecutableItemIdsList()) {
            try {
                Identifier key = new Identifier("executable_items", name);
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier key, boolean isItem) {
        return key.namespace().equalsIgnoreCase("executable_items") && isItem;
    }
}
