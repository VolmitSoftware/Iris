package com.volmit.iris.core.link.data;

import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
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
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        return ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(itemId.key())
                .map(item -> item.buildItem(1, Optional.empty()))
                .orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key()));
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType != DataType.ITEM) return List.of();
        return ExecutableItemsAPI.getExecutableItemsManager()
                .getExecutableItemIdsList()
                .stream()
                .map(name -> new Identifier("executable_items", name))
                .filter(dataType.asPredicate(this))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier key, DataType dataType) {
        return key.namespace().equalsIgnoreCase("executable_items") && dataType == DataType.ITEM;
    }
}
