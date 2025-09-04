package com.volmit.iris.core.link.data;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

public class NexoDataProvider extends ExternalDataProvider {
    public NexoDataProvider() {
        super("Nexo");
    }

    @Override
    public void init() {
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        if (!NexoItems.exists(blockId.key())) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }

        Identifier blockState = ExternalDataSVC.buildState(blockId, state);
        if (NexoBlocks.isCustomBlock(blockId.key())) {
            BlockData data = NexoBlocks.blockData(blockId.key());
            if (data == null)
                throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
            return new IrisCustomData(data, blockState);
        } else if (NexoFurniture.isFurniture(blockId.key())) {
            return new IrisCustomData(B.getAir(), blockState);
        }

        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public @NotNull List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        if (!NexoItems.exists(blockId.key())) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }

        return NexoFurniture.isFurniture(blockId.key()) ? YAW_FACE_BIOME_PROPERTIES : List.of();
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        ItemBuilder builder = NexoItems.itemFromId(itemId.key());
        if (builder == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        try {
            return builder.build();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        var statePair = ExternalDataSVC.parseState(blockId);
        var state = statePair.getB();
        blockId = statePair.getA();

        if (NexoBlocks.isCustomBlock(blockId.key())) {
            NexoBlocks.place(blockId.key(), block.getLocation());
            return;
        }

        if (!NexoFurniture.isFurniture(blockId.key()))
            return;

        var pair = parseYawAndFace(engine, block, state);
        ItemDisplay display = NexoFurniture.place(blockId.key(), block.getLocation(), pair.getA(), pair.getB());
        if (display == null) return;
        ItemStack itemStack = display.getItemStack();
        if (itemStack == null) return;

        BiomeColor type = null;
        try {
            type = BiomeColor.valueOf(state.get("matchBiome").toUpperCase());
        } catch (NullPointerException | IllegalArgumentException ignored) {}

        if (type != null) {
            var biomeColor = INMS.get().getBiomeColor(block.getLocation(), type);
            if (biomeColor == null) return;
            var potionColor = Color.fromARGB(biomeColor.getAlpha(), biomeColor.getRed(), biomeColor.getGreen(), biomeColor.getBlue());
            var meta = itemStack.getItemMeta();
            switch (meta) {
                case LeatherArmorMeta armor -> armor.setColor(potionColor);
                case PotionMeta potion -> potion.setColor(potionColor);
                case MapMeta map -> map.setColor(potionColor);
                case null, default -> {}
            }
            itemStack.setItemMeta(meta);
        }
        display.setItemStack(itemStack);
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType == DataType.ENTITY) return List.of();
        return NexoItems.itemNames()
                .stream()
                .map(i -> new Identifier("nexo", i))
                .filter(dataType.asPredicate(this))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return "nexo".equalsIgnoreCase(id.namespace());
    }
}
