package art.arcane.iris.integration.data;

import art.arcane.iris.integration.ExternalDataProvider;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.platform.bukkit.nms.container.BlockProperty;
import art.arcane.iris.platform.bukkit.nms.container.Pair;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.BooleanProperty;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CraftEngineDataProvider extends ExternalDataProvider implements Listener {
    private static final List<BlockProperty> FURNITURE_PROPERTIES = List.of(
            BlockProperty.ofBoolean("randomYaw", false),
            BlockProperty.ofDouble("yaw", 0, 0, 360f, false, true),
            BlockProperty.ofBoolean("randomPitch", false),
            BlockProperty.ofDouble("pitch", 0, 0, 360f, false, true)
    );
    private static final ContentRegistry EMPTY_CONTENT = new ContentRegistry(Set.of(), Set.of(), false);

    private volatile ContentRegistry content = EMPTY_CONTENT;

    public CraftEngineDataProvider() {
        super("CraftEngine");
    }

    @Override
    public void init() {
        content = EMPTY_CONTENT;
        BukkitCraftEngine plugin = BukkitCraftEngine.instance();
        if (plugin != null && plugin.isFullyLoaded()) {
            updateContent();
        }
    }

    @Override
    public boolean isReady() {
        return content.ready() && super.isReady();
    }

    @EventHandler
    public void onReload(CraftEngineReloadEvent event) {
        if (!updateContent()) {
            return;
        }
        ExternalDataSVC service = IrisServices.getOrNull(ExternalDataSVC.class);
        if (service != null) {
            service.notifyContentChanged();
        }
    }

    @Override
    public @NotNull List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        ImmutableBlockState blockState = findBlockState(blockId);
        if (blockState != null) {
            return blockState.owner().value().properties().stream().map(CraftEngineDataProvider::convert).toList();
        }
        FurnitureDefinition furniture = requireFurniture(blockId);
        List<BlockProperty> properties = new ArrayList<>(FURNITURE_PROPERTIES);
        properties.add(new BlockProperty("variant", String.class, defaultVariant(furniture),
                furniture.variants().keySet(), Function.identity()));
        return List.copyOf(properties);
    }

    @Override
    public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        BukkitItemDefinition item = CraftEngineItems.byId(Key.of(itemId.namespace(), itemId.key()));
        if (item == null) {
            throw missing(itemId, "Failed to find CraftEngine item.");
        }
        return item.buildBukkitItem();
    }

    @Override
    public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        ImmutableBlockState blockState = findBlockState(blockId);
        if (blockState != null) {
            ImmutableBlockState resolved = applyProperties(blockId, blockState, state);
            return IrisCustomData.of(CraftEngineBlocks.getBukkitBlockData(resolved), Identifier.fromString(resolved.toString()));
        }
        FurnitureDefinition furniture = requireFurniture(blockId);
        KMap<String, String> properties = validateFurnitureProperties(blockId, furniture, state);
        return IrisCustomData.of(BukkitBlockResolution.getAir(), ExternalDataSVC.buildState(blockId, properties));
    }

    @Override
    public Optional<Identifier> identifyBlock(@NotNull BlockData blockData) {
        BlockData nativeData = blockData instanceof IrisCustomData custom ? custom.getBase() : blockData;
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(nativeData);
        return state == null || state.isEmpty() ? Optional.empty() : Optional.of(Identifier.fromString(state.toString()));
    }

    @Override
    public boolean placeBlock(@NotNull Block block, @NotNull Identifier blockId) {
        Pair<Identifier, KMap<String, String>> parsed = ExternalDataSVC.parseState(blockId);
        ImmutableBlockState state = findBlockState(parsed.getA());
        if (state == null) {
            validateFurnitureProperties(parsed.getA(), requireFurniture(parsed.getA()), parsed.getB());
            return false;
        }
        placeBlockState(block, applyProperties(parsed.getA(), state, parsed.getB()), blockId);
        return true;
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        Pair<Identifier, KMap<String, String>> statePair = ExternalDataSVC.parseState(blockId);
        Identifier baseBlockId = statePair.getA();
        KMap<String, String> state = statePair.getB();
        ImmutableBlockState blockState = findBlockState(baseBlockId);
        if (blockState != null) {
            placeBlockState(block, applyProperties(baseBlockId, blockState, state), blockId);
            return;
        }
        FurnitureDefinition furniture = requireFurniture(baseBlockId);
        KMap<String, String> properties = validateFurnitureProperties(baseBlockId, furniture, state);
        Location location = parseYawAndPitch(engine, block, properties);
        if (CraftEngineFurniture.place(location, furniture, properties.get("variant"), false) == null) {
            throw missing(blockId, "Failed to place CraftEngine furniture.");
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
        ContentRegistry registry = content;
        if (!registry.ready()) {
            return false;
        }
        return switch (dataType) {
            case ENTITY -> false;
            case ITEM -> registry.items().contains(id);
            case BLOCK -> registry.blocks().contains(id) || findRuntimeBlockState(id) != null;
        };
    }

    private boolean updateContent() {
        try {
            Set<Identifier> blocks = new LinkedHashSet<>();
            for (Key key : CraftEngineBlocks.loadedBlocks().keySet()) {
                blocks.add(new Identifier(key.namespace(), key.value()));
            }
            for (Key key : CraftEngineFurniture.loadedFurniture().keySet()) {
                blocks.add(new Identifier(key.namespace(), key.value()));
            }
            Set<Identifier> items = CraftEngineItems.loadedItems().keySet().stream()
                    .map(key -> new Identifier(key.namespace(), key.value())).collect(Collectors.toUnmodifiableSet());
            content = new ContentRegistry(items, Set.copyOf(blocks), true);
            return true;
        } catch (RuntimeException | LinkageError error) {
            content = EMPTY_CONTENT;
            IrisLogging.reportError("Failed to update CraftEngine content registry.", error);
            return false;
        }
    }

    private static ImmutableBlockState findBlockState(Identifier blockId) {
        BlockDefinition block = CraftEngineBlocks.byId(Key.of(blockId.namespace(), blockId.key()));
        return block != null ? block.defaultState() : findRuntimeBlockState(blockId);
    }

    private static ImmutableBlockState findRuntimeBlockState(Identifier blockId) {
        if (!Key.CRAFTENGINE_NAMESPACE.equals(blockId.namespace())) {
            return null;
        }
        BlockData data;
        try {
            data = Bukkit.createBlockData(blockId.toString());
        } catch (IllegalArgumentException error) {
            return null;
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(data);
        return state == null || state.isEmpty() ? null : state;
    }

    private static ImmutableBlockState applyProperties(Identifier id, ImmutableBlockState state, Map<String, String> properties) {
        BlockDefinition block = state.owner().value();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getProperty(entry.getKey());
            if (property == null) {
                throw missing(id, "Unknown CraftEngine block property '" + entry.getKey() + "'.");
            }
            Comparable<?> value = property.optional(entry.getValue()).orElse(null);
            if (value == null) {
                throw missing(id, "Invalid CraftEngine block property '" + entry.getKey() + "=" + entry.getValue() + "'.");
            }
            state = ImmutableBlockState.with(state, property, value);
        }
        return state;
    }

    private static void placeBlockState(Block block, ImmutableBlockState state, Identifier id) {
        if (!CraftEngineBlocks.place(block.getLocation(), state, false)
                && !state.equals(CraftEngineBlocks.getCustomBlockState(block))) {
            throw missing(id, "Failed to place CraftEngine block.");
        }
    }

    private static FurnitureDefinition requireFurniture(Identifier id) {
        FurnitureDefinition furniture = CraftEngineFurniture.byId(Key.of(id.namespace(), id.key()));
        if (furniture == null) {
            throw missing(id, "Failed to find CraftEngine block or furniture.");
        }
        return furniture;
    }

    private static KMap<String, String> validateFurnitureProperties(Identifier id, FurnitureDefinition furniture, Map<String, String> state) {
        KMap<String, String> properties = new KMap<>();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String value = entry.getValue();
            switch (entry.getKey()) {
                case "randomYaw", "randomPitch" -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw missing(id, "Invalid CraftEngine furniture boolean '" + entry.getKey() + "=" + value + "'.");
                    }
                }
                case "yaw", "pitch" -> validateAngle(id, entry.getKey(), value);
                case "variant" -> {
                    if (!furniture.variants().containsKey(value)) {
                        throw missing(id, "Unknown CraftEngine furniture variant '" + value + "'.");
                    }
                }
                default -> throw missing(id, "Unknown CraftEngine furniture property '" + entry.getKey() + "'.");
            }
            properties.put(entry.getKey(), value);
        }
        properties.putIfAbsent("variant", defaultVariant(furniture));
        return properties;
    }

    private static void validateAngle(Identifier id, String property, String value) {
        float angle;
        try {
            angle = Float.parseFloat(value);
        } catch (NumberFormatException error) {
            throw missing(id, "Invalid CraftEngine furniture angle '" + property + "=" + value + "'.");
        }
        if (!Float.isFinite(angle) || angle < 0 || angle >= 360) {
            throw missing(id, "CraftEngine furniture angle '" + property + "' must be at least 0 and below 360.");
        }
    }

    private static String defaultVariant(FurnitureDefinition furniture) {
        return furniture.variants().keySet().stream().sorted().findFirst()
                .orElseThrow(() -> missing(Identifier.fromString(furniture.id().toString()), "CraftEngine furniture has no variants."));
    }

    private static Location parseYawAndPitch(@NotNull Engine engine, @NotNull Block block, @NotNull Map<String, String> state) {
        Location location = block.getLocation();
        long seed = engine.getSeedManager().getSeed() + Cache.key(block.getX(), block.getZ()) + block.getY();
        RNG rng = new RNG(seed);
        location.setYaw("true".equals(state.get("randomYaw")) ? rng.f(0, 360) : Float.parseFloat(state.getOrDefault("yaw", "0")));
        location.setPitch("true".equals(state.get("randomPitch")) ? rng.f(0, 360) : Float.parseFloat(state.getOrDefault("pitch", "0")));
        return location;
    }

    private static MissingResourceException missing(Identifier id, String message) {
        return new MissingResourceException(message, id.namespace(), id.key());
    }

    private static <T extends Comparable<T>> BlockProperty convert(Property<T> raw) {
        return switch (raw) {
            case BooleanProperty property -> BlockProperty.ofBoolean(property.name(), property.defaultValue());
            case IntegerProperty property -> BlockProperty.ofLong(property.name(), property.defaultValue(), property.min, property.max, false, false);
            default -> new BlockProperty(raw.name(), raw.valueClass(), raw.defaultValue(), raw.possibleValues(), raw::valueName);
        };
    }

    private record ContentRegistry(Set<Identifier> items, Set<Identifier> blocks, boolean ready) {
    }
}
