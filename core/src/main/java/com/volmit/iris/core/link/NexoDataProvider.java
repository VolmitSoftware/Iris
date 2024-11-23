package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisBlockData;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.reflect.WrappedReturningMethod;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NexoDataProvider extends ExternalDataProvider {
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private WrappedReturningMethod<?, String[]> itemNames;
    private WrappedReturningMethod<?, Boolean> exists;

    private WrappedReturningMethod<?, Boolean> isCustomBlock;
    private WrappedReturningMethod<?, Boolean> isFurniture;

    private WrappedReturningMethod<?, BlockData> getBlockData;
    private WrappedReturningMethod<?, ItemDisplay> placeFurniture;

    private WrappedReturningMethod<?, Object> itemFromId;
    private WrappedReturningMethod<?, ItemStack> buildItem;

    public NexoDataProvider() {
        super("Nexo");
    }

    @Override
    public void init() {
        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            Class<?> nexoBlocks = Class.forName("com.nexomc.nexo.api.NexoBlocks");
            Class<?> nexoFurniture = Class.forName("com.nexomc.nexo.api.NexoFurniture");
            Class<?> itemBuilder = Class.forName("com.nexomc.nexo.items.ItemBuilder");

            itemNames = new WrappedReturningMethod<>(nexoItems, "itemNames");
            exists = new WrappedReturningMethod<>(nexoItems, "exists", String.class);

            isCustomBlock = new WrappedReturningMethod<>(nexoBlocks, "isCustomBlock", String.class);
            isFurniture = new WrappedReturningMethod<>(nexoFurniture, "isFurniture", String.class);

            getBlockData = new WrappedReturningMethod<>(nexoBlocks, "blockData", String.class);
            placeFurniture = new WrappedReturningMethod<>(nexoFurniture, "place", String.class, Location.class, float.class, BlockFace.class);

            itemFromId = new WrappedReturningMethod<>(nexoItems, "itemFromId", String.class);
            buildItem = new WrappedReturningMethod<>(itemBuilder, "buildItem");
        } catch (Throwable e) {
            failed.set(true);
            Iris.error("Failed to initialize NexoDataProvider");
            e.printStackTrace();
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        if (!exists.invoke(blockId.key())) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }

        if (isCustomBlock.invoke(blockId.key())) {
            return getBlockData.invoke(blockId.key());
        } else if (isFurniture.invoke(blockId.key())) {
            return new IrisBlockData(B.getAir(), ExternalDataSVC.buildState(blockId, state));
        }

        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        Object o = itemFromId.invoke(itemId.key());
        if (o == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        ItemStack itemStack = buildItem.invoke(o, new Object[0]);
        if (itemStack == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return itemStack;
    }

    @Override
    public void processUpdate(Engine engine, Block block, Identifier blockId) {
        var pair = ExternalDataSVC.parseState(blockId);
        var state = pair.getB();
        blockId = pair.getA();

        if (!isFurniture.invoke(blockId.key()))
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
        ItemDisplay display = placeFurniture.invoke(blockId.key(), block.getLocation(), yaw, face);
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

    @Override
    public Identifier[] getBlockTypes() {
        return Arrays.stream(itemNames.invoke())
                .filter(i -> isCustomBlock.invoke(i) || isFurniture.invoke(i))
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

    @Override
    public Identifier[] getItemTypes() {
        return Arrays.stream(itemNames.invoke())
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
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return "nexo".equalsIgnoreCase(id.namespace());
    }

    @Override
    public boolean isReady() {
        return super.isReady() && !failed.get();
    }
}
