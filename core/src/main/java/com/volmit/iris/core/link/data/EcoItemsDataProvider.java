package com.volmit.iris.core.link.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.reflect.WrappedField;
import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
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

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        EcoItem item = EcoItems.INSTANCE.getByID(itemId.key());
        if (item == null) throw new MissingResourceException("Failed to find Item!", itemId.namespace(), itemId.key());
        return itemStack.get(item).clone();
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType != DataType.ITEM) return List.of();
        return EcoItems.INSTANCE.values()
                .stream()
                .map(x -> Identifier.fromNamespacedKey(id.get(x)))
                .filter(dataType.asPredicate(this))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        return id.namespace().equalsIgnoreCase("ecoitems") && dataType == DataType.ITEM;
    }
}
