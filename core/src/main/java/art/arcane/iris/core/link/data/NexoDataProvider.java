package art.arcane.iris.core.link.data;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.container.BiomeColor;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.data.IrisCustomData;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

import static org.bukkit.Color.fromARGB;

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
            return IrisCustomData.of(data, blockState);
        } else if (NexoFurniture.isFurniture(blockId.key())) {
            return IrisCustomData.of(BukkitBlockResolution.getAir(), blockState);
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
            IrisLogging.reportError("Failed to build Nexo item data for " + itemId + ".", e);
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        Pair<Identifier, KMap<String, String>> statePair = ExternalDataSVC.parseState(blockId);
        KMap<String, String> state = statePair.getB();
        blockId = statePair.getA();

        if (NexoBlocks.isCustomBlock(blockId.key())) {
            NexoBlocks.place(blockId.key(), block.getLocation());
            return;
        }

        if (!NexoFurniture.isFurniture(blockId.key()))
            return;

        Pair<Float, BlockFace> pair = parseYawAndFace(engine, block, state);
        ItemDisplay display = NexoFurniture.place(blockId.key(), block.getLocation(), pair.getA(), pair.getB());
        if (display == null) return;
        ItemStack itemStack = display.getItemStack();
        if (itemStack == null) return;

        BiomeColor type = null;
        try {
            type = BiomeColor.valueOf(state.get("matchBiome").toUpperCase());
        } catch (NullPointerException | IllegalArgumentException ignored) {}

        if (type != null) {
            Color biomeColor = INMS.get().getBiomeColor(block.getLocation(), type);
            if (biomeColor == null) return;
            ItemMeta meta = itemStack.getItemMeta();
            switch (meta) {
                case LeatherArmorMeta armor -> armor.setColor(fromARGB(biomeColor.getAlpha(), biomeColor.getRed(), biomeColor.getGreen(), biomeColor.getBlue()));
                case PotionMeta potion -> potion.setColor(fromARGB(biomeColor.getAlpha(), biomeColor.getRed(), biomeColor.getGreen(), biomeColor.getBlue()));
                case MapMeta map -> map.setColor(fromARGB(biomeColor.getAlpha(), biomeColor.getRed(), biomeColor.getGreen(), biomeColor.getBlue()));
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
        if (!"nexo".equalsIgnoreCase(id.namespace())) {
            return false;
        }
        return switch (dataType) {
            case BLOCK -> NexoBlocks.isCustomBlock(id.key()) || NexoFurniture.isFurniture(id.key());
            case ITEM -> NexoItems.exists(id.key());
            case ENTITY -> false;
        };
    }
}
