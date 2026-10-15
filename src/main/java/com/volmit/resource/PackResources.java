package com.volmit.resource;

import com.volmit.block.BlockRegistry;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/** Immutable view of one loaded pack snapshot, safe for concurrent reads.
 * Reload produces a different snapshotId, even for the same pack. Resources must be immutable
 * or explicitly thread-safe. The requested type selects the kind of resource, so one ID can name
 * both a biome and a region. Hosts serve {@code GeneratedObject} (pack objects),
 * {@code PackBiome}, {@code PackRegion} and the type of every {@link ResourceKind} the pack's modules
 * register; an ID the pack lacks for that kind returns empty, and a type the host does not serve throws
 * IllegalArgumentException. Broken resources fail loading rather than appearing absent.
 * The host owns resolution, imports, native handles and caches; no filesystem paths escape.
 */
public interface PackResources {
    ResourceId packId();
    UUID snapshotId();
    BlockRegistry blocks();

    /** Asks the platform about items, such as how much damage one takes. */
    ItemRegistry items();

    /** Builds the noise of the {@link com.volmit.noise.NoiseStyle}s the pack's settings describe. */
    NoiseFactory noise();

    /** The id the pack's resources have for the one the pack's own files name by {@code packKey}, such as {@code desert/dunes}
     * (a biome's key in a region, a settings field naming a biome): {@code overworld:desert/dunes} in the pack
     * {@code overworld}. Characters an id cannot hold are replaced the way the host does for its own ids, so use this and
     * not {@code new ResourceId(packId().namespace(), packKey)} for a key that may hold them.
     */
    default ResourceId idOf(String packKey) {
        return new ResourceId(packId().namespace(), packKey);
    }

    <T> Optional<T> find(ResourceKey<T> key);

    /** The ids of every resource of the kind {@code type} selects, in id order. A type the host does not serve
     * throws IllegalArgumentException, as in {@link #find}.
     */
    List<ResourceId> ids(Class<?> type);

    /** The id of the resource the pack's files name by {@code key} (the file path inside the kind's folder, without
     * {@code .json}), such as {@code overworld:dungeon/chest} for {@code dungeon/chest}; see
     * {@link ResourceId#ofPackKey}. Settings that name a resource, such as a {@link com.volmit.config.ResourceRef},
     * hold such a key.
     */
    default ResourceId idOf(String key) {
        return ResourceId.ofPackKey(packId().namespace(), key);
    }

    default <T> T require(ResourceKey<T> key) {
        return find(key).orElseThrow(() -> new NoSuchElementException("Missing resource: " + key.id()));
    }
}
