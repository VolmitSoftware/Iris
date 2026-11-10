package art.arcane.iris.core.link.data;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.BooleanProperty;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class CraftEngineDataProvider extends ExternalDataProvider implements Listener {
    private static final String PERSISTENT_PROPERTY = "persistent";
    private static final BlockProperty[] FURNITURE_PROPERTIES = new BlockProperty[]{
            BlockProperty.ofBoolean("randomYaw", false),
            BlockProperty.ofDouble("yaw", 0, 0, 360f, false, true),
            BlockProperty.ofBoolean("randomPitch", false),
            BlockProperty.ofDouble("pitch", 0, 0, 360f, false, true),
    };

    public CraftEngineDataProvider() {
        super("CraftEngine");
    }

    @Override
    public void init() {
    }

    @Override
    public @NotNull List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        Key key = Key.of(blockId.namespace(), blockId.key());
        BlockDefinition block = CraftEngineBlocks.byId(key);
        if (block != null) {
            return block.properties().stream().map(CraftEngineDataProvider::convert).toList();
        }

        FurnitureDefinition furniture = CraftEngineFurniture.byId(key);
        if (furniture != null) {
            BlockProperty[] properties = Arrays.copyOf(FURNITURE_PROPERTIES, 5);
            properties[4] = new BlockProperty(
                    "variant",
                    String.class,
                    furniture.anyVariantName(),
                    furniture.variants().keySet(),
                    Function.identity()
            );
            return List.of(properties);
        }

        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        BukkitItemDefinition item = CraftEngineItems.byId(Key.of(itemId.namespace(), itemId.key()));
        if (item == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }

        return item.buildBukkitItem();
    }

    @EventHandler
    public void onReload(CraftEngineReloadEvent event) {
        INMS.get().forgetBlockRules();
    }

    @Override
    public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        Key key = Key.of(blockId.namespace(), blockId.key());
        BlockDefinition customBlock = CraftEngineBlocks.byId(key);
        if (customBlock != null) {
            return CraftEngineBlocks.getBukkitBlockData(stateOf(customBlock, key, state));
        }
        if (CraftEngineFurniture.byId(key) == null) {
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        }

        return IrisCustomData.of(BukkitBlockResolution.getAir(), ExternalDataSVC.buildState(blockId, state));
    }

    private static ImmutableBlockState stateOf(BlockDefinition block, Key key, Map<String, String> state) {
        ImmutableBlockState blockState = block.defaultState();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            Property<?> property = block.getProperty(entry.getKey());
            if (property == null) {
                IrisLogging.warnOnce("craftengine:property:" + key + ":" + entry.getKey(),
                        "Invalid property %s for block %s", entry.getKey(), key);
                continue;
            }
            Optional<?> value = property.optional(entry.getValue());
            if (value.isEmpty()) {
                IrisLogging.warnOnce("craftengine:value:" + key + ":" + entry.getKey() + "=" + entry.getValue(),
                        "Invalid property %s=%s for block %s", entry.getKey(), entry.getValue(), key);
                continue;
            }
            blockState = ImmutableBlockState.with(blockState, property, value.get());
        }
        return blockState;
    }

    /** Only furniture is placed after generation: blocks resolve to their own block data and generate in place. */
    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        art.arcane.iris.core.nms.container.Pair<Identifier, KMap<String, String>> statePair = ExternalDataSVC.parseState(blockId);
        Identifier baseBlockId = statePair.getA();
        KMap<String, String> state = statePair.getB();
        Key key = Key.of(baseBlockId.namespace(), baseBlockId.key());

        FurnitureDefinition furniture = CraftEngineFurniture.byId(key);
        if (furniture == null) {
            return;
        }

        Location location = parseYawAndPitch(engine, block, state);
        String variant = state.getOrDefault("variant", furniture.anyVariantName());
        CraftEngineFurniture.place(location, furniture, variant, false);
    }

    @Override
    public @NotNull BlockData withoutDecay(@NotNull BlockData data) {
        ImmutableBlockState state = customStateOf(data);
        if (state == null) {
            return data;
        }
        Property<Boolean> persistent = state.getProperty(PERSISTENT_PROPERTY);
        if (persistent == null || persistent.valueClass() != Boolean.class || state.get(persistent)) {
            return data;
        }
        return CraftEngineBlocks.getBukkitBlockData(state.with(persistent, true));
    }

    @Override
    public boolean hasBlockTag(@NotNull BlockData data, @NotNull NamespacedKey tag) {
        ImmutableBlockState state = customStateOf(data);
        return state != null && state.settings().tags().contains(Key.of(tag.getNamespace(), tag.getKey()));
    }

    private static ImmutableBlockState customStateOf(BlockData data) {
        if (data instanceof IrisCustomData) {
            return null;
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(data);
        return state == null || state.isEmpty() ? null : state;
    }

    private static Location parseYawAndPitch(@NotNull Engine engine, @NotNull Block block, @NotNull Map<String, String> state) {
        Location location = block.getLocation();
        long seed = engine.getSeedManager().getSeed() + CacheKey.key(block.getX(), block.getZ()) + block.getY();
        RNG rng = new RNG(seed);

        if ("true".equals(state.get("randomYaw"))) {
            location.setYaw(rng.f(0, 360));
        } else if (state.containsKey("yaw")) {
            location.setYaw(Float.parseFloat(state.get("yaw")));
        }

        if ("true".equals(state.get("randomPitch"))) {
            location.setPitch(rng.f(0, 360));
        } else if (state.containsKey("pitch")) {
            location.setPitch(Float.parseFloat(state.get("pitch")));
        }

        return location;
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        Stream<Key> keys = switch (dataType) {
            case ENTITY -> Stream.<Key>empty();
            case ITEM -> CraftEngineItems.loadedItems().keySet().stream();
            case BLOCK -> Stream.concat(CraftEngineBlocks.loadedBlocks().keySet().stream(),
                    CraftEngineFurniture.loadedFurniture().keySet().stream());
        };
        return keys.map(key -> new Identifier(key.namespace(), key.value())).toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        Key key = Key.of(id.namespace(), id.key());
        return switch (dataType) {
            case ENTITY -> false;
            case ITEM -> CraftEngineItems.byId(key) != null;
            case BLOCK -> CraftEngineBlocks.byId(key) != null || CraftEngineFurniture.byId(key) != null;
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
