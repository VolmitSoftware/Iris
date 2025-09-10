package com.volmit.iris.core.link.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.IrisCustomData;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

public class ItemAdderDataProvider extends ExternalDataProvider {

    private final KSet<String> itemNamespaces = new KSet<>();
    private final KSet<String> blockNamespaces = new KSet<>();

    public ItemAdderDataProvider() {
        super("ItemsAdder");
    }

    @Override
    public void init() {
        updateNamespaces();
    }

    @EventHandler
    public void onLoadData(ItemsAdderLoadDataEvent event) {
        updateNamespaces();
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        CustomBlock block = CustomBlock.getInstance(blockId.toString());
        if (block == null) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }
        return new IrisCustomData(block.getBaseBlockData(), blockId);
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        CustomStack stack = CustomStack.getInstance(itemId.toString());
        if (stack == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return stack.getItemStack();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        CustomBlock custom;
        if ((custom = CustomBlock.place(blockId.toString(), block.getLocation())) == null)
            return;
        block.setBlockData(custom.getBaseBlockData(), false);
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        return switch (dataType) {
            case ENTITY -> List.of();
            case ITEM -> CustomStack.getNamespacedIdsInRegistry()
                    .stream()
                    .map(Identifier::fromString)
                    .toList();
            case BLOCK -> CustomBlock.getNamespacedIdsInRegistry()
                    .stream()
                    .map(Identifier::fromString)
                    .toList();
        };
    }

    private void updateNamespaces() {
        try {
            updateNamespaces(DataType.ITEM);
            updateNamespaces(DataType.BLOCK);
        } catch (Throwable e) {
            Iris.warn("Failed to update ItemAdder namespaces: " + e.getMessage());
        }
    }

    private void updateNamespaces(DataType dataType) {
        var namespaces = getTypes(dataType).stream().map(Identifier::namespace).collect(Collectors.toSet());
        var currentNamespaces = dataType == DataType.ITEM ? itemNamespaces : blockNamespaces;
        currentNamespaces.removeIf(n -> !namespaces.contains(n));
        currentNamespaces.addAll(namespaces);
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return dataType == DataType.ITEM ? itemNamespaces.contains(id.namespace()) : blockNamespaces.contains(id.namespace());
    }
}
