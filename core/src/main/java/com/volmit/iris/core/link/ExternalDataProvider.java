package com.volmit.iris.core.link;

import com.volmit.iris.core.link.data.DataType;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.math.RNG;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

@Getter
@RequiredArgsConstructor
public abstract class ExternalDataProvider implements Listener {

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
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    /**
     * Retrieves a list of all {@link BlockProperty} objects associated with the specified block identifier.
     *
     * @param blockId The identifier of the block whose properties are to be retrieved. Must not be null.
     * @return A list of {@link BlockProperty} objects representing the properties of the block.
     * @throws MissingResourceException If the specified block identifier is invalid or cannot be found.
     */
    @NotNull
    public List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        return List.of();
    }

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
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
    }

    /**
     * This method is used for placing blocks that need to use the plugins api
     * it will only be called when the {@link ExternalDataProvider#getBlockData(Identifier, KMap)} returned a {@link IrisCustomData}
     *
     * @param engine The engine of the world the block is being placed in
     * @param block The block where the block should be placed
     * @param blockId The blockId to place
     */
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {}

    /**
     * Spawns a mob in the specified location using the given engine and entity identifier.
     *
     * @param location The location in the world where the mob should spawn. Must not be null.
     * @param entityId The identifier of the mob entity to spawn. Must not be null.
     * @return The spawned {@link Entity} if successful, or null if the mob could not be spawned.
     */
    @Nullable
    public Entity spawnMob(@NotNull Location location, @NotNull Identifier entityId) throws MissingResourceException {
        throw new MissingResourceException("Failed to find Entity!", entityId.namespace(), entityId.key());
    }

    public abstract @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType);

    public abstract boolean isValidProvider(@NotNull Identifier id, DataType dataType);

    protected static Pair<Float, BlockFace> parseYawAndFace(@NotNull Engine engine, @NotNull Block block, @NotNull KMap<@NotNull String, @NotNull String> state) {
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

        return new Pair<>(yaw, face);
    }

    protected static List<BlockProperty> YAW_FACE_BIOME_PROPERTIES = List.of(
            BlockProperty.ofEnum(BiomeColor.class, "matchBiome", null),
            BlockProperty.ofBoolean("randomYaw", false),
            BlockProperty.ofFloat("yaw", 0, 0, 360f, false, true),
            BlockProperty.ofBoolean("randomFace", true),
            new BlockProperty(
                    "face",
                    BlockFace.class,
                    BlockFace.NORTH,
                    Arrays.asList(BlockFace.values()).subList(0, BlockFace.values().length - 1),
                    BlockFace::name
            )
    );
}
