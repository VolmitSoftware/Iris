package com.volmit.iris.core.link;

import com.volmit.iris.util.collection.KList;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

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

        for(Identifier i : getItemTypes()) {
            itemNamespaces.addIfMissing(i.namespace());
        }
        for(Identifier i : getBlockTypes()) {
            blockNamespaces.addIfMissing(i.namespace());
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        return CustomBlock.getBaseBlockData(blockId.toString());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        CustomStack stack = CustomStack.getInstance(itemId.toString());
        if (stack == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return stack.getItemStack();
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomBlock.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomStack.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return isItem ? this.itemNamespaces.contains(id.namespace()) : this.blockNamespaces.contains(id.namespace());
    }
}
