package art.arcane.iris.integration.data;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.integration.ExternalDataProvider;
import art.arcane.iris.integration.Identifier;
import art.arcane.volmlib.util.collection.KMap;
import com.willfp.eco.core.items.CustomItem;
import com.willfp.eco.core.items.Items;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

public class EcoItemsDataProvider extends ExternalDataProvider {
    public EcoItemsDataProvider() {
        super("EcoItems");
    }

    @Override
    public void init() {
        IrisLogging.info("Setting up EcoItems Link...");
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        ItemStack item = Items.lookup(itemId.namespace() + ":" + itemId.key()).getItem();
        if (item.getType().isAir()) {
            throw new MissingResourceException("Failed to find Item!", itemId.namespace(), itemId.key());
        }
        return item;
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType != DataType.ITEM) return List.of();
        return Items.getCustomItems()
                .stream()
                .map(CustomItem::getKey)
                .filter(key -> key.getNamespace().equalsIgnoreCase("ecoitems"))
                .map(key -> new Identifier(key.getNamespace(), key.getKey()))
                .filter(dataType.asPredicate(this))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        return id.namespace().equalsIgnoreCase("ecoitems") && dataType == DataType.ITEM;
    }
}
