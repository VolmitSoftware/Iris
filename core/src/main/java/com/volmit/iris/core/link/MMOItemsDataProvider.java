package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.block.CustomBlock;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class MMOItemsDataProvider extends ExternalDataProvider {

    public MMOItemsDataProvider() {
        super("MMOItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up MMOItems Link...");
    }

    @Override
    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        int id = -1;
        try {
            id = Integer.parseInt(blockId.key());
        } catch (NumberFormatException ignored) {}
        CustomBlock block = api().getCustomBlocks().getBlock(id);
        if (block == null) throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return block.getState().getBlockData();
    }

    @Override
    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        String[] parts = itemId.namespace().split("_", 2);
        if (parts.length != 2)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        ItemStack item = api().getItem(parts[1], itemId.key());
        if (item == null)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        return item;
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> names = new KList<>();
        for (Integer id : api().getCustomBlocks().getBlockIds()) {
            try {
                Identifier key = new Identifier("mmoitems", String.valueOf(id));
                if (getBlockData(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }
        return names.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (Type type : api().getTypes().getAll()) {
            for (String name : api().getTemplates().getTemplateNames(type)) {
                try {
                    Identifier key = new Identifier("mmoitems_" + type, name);
                    if (getItemStack(key) != null)
                        names.add(key);
                } catch (MissingResourceException ignored) {
                }
            }
        }
        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return isItem ? id.namespace().split("_", 2).length == 2 : id.namespace().equals("mmoitems");
    }

    private MMOItems api() {
        return MMOItems.plugin;
    }
}
