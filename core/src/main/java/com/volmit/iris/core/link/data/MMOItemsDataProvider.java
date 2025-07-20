package com.volmit.iris.core.link.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.scheduling.J;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ItemTier;
import net.Indyuce.mmoitems.api.block.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class MMOItemsDataProvider extends ExternalDataProvider {

    public MMOItemsDataProvider() {
        super("MMOItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up MMOItems Link...");
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        int id = -1;
        try {
            id = Integer.parseInt(blockId.key());
        } catch (NumberFormatException ignored) {}
        CustomBlock block = api().getCustomBlocks().getBlock(id);
        if (block == null) throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return block.getState().getBlockData();
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        String[] parts = itemId.namespace().split("_", 2);
        if (parts.length != 2)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        Runnable run = () -> {
            try {
                var type = api().getTypes().get(parts[1]);
                int level = -1;
                ItemTier tier = null;

                if (customNbt != null) {
                    level = (int) customNbt.getOrDefault("level", -1);
                    tier = api().getTiers().get(String.valueOf(customNbt.get("tier")));
                }

                ItemStack itemStack;
                if (type == null) {
                    future.complete(null);
                    return;
                }

                if (level != -1 && tier != null) {
                    itemStack = api().getItem(type, itemId.key(), level, tier);
                } else {
                    itemStack = api().getItem(type, itemId.key());
                }
                future.complete(itemStack);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (Bukkit.isPrimaryThread()) run.run();
        else J.s(run);
        ItemStack item = null;
        try {
            item = future.get();
        } catch (InterruptedException | ExecutionException ignored) {}
        if (item == null)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        return item;
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        return switch (dataType) {
            case ENTITY -> List.of();
            case BLOCK -> api().getCustomBlocks().getBlockIds().stream().map(id -> new Identifier("mmoitems", String.valueOf(id)))
                    .filter(dataType.asPredicate(this))
                    .toList();
            case ITEM -> {
                Supplier<Collection<Identifier>> supplier = () -> api().getTypes()
                        .getAll()
                        .stream()
                        .flatMap(type -> api()
                                .getTemplates()
                                .getTemplateNames(type)
                                .stream()
                                .map(name -> new Identifier("mmoitems_" + type.getId(), name)))
                        .filter(dataType.asPredicate(this))
                        .toList();

                if (Bukkit.isPrimaryThread()) yield supplier.get();
                else yield J.sfut(supplier).join();
            }
        };
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return dataType == DataType.ITEM ? id.namespace().split("_", 2).length == 2 : id.namespace().equals("mmoitems");
    }

    private MMOItems api() {
        return MMOItems.plugin;
    }
}
