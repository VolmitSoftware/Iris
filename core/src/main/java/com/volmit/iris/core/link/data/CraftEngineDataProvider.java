package com.volmit.iris.core.link.data;

import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.properties.BooleanProperty;
import net.momirealms.craftengine.core.block.properties.IntegerProperty;
import net.momirealms.craftengine.core.block.properties.Property;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.stream.Stream;

public class CraftEngineDataProvider extends ExternalDataProvider {

    public CraftEngineDataProvider() {
        super("CraftEngine");
    }

    @Override
    public void init() {
    }

    @Override
    public @NotNull List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        var block = CraftEngineBlocks.byId(Key.of(blockId.namespace(), blockId.key()));
        if (block == null) throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return block.properties()
                .stream()
                .map(CraftEngineDataProvider::convert)
                .toList();
    }

    @Override
    public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        var item = CraftEngineItems.byId(Key.of(itemId.namespace(), itemId.key()));
        if (item == null) throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        return item.buildItemStack();
    }

    @Override
    public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        var key = Key.of(blockId.namespace(), blockId.key());
        if (CraftEngineBlocks.byId(key) == null && CraftEngineFurniture.byId(key) == null)
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return new IrisCustomData(B.getAir(), ExternalDataSVC.buildState(blockId, state));
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        var pair = ExternalDataSVC.parseState(blockId);
        var key = Key.of(blockId.namespace(), blockId.key());
        var state = pair.getB();

        var customBlock = CraftEngineBlocks.byId(key);
        if (customBlock != null) {
            ImmutableBlockState blockState = customBlock.defaultState();

            for (var entry : state.entrySet()) {
                var property = customBlock.getProperty(entry.getKey());
                if (property == null) continue;
                var tag = property.optional(entry.getValue()).orElse(null);
                if (tag == null) continue;
                blockState = ImmutableBlockState.with(blockState, property, tag);
            }
            CraftEngineBlocks.place(block.getLocation(), blockState, false);
            return;
        }

        var furniture = CraftEngineFurniture.byId(key);
        if (furniture == null) return;
        CraftEngineFurniture.place(block.getLocation(), furniture, furniture.getAnyAnchorType(), false);
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        return (switch (dataType) {
            case ENTITY -> Stream.<Key>empty();
            case ITEM -> CraftEngineItems.loadedItems().keySet().stream();
            case BLOCK -> Stream.concat(CraftEngineBlocks.loadedBlocks().keySet().stream(),
                    CraftEngineFurniture.loadedFurniture().keySet().stream());
        }).map(key -> new Identifier(key.namespace(), key.value())).toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        Key key = Key.of(id.namespace(), id.key());
        return switch (dataType) {
            case ENTITY -> false;
            case ITEM -> CraftEngineItems.byId(key) != null;
            case BLOCK -> (CraftEngineBlocks.byId(key) != null || CraftEngineFurniture.byId(key) != null);
        };
    }

    private static <T extends Comparable<T>> BlockProperty convert(Property<T> raw) {
        return switch (raw) {
            case BooleanProperty property -> BlockProperty.ofBoolean(property.name(), property.defaultValue());
            case IntegerProperty property -> BlockProperty.ofLong(property.name(), property.defaultValue(), property.min, property.max, false, false);
            default -> new BlockProperty(raw.name(), raw.valueClass(), raw.defaultValue(), raw.possibleValues(), raw::valueName);
        };
    }
}
