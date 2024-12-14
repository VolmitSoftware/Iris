package com.volmit.iris.core.link;

import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        return ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(itemId.key())
                .map(item -> item.buildItem(1, Optional.empty()))
                .orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key()));
    }

    @NotNull
    @Override
    public Identifier[] getBlockTypes() {
        return new Identifier[0];
    }

    @NotNull
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
    public boolean isValidProvider(@NotNull Identifier key, boolean isItem) {
        return key.namespace().equalsIgnoreCase("executable_items") && isItem;
    }
}
