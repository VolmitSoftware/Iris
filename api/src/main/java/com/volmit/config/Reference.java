package com.volmit.config;

/** The platform registry a text option names, so studio can offer the matching completion list: entries of the
 * server's registries, such as {@code minecraft:stone}, including those that data packs and mods add, and the host's
 * own noise generators. Resources of the pack are named by their kind instead, {@code @Ref(kind = ...)}. The set is
 * closed: every registry is one the Iris host can list.
 */
public enum Reference {
    /** A block type, such as {@code minecraft:stone}. {@link com.volmit.block.BlockState} options are block references without {@link Ref}. */
    BLOCK,
    /** An item type, such as {@code minecraft:stick}. */
    ITEM,
    /** An entity type, such as {@code minecraft:zombie}. */
    ENTITY,
    /** A platform biome, such as {@code minecraft:plains}; not a biome of the pack. */
    BIOME,
    /** A platform structure, such as {@code minecraft:village_plains}. */
    STRUCTURE,
    /** A platform structure or a prefix naming a family of them: {@code minecraft:village_plains},
     * {@code minecraft:village} (every village) or {@code nova_structures:} (a whole namespace). */
    STRUCTURE_PREFIX,
    /** A platform structure set, such as {@code minecraft:villages}. */
    STRUCTURE_SET,
    /** A platform jigsaw template pool, such as {@code minecraft:village/plains/town_centers}. */
    TEMPLATE_POOL,
    /** A potion effect type, such as {@code minecraft:speed}. */
    POTION_EFFECT,
    /** An enchantment, such as {@code minecraft:sharpness}. */
    ENCHANTMENT,
    /** A loot table of the platform, such as {@code minecraft:chests/simple_dungeon}; not a loot table of the pack. */
    LOOT_TABLE,
    /** A mob of another plugin or content provider, such as {@code mythicmobs:SkeletonKing}; see {@link com.volmit.resource.ContentKey.Provided}. */
    SPECIAL_ENTITY,
    /** One of the host's noise styles, such as {@code SIMPLEX}; see {@link com.volmit.noise.NoiseStyle}. */
    NOISE_STYLE
}
