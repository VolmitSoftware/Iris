package com.volmit.resource;

import com.volmit.block.BlockRegistry;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.PackExpression;
import com.volmit.noise.PackImageMap;
import com.volmit.object.GeneratedObject;
import com.volmit.world.PackBiome;
import com.volmit.world.PackRegion;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/** Immutable view of one loaded pack snapshot, safe for concurrent reads.
 * Reload produces a different snapshotId, even for the same pack. Resources must be immutable
 * or explicitly thread-safe. A resource is named by its kind and its id, so one id can name
 * both a biome and a region. Hosts serve the kinds {@link #BIOMES}, {@link #REGIONS}, {@link #OBJECTS},
 * {@link #EXPRESSIONS} and {@link #IMAGE_MAPS} and every {@link ResourceKind} the pack's modules register; an id the pack lacks for that kind returns empty,
 * and a kind the host does not serve throws IllegalArgumentException. Broken resources fail loading rather than
 * appearing absent. The host owns resolution, imports, native handles and caches; no filesystem paths escape.
 */
public interface PackResources {
    /** The pack's biomes, as the host reads them from its own folder. */
    ResourceKind<PackBiome> BIOMES = ResourceKind.of("host:biome", "biomes", PackBiome.class);

    /** The pack's regions, as the host reads them from its own folder. */
    ResourceKind<PackRegion> REGIONS = ResourceKind.of("host:region", "regions", PackRegion.class);

    /** The pack's objects as geometry, as the host reads them from its own folder. */
    ResourceKind<GeneratedObject> OBJECTS = ResourceKind.of("host:object", "objects", GeneratedObject.class);

    /** The pack's expressions, which noise styles use; the host compiles them from its own folder. */
    ResourceKind<PackExpression> EXPRESSIONS = ResourceKind.of("host:expression", "expressions", PackExpression.class);

    /** The pack's image maps, which noise styles use; the host reads them from its own folder. */
    ResourceKind<PackImageMap> IMAGE_MAPS = ResourceKind.of("host:image-map", "image-maps", PackImageMap.class);

    ResourceId packId();
    UUID snapshotId();
    BlockRegistry blocks();

    /** Asks the platform about items, such as how much damage one takes. */
    ItemRegistry items();

    /** Builds the noise of the {@link com.volmit.noise.NoiseStyle}s the pack's settings describe. */
    NoiseFactory noise();

    /** The resource of {@code kind} the pack has under {@code id}; empty when it has none. */
    <T> Optional<T> find(ResourceKind<T> kind, ResourceId id);

    /** The ids of every resource of {@code kind}, in id order. A kind the host does not serve throws
     * IllegalArgumentException, as in {@link #find}.
     */
    List<ResourceId> ids(ResourceKind<?> kind);

    /** The id of the resource the pack's files name by {@code key} (the file path inside the kind's folder, without
     * {@code .json}), such as {@code overworld:dungeon/chest} for {@code dungeon/chest}; see
     * {@link ResourceId#ofPackKey}. Settings that name a resource in a {@code String}, such as a {@link com.volmit.config.Ref} option, or a
     * biome's key in a region, hold such a key. Characters an id cannot hold are replaced the way the host does for its
     * own ids, so use this and not {@code new ResourceId(packId().namespace(), key)}.
     */
    default ResourceId idOf(String key) {
        return ResourceId.ofPackKey(packId().namespace(), key);
    }

    /** {@link #find} for a resource that must exist: a missing one throws NoSuchElementException naming kind and id. */
    default <T> T require(ResourceKind<T> kind, ResourceId id) {
        return find(kind, id).orElseThrow(() -> new NoSuchElementException("Missing " + kind.id() + " resource: " + id));
    }
}
