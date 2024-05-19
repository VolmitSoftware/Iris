package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.reflect.WrappedField;
import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class EcoItemsDataProvider extends ExternalDataProvider {
    private WrappedField<EcoItem, ItemStack> itemStack;
    private WrappedField<EcoItem, NamespacedKey> id;

    public EcoItemsDataProvider() {
        super("EcoItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up EcoItems Link...");
        itemStack = new WrappedField<>(EcoItem.class, "_itemStack");
        if (this.itemStack.hasFailed()) {
            Iris.error("Failed to set up EcoItems Link: Unable to fetch ItemStack field!");
        }
        id = new WrappedField<>(EcoItem.class, "id");
        if (this.id.hasFailed()) {
            Iris.error("Failed to set up EcoItems Link: Unable to fetch id field!");
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        EcoItem item = EcoItems.INSTANCE.getByID(itemId.key());
        if (item == null) throw new MissingResourceException("Failed to find Item!", itemId.namespace(), itemId.key());
        return itemStack.get(item).clone();
    }

    @Override
    public Identifier[] getBlockTypes() {
        return new Identifier[0];
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (EcoItem item : EcoItems.INSTANCE.values()) {
            try {
                Identifier key = Identifier.fromNamespacedKey(id.get(item));
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return id.namespace().equalsIgnoreCase("ecoitems") && isItem;
    }
}
