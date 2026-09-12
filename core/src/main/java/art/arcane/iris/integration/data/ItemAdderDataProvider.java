package art.arcane.iris.integration.data;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.integration.ExternalDataProvider;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.generation.block.IrisCustomData;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.stream.Collectors;

public class ItemAdderDataProvider extends ExternalDataProvider implements Listener {

    private volatile ContentRegistry content = new ContentRegistry(Set.of(), Set.of(), Set.of(), false);

    public ItemAdderDataProvider() {
        super("ItemsAdder");
    }

    @Override
    public void init() {
        if (ItemsAdder.areItemsLoaded()) {
            updateContent();
        }
    }

    @Override
    public boolean isReady() {
        return super.isReady() && content.ready();
    }

    @EventHandler
    public void onLoadData(ItemsAdderLoadDataEvent event) {
        if (!updateContent()) {
            return;
        }
        ExternalDataSVC service = IrisServices.getOrNull(ExternalDataSVC.class);
        if (service != null) {
            service.notifyContentChanged();
        }
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        if (!state.isEmpty()) {
            throw new MissingResourceException("ItemsAdder blocks do not expose Iris block properties.", blockId.namespace(), blockId.key());
        }
        CustomBlock block = CustomBlock.getInstance(blockId.toString());
        if (block == null) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }
        BlockData base = block.getBaseBlockData();
        if (base == null) {
            throw new MissingResourceException("Failed to find ItemsAdder block data.", blockId.namespace(), blockId.key());
        }
        return IrisCustomData.of(base, blockId);
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        CustomStack stack = CustomStack.getInstance(itemId.toString());
        if (stack == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return stack.getItemStack();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        if (CustomBlock.place(blockId.toString(), block.getLocation()) == null) {
            throw new MissingResourceException("Failed to place ItemsAdder block.", blockId.namespace(), blockId.key());
        }
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        return switch (dataType) {
            case ENTITY -> List.of();
            case ITEM -> content.items();
            case BLOCK -> content.blocks();
        };
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return dataType == DataType.ITEM ? content.itemNamespaces().contains(id.namespace()) : content.blocks().contains(id);
    }

    private boolean updateContent() {
        try {
            Set<Identifier> items = CustomStack.getNamespacedIdsInRegistry().stream()
                    .map(Identifier::fromString).collect(Collectors.toUnmodifiableSet());
            Set<Identifier> blocks = CustomBlock.getNamespacedIdsInRegistry().stream()
                    .map(Identifier::fromString).collect(Collectors.toUnmodifiableSet());
            Set<String> itemNamespaces = items.stream().map(Identifier::namespace).collect(Collectors.toUnmodifiableSet());
            content = new ContentRegistry(items, blocks, itemNamespaces, true);
            IrisLogging.debug("Updated ItemsAdder content registry: " + items.size() + " items, " + blocks.size() + " blocks");
            return true;
        } catch (RuntimeException | LinkageError e) {
            IrisLogging.reportError("Failed to update ItemsAdder content registry.", e);
            return false;
        }
    }

    private record ContentRegistry(Set<Identifier> items, Set<Identifier> blocks, Set<String> itemNamespaces, boolean ready) {
    }
}
