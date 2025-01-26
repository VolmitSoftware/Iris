package com.volmit.iris.core.link;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.IrisCustomData;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.MissingResourceException;

@Getter
@RequiredArgsConstructor
public abstract class ExternalDataProvider {

    @NonNull
    private final String pluginId;

    @Nullable
    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(pluginId);
    }

    public boolean isReady() {
        return getPlugin() != null && getPlugin().isEnabled();
    }

    public abstract void init();

    /**
     * @see ExternalDataProvider#getBlockData(Identifier, KMap)
     */
    @NotNull
    public BlockData getBlockData(@NotNull Identifier blockId) throws MissingResourceException {
        return getBlockData(blockId, new KMap<>());
    }

    /**
     * This method returns a {@link BlockData} corresponding to the blockID
     * it is used in any place Iris accepts {@link BlockData}
     *
     * @param blockId The id of the block to get
     * @param state The state of the block to get
     * @return Corresponding {@link BlockData} to the blockId
     *         may return {@link IrisCustomData} for blocks that need a world for placement
     * @throws MissingResourceException when the blockId is invalid
     */
    @NotNull
    public abstract BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException;

    /**
     * @see ExternalDataProvider#getItemStack(Identifier)
     */
    @NotNull
    public ItemStack getItemStack(@NotNull Identifier itemId) throws MissingResourceException {
        return getItemStack(itemId, new KMap<>());
    }

    /**
     * This method returns a {@link ItemStack} corresponding to the itemID
     * it is used in loot tables
     *
     * @param itemId The id of the item to get
     * @param customNbt Custom nbt to apply to the item
     * @return Corresponding {@link ItemStack} to the itemId
     * @throws MissingResourceException when the itemId is invalid
     */
    @NotNull
    public abstract ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException;

    /**
     * This method is used for placing blocks that need to use the plugins api
     * it will only be called when the {@link ExternalDataProvider#getBlockData(Identifier, KMap)} returned a {@link IrisCustomData}
     *
     * @param engine The engine of the world the block is being placed in
     * @param block The block where the block should be placed
     * @param blockId The blockId to place
     */
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {}

    public abstract @NotNull Identifier[] getBlockTypes();

    public abstract @NotNull Identifier[] getItemTypes();

    public abstract boolean isValidProvider(@NotNull Identifier id, boolean isItem);
}
