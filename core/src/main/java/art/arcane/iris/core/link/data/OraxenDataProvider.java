package art.arcane.iris.core.link.data;

import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

public final class OraxenDataProvider extends ExternalDataProvider implements Listener {
    public OraxenDataProvider() {
        super("Oraxen");
    }

    @Override
    public void init() {
    }

    @EventHandler
    public void onItemsLoaded(OraxenItemsLoadedEvent event) {
        ExternalDataSVC service = IrisServices.getOrNull(ExternalDataSVC.class);
        if (service != null) {
            service.notifyContentChanged();
        }
    }

    @Override
    public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) {
        if (!state.isEmpty()) {
            throw new MissingResourceException("Oraxen blocks do not expose Iris block properties.", blockId.namespace(), blockId.key());
        }
        BlockData data = OraxenBlocks.getOraxenBlockData(blockId.key());
        if (data == null) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }
        return IrisCustomData.of(data, blockId);
    }

    @Override
    public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) {
        ItemBuilder builder = OraxenItems.getItemById(itemId.key());
        if (builder == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return builder.build();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        OraxenBlocks.place(blockId.key(), block.getLocation());
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType == DataType.ENTITY) {
            return List.of();
        }
        return OraxenItems.getNames().stream()
                .filter(id -> dataType == DataType.ITEM || OraxenBlocks.isOraxenBlock(id))
                .map(id -> new Identifier("oraxen", id))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (!"oraxen".equalsIgnoreCase(id.namespace())) {
            return false;
        }
        return switch (dataType) {
            case BLOCK -> OraxenBlocks.isOraxenBlock(id.key());
            case ITEM -> OraxenItems.exists(id.key());
            case ENTITY -> false;
        };
    }
}
