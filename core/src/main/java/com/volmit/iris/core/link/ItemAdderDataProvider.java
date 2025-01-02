package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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

        for (Identifier i : getItemTypes()) {
            itemNamespaces.addIfMissing(i.namespace());
        }
        for (Identifier i : getBlockTypes()) {
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

    @NotNull
    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomBlock.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @NotNull
    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomStack.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, boolean isItem) {
        return isItem ? this.itemNamespaces.contains(id.namespace()) : this.blockNamespaces.contains(id.namespace());
    }
}
