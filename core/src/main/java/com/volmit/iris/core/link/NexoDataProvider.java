package com.volmit.iris.core.link;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NexoDataProvider extends ExternalDataProvider {
    private final AtomicBoolean failed = new AtomicBoolean(false);

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

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        ItemBuilder builder = NexoItems.itemFromId(itemId.key());
        if (builder == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return builder.build();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        var pair = ExternalDataSVC.parseState(blockId);
        var state = pair.getB();
        blockId = pair.getA();

        if (NexoBlocks.isCustomBlock(blockId.key())) {
            NexoBlocks.place(blockId.key(), block.getLocation());
            return;
        }

        if (!NexoFurniture.isFurniture(blockId.key()))
            return;

        float yaw = 0;
        BlockFace face = BlockFace.NORTH;

        long seed = engine.getSeedManager().getSeed() + Cache.key(block.getX(), block.getZ()) + block.getY();
        RNG rng = new RNG(seed);
        if ("true".equals(state.get("randomYaw"))) {
            yaw = rng.f(0, 360);
        } else if (state.containsKey("yaw")) {
            yaw = Float.parseFloat(state.get("yaw"));
        }
        if ("true".equals(state.get("randomFace"))) {
            BlockFace[] faces = BlockFace.values();
            face = faces[rng.i(0, faces.length - 1)];
        } else if (state.containsKey("face")) {
            face = BlockFace.valueOf(state.get("face").toUpperCase());
        }
        if (face == BlockFace.SELF) {
            face = BlockFace.NORTH;
        }
        ItemDisplay display = NexoFurniture.place(blockId.key(), block.getLocation(), yaw, face);
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
            if (itemStack.getItemMeta() instanceof PotionMeta meta) {
                meta.setColor(potionColor);
                itemStack.setItemMeta(meta);
            }
        }
        display.setItemStack(itemStack);
    }

    @NotNull
    @Override
    public Identifier[] getBlockTypes() {
        return Arrays.stream(NexoItems.itemNames())
                .map(i -> new Identifier("nexo", i))
                .filter(i -> {
                    try {
                        return getBlockData(i) != null;
                    } catch (MissingResourceException e) {
                        return false;
                    }
                })
                .toArray(Identifier[]::new);
    }

    @NotNull
    @Override
    public Identifier[] getItemTypes() {
        return Arrays.stream(NexoItems.itemNames())
                .map(i -> new Identifier("nexo", i))
                .filter(i -> {
                    try {
                        return getItemStack(i) != null;
                    } catch (MissingResourceException e) {
                        return false;
                    }
                })
                .toArray(Identifier[]::new);
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, boolean isItem) {
        return "nexo".equalsIgnoreCase(id.namespace());
    }

    @Override
    public boolean isReady() {
        return super.isReady() && !failed.get();
    }
}
