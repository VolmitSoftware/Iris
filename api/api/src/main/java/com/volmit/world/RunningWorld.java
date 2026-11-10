package com.volmit.world;

import com.volmit.config.FeatureSettings;
import com.volmit.data.SavedKey;
import com.volmit.geometry.BlockPosition;
import com.volmit.mantle.CarvedVoid;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A world the host is running, as it is now, for services and lifecycle hooks: they have no chunk, so they ask the
 * world's layout, settings and platform state here instead of through an invocation. A chunk stage uses its
 * invocation's views, which may differ while the chunk generates.
 *
 * <p>X and Z are world coordinates; Y is the height above the dimension's bottom, as everywhere in the API. The views
 * live as long as the world and are safe to use from any thread.
 */
public interface RunningWorld {
    /** The world's terrain and biomes as generation lays them out, before anything was placed: its bounds are every
     * column of the world at full height. The same answers a chunk stage's {@link TerrainSampler} gives for its columns.
     */
    TerrainSampler layout();

    /** The settings at a world column, as a chunk stage's {@link FeatureSettings} gives them: the dimension's, the
     * column's region's and its surface biome's; a cave biome's through {@link FeatureSettings#declaredBy}.
     */
    FeatureSettings settings();

    /** The height of the highest block in the column now: the terrain with what objects and structures added.
     * {@code ignoreFluid} skips fluid blocks, so it answers the ground under a sea.
     */
    int highest(int worldX, int worldZ, boolean ignoreFluid);

    /** The floors of the caves generation marked in the chunk, as positions of the floor blocks; none on a platform that
     * cannot read them.
     */
    List<BlockPosition> caveFloors(int chunkX, int chunkZ);

    /** The placement a module recorded at the block when it generated it
     * ({@link com.volmit.placement.PlacementTerrain#recordPlacement}); empty when none was, or when the block's chunk
     * keeps no generation data any more. Y is the height above the dimension's bottom.
     */
    Optional<PlacementRecord> placementAt(int worldX, int y, int worldZ);

    /**
     * The void the cave carver planned at the block, as the world holds it now; empty where none was, or when the block's
     * chunk keeps no generation data any more, and on a platform that cannot read it. Y is the height above the
     * dimension's bottom.
     */
    default Optional<CarvedVoid> carvedAt(int worldX, int y, int worldZ) {
        return Optional.empty();
    }

    /**
     * The value the module keeps with this world across restarts under {@code key}: the host's one instance of it for
     * the world, read from the world's storage the first time anyone asks (or {@code key.initial()} when none was
     * stored), and written back when the host saves the world and when it stops. Keep what a spawner last did or a
     * counter that must outlive the server here, not what can be rebuilt from the pack; per-world caches belong in
     * {@link PackWorld#state}.
     *
     * <p>The module changes the value in place and owns its thread safety. The host writes it while module threads may
     * still change it, so {@link com.volmit.data.StorageCodec#write} must read the value safely. A stored payload the codec
     * rejects is logged and replaced by {@code key.initial()}; the next save overwrites it. A pack reload or hotload
     * stops the world and starts another over the same storage, so the new world reads what the old one wrote when it
     * stopped.
     *
     * @throws IllegalArgumentException if {@code key} is not the key a module registered with
     *     {@link com.volmit.FeatureRegistry#registerSaved}: an unregistered key, or another key with the id of a
     *     registered one, such as one that another module built in its namespace
     */
    <T> T saved(SavedKey<T> key);

    /** The time and weather of the world; empty while the platform has not loaded the world. */
    Optional<Sky> sky();

    /** The ids of the loot tables the platform has, such as {@code minecraft:chests/simple_dungeon}. They do not change
     * while the world runs.
     */
    Set<ResourceId> platformLootTables();

    /** Whether the world is one a pack author is working on (Iris: a studio world): the host then shows where generated
     * things came from, such as the sources of items ({@link com.volmit.item.ItemDrop#source()}).
     */
    boolean debug();

    /** The world's time of day in ticks, whether it storms and whether it thunders. */
    record Sky(long time, boolean storming, boolean thundering) {
    }
}
