package com.volmit.iris.core.link.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

public class ItemAdderDataProvider extends ExternalDataProvider {

    private KList<String> itemNamespaces, blockNamespaces;

    public ItemAdderDataProvider() {
        super("ItemsAdder");
    }

    @Override
    public void init() {
        this.itemNamespaces = new KList<>();
        this.blockNamespaces = new KList<>();

        for (Identifier i : getTypes(DataType.ITEM)) {
            itemNamespaces.addIfMissing(i.namespace());
        }
        for (Identifier i : getTypes(DataType.BLOCK)) {
            blockNamespaces.addIfMissing(i.namespace());
            Iris.info("Found ItemAdder Block: " + i);
        }
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        return CustomBlock.getBaseBlockData(blockId.toString());
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

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return dataType == DataType.ITEM ? this.itemNamespaces.contains(id.namespace()) : this.blockNamespaces.contains(id.namespace());
    }
}
